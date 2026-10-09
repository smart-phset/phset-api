package com.smart.phset.api.feature.iot;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MqttGateway implements MqttCallbackExtended {
    private static final Logger log=LoggerFactory.getLogger(MqttGateway.class);
    private final IMqttAsyncClient client;
    private final IotIngestionService ingestion;
    private final JsonMapper mapper;
    private final MqttConnectOptions options;
    private final boolean enabled;
    private final AtomicBoolean connecting=new AtomicBoolean();
    private final ScheduledExecutorService retries=Executors.newSingleThreadScheduledExecutor(r -> {
        var thread=new Thread(r,"smartphset-mqtt-reconnect");thread.setDaemon(true);return thread;
    });
    private volatile boolean ready;
    private volatile boolean stopped;
    @org.springframework.beans.factory.annotation.Autowired
    public MqttGateway(IotIngestionService ingestion, JsonMapper mapper,
            @Value("${app.mqtt.broker-url:tcp://localhost:1883}") String url,
            @Value("${app.mqtt.client-id:smartphset-api}") String clientId,
            @Value("${app.mqtt.username:}") String username,
            @Value("${app.mqtt.password:}") String password,
            @Value("${app.mqtt.enabled:true}") boolean enabled) throws MqttException {
        this(new MqttAsyncClient(url,clientId,new MemoryPersistence()),ingestion,mapper,username,password,enabled);
    }
    MqttGateway(IMqttAsyncClient client,IotIngestionService ingestion,JsonMapper mapper,
                String username,String password,boolean enabled) {
        this.client=client;this.ingestion=ingestion;this.mapper=mapper;this.enabled=enabled;
        options=new MqttConnectOptions();options.setCleanSession(true);options.setAutomaticReconnect(false);
        options.setConnectionTimeout(3);options.setKeepAliveInterval(20);options.setMaxInflight(10);
        if (!username.isBlank()) {options.setUserName(username);options.setPassword(password.toCharArray());}
        client.setCallback(this);
    }
    @PostConstruct void start() { if(enabled) retries.scheduleWithFixedDelay(this::tryConnect,0,5,TimeUnit.SECONDS); }
    void tryConnect() {
        if(stopped || !enabled || client.isConnected() || !connecting.compareAndSet(false,true)) return;
        try {client.connect(options,null,new IMqttActionListener() {
            public void onSuccess(IMqttToken token) {connecting.set(false);}
            public void onFailure(IMqttToken token,Throwable error) {connecting.set(false);log.warn("MQTT unavailable; retry scheduled");}
        });} catch(MqttException error) {connecting.set(false);log.warn("MQTT connection failed; retry scheduled");}
    }
    public void connectComplete(boolean reconnect,String serverURI) {
        if(stopped) return;
        try {client.subscribe(new String[]{"smartphset/devices/+/telemetry","smartphset/devices/+/state"},
                new int[]{1,1},null,new IMqttActionListener() {
                    public void onSuccess(IMqttToken token) {if(!stopped)ready=true;}
                    public void onFailure(IMqttToken token,Throwable error) {resetSubscription();}
                });} catch(MqttException error) {resetSubscription();}
    }
    private void resetSubscription() {
        ready=false;
        try {client.disconnectForcibly(0,1000);} catch(MqttException ignored) { }
    }
    public void connectionLost(Throwable cause) {ready=false;connecting.set(false);log.warn("MQTT connection lost; retry scheduled");}
    public void messageArrived(String topic,MqttMessage message) {
        try {ingestion.accept(topic,message.getPayload());}
        catch(RuntimeException error) {log.warn("MQTT device message rejected ({})",error.getClass().getSimpleName());}
    }
    public void deliveryComplete(IMqttDeliveryToken token) { }
    public Map<String,Object> command(String device,String actuator,boolean on) {
        if (stopped || !ready || !client.isConnected()) throw new BrokerUnavailableException();
        var command=Map.<String,Object>of("command_id",UUID.randomUUID().toString(),"on",on,
                "expires_at",Instant.now().plusSeconds(10).getEpochSecond());
        try {
            // No retained commands, offline buffer or retries of actuation requests.
            client.publish(IotProperties.topic(device,"commands/"+actuator),
                    mapper.writeValueAsString(command).getBytes(StandardCharsets.UTF_8),0,false);
            return command;
        } catch(MqttException error) {throw new BrokerUnavailableException();}
    }
    @PreDestroy void close() {
        stopped=true;ready=false;retries.shutdownNow();
        try {if(client.isConnected())client.disconnectForcibly(0,1000);}
        catch(MqttException ignored) { }
        try {client.close();} catch(MqttException ignored) { }
    }
    static class BrokerUnavailableException extends RuntimeException { }
}
