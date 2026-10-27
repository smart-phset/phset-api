package com.smart.phset.api.feature.iot;

import org.junit.jupiter.api.Test;
import org.eclipse.paho.client.mqttv3.*;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import org.mockito.ArgumentCaptor;

class MqttGatewayTests {
    @Test void disabledGatewayNeverConnectsOrPublishes() throws Exception {
        var client=mock(IMqttAsyncClient.class);
        var gateway=new MqttGateway(client,mock(IotIngestionService.class),new JsonMapper(),"","",false);
        try {
            gateway.start();
            gateway.tryConnect();
            assertThatThrownBy(() -> gateway.command("grow-room-1","fan",true))
                    .isInstanceOf(MqttGateway.BrokerUnavailableException.class);
            verify(client,never()).connect(any(),isNull(),any());
            verify(client,never()).publish(anyString(),any(byte[].class),anyInt(),anyBoolean());
        } finally {gateway.close();}
    }
    @Test void initialFailureRetriesAndReconnectResubscribes() throws Exception {
        var client=mock(IMqttAsyncClient.class);
        var ingestion=mock(IotIngestionService.class);
        var gateway=new MqttGateway(client,ingestion,new JsonMapper(),"","",true);
        try {
            gateway.tryConnect();
            var listener=ArgumentCaptor.forClass(IMqttActionListener.class);
            verify(client).connect(any(),isNull(),listener.capture());
            var options=ArgumentCaptor.forClass(MqttConnectOptions.class);
            verify(client).connect(options.capture(),isNull(),any());
            assertThat(options.getValue().isCleanSession()).isTrue();
            assertThat(options.getValue().getConnectionTimeout()).isEqualTo(3);
            gateway.tryConnect(); // One in-flight connection, no retry storm.
            verify(client,times(1)).connect(any(),isNull(),any());
            listener.getValue().onFailure(null,new RuntimeException());
            gateway.tryConnect();verify(client,times(2)).connect(any(),isNull(),any());
            gateway.connectComplete(false,"");
            gateway.connectionLost(new RuntimeException());
            gateway.connectComplete(true,"");
            verify(client,times(2)).subscribe(eq(new String[]{"smartphset/devices/+/telemetry","smartphset/devices/+/state"}),eq(new int[]{1,1}),isNull(),any());
        } finally {gateway.close();}
    }
    @Test void commandsAreNonretainedAndUnavailableDoesNotQueue() throws Exception {
        var client=mock(IMqttAsyncClient.class);
        var gateway=new MqttGateway(client,mock(IotIngestionService.class),new JsonMapper(),"","",true);
        try {
            assertThatThrownBy(() -> gateway.command("grow-room-1","pump",true))
                    .isInstanceOf(MqttGateway.BrokerUnavailableException.class);
            verify(client,never()).publish(anyString(),any(byte[].class),anyInt(),anyBoolean());
            when(client.isConnected()).thenReturn(true);
            gateway.connectComplete(false,"");
            var listener=ArgumentCaptor.forClass(IMqttActionListener.class);
            verify(client).subscribe(any(String[].class),any(int[].class),isNull(),listener.capture());
            listener.getValue().onSuccess(null);
            var result=gateway.command("grow-room-1","pump",false);
            assertThat(result).containsEntry("on",false).containsKeys("command_id","expires_at");
            verify(client).publish(eq("smartphset/devices/grow-room-1/commands"),any(byte[].class),eq(0),eq(false));
            gateway.connectionLost(null);
            assertThatThrownBy(() -> gateway.command("grow-room-1","pump",true))
                    .isInstanceOf(MqttGateway.BrokerUnavailableException.class);
        } finally {gateway.close();}
    }
    @Test void allExplicitCodesPreserveRestReceiptAndSafetyMetadata() throws Exception {
        var client=mock(IMqttAsyncClient.class);
        var mapper=new JsonMapper();
        var gateway=new MqttGateway(client,mock(IotIngestionService.class),mapper,"","",true);
        try {
            when(client.isConnected()).thenReturn(true);
            gateway.connectComplete(false,"");
            var listener=ArgumentCaptor.forClass(IMqttActionListener.class);
            verify(client).subscribe(any(String[].class),any(int[].class),isNull(),listener.capture());
            listener.getValue().onSuccess(null);
            String[] actuators={"pump","fan","light"};
            String[] prefixes={"P","F","L"};
            for(int i=0;i<actuators.length;i++) for(boolean on:new boolean[]{true,false}) {
                clearInvocations(client);
                long before=java.time.Instant.now().getEpochSecond();
                var receipt=gateway.command("grow-room-1",actuators[i],on);
                var bytes=ArgumentCaptor.forClass(byte[].class);
                verify(client).publish(eq("smartphset/devices/grow-room-1/commands"),bytes.capture(),eq(0),eq(false));
                var payload=mapper.readTree(bytes.getValue());
                assertThat(payload.size()).isEqualTo(3);
                assertThat(payload.get("cmd").asText()).isEqualTo(prefixes[i]+(on?"1":"0"));
                assertThat(payload.get("command_id").asText()).isEqualTo(receipt.get("command_id"));
                assertThat(java.util.UUID.fromString(payload.get("command_id").asText())).isNotNull();
                assertThat(payload.get("expires_at").asLong()).isEqualTo(receipt.get("expires_at"))
                        .isBetween(before+10,java.time.Instant.now().getEpochSecond()+10);
                assertThat(receipt).containsOnlyKeys("command_id","on","expires_at").containsEntry("on",on);
            }
            clearInvocations(client);
            assertThatThrownBy(() -> gateway.command("grow-room-1","unknown",true))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(client,never()).publish(anyString(),any(byte[].class),anyInt(),anyBoolean());
        } finally {gateway.close();}
    }
    @Test void malformedMessageCannotEscapeCallback() {
        var ingestion=mock(IotIngestionService.class);
        doThrow(new IllegalArgumentException()).when(ingestion).accept(anyString(),any());
        var gateway=new MqttGateway(mock(IMqttAsyncClient.class),ingestion,new JsonMapper(),"","",false);
        try {gateway.messageArrived("bad",new MqttMessage("{bad".getBytes()));}
        finally {gateway.close();}
    }
}
