package com.smart.phset.api.feature.iot;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IotIngestionTests {
    @Test void validTelemetryAndStateReachPersistenceAndInvalidInputDoesNot() {
        var jdbc=mock(JdbcTemplate.class);
        // Match production strict scalar coercion configuration.
        var mapper=JsonMapper.builder().disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=new IotIngestionService(mapper.build(),factory.getValidator(),new IotProperties("grow-room-1"),jdbc);
            var valid=IotIntegrationTests.telemetry("grow-room-1","2026-01-01T00:00:00Z");
            service.accept("smartphset/devices/grow-room-1/telemetry",valid.getBytes(StandardCharsets.UTF_8));
            service.accept("smartphset/devices/grow-room-1/state",("{\"device_id\":\"grow-room-1\",\"fan\":false,\"light\":true,\"pump\":false,\"captured_at\":\"2026-01-01T00:00:00Z\"}").getBytes(StandardCharsets.UTF_8));
            assertThat(mockingDetails(jdbc).getInvocations()).hasSize(2);
            for(String bad:new String[]{"{}","{",valid.replace("25.8","90.0"),valid.replace("87.0","-1.0"),
                    valid.replace("68.0","101.0"),valid.replace("grow-room-1","other"),valid.replace("25.8","\"25.8\"")}) {
                assertThatThrownBy(() -> service.accept("smartphset/devices/grow-room-1/telemetry",bad.getBytes(StandardCharsets.UTF_8)))
                        .isInstanceOf(RuntimeException.class);
            }
            assertThatThrownBy(() -> service.accept("smartphset/devices/unknown/telemetry",valid.getBytes())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.accept("smartphset/devices/grow-room-1/telemetry",new byte[2049])).isInstanceOf(IllegalArgumentException.class);
            assertThat(mockingDetails(jdbc).getInvocations()).hasSize(2);
        }
    }
}
