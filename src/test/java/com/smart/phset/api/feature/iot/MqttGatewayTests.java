package com.smart.phset.api.feature.iot;

import org.junit.jupiter.api.Test;
import org.eclipse.paho.client.mqttv3.*;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import org.mockito.ArgumentCaptor;

class MqttGatewayTests {
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
            verify(client).publish(eq("smartphset/devices/grow-room-1/commands/pump"),any(byte[].class),eq(0),eq(false));
            gateway.connectionLost(null);
            assertThatThrownBy(() -> gateway.command("grow-room-1","pump",true))
                    .isInstanceOf(MqttGateway.BrokerUnavailableException.class);
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
