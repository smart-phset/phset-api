package com.smart.phset.api.feature.iot;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IotControllerTests {
    @Test void configurationAndDeviceAndActuatorBoundaries() {
        var states=mock(DeviceStateRepository.class);
        var mqtt=mock(MqttGateway.class);
        var properties=new IotProperties("grow-room-1");
        var disabled=new IotController(states,properties,mqtt,"");
        assertThatThrownBy(() -> disabled.state("grow-room-1",null))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        var controller=new IotController(states,properties,mqtt,"test-key");
        for(String key:new String[]{null,"wrong"}) {
            assertThatThrownBy(() -> controller.state("grow-room-1",key))
                    .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        }
        assertThatThrownBy(() -> controller.command("unknown","fan","test-key",new IotMessages.Command(false)))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> controller.command("grow-room-1","heater","test-key",new IotMessages.Command(false)))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(mqtt,states);
    }
}
