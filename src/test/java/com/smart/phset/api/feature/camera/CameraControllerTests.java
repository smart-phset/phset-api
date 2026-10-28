package com.smart.phset.api.feature.camera;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class CameraControllerTests {
    @Test void authenticatedRegistryContainsNoAddresses() {
        var controller = new CameraController("esp32-cam=Grow room,usb=Worker USB", "secret");
        assertThatThrownBy(() -> controller.list(null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.list("wrong")).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.list("secret")).hasSize(2);
        assertThat(controller.availability("usb", "secret")).containsEntry("availability", "unknown");
        assertThatThrownBy(() -> controller.availability("missing", "secret")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> new CameraController("http://private=Unsafe", "key")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraController("usb=A,usb=B", "key")).isInstanceOf(IllegalArgumentException.class);
    }
}
