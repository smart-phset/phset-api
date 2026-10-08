package com.smart.phset.api.feature.ai;

import com.smart.phset.api.feature.ai.dto.AiDetectionRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AiDetectionContractTests {
    private JsonMapper mapper() {
        var builder = JsonMapper.builder();
        new AiJsonConfiguration().strictDetectionJson().customize(builder);
        return builder.build();
    }

    @Test
    void snakeCaseRoundTripAndValidation() {
        var mapper = mapper();
        var event = UUID.randomUUID();
        var request = mapper.readValue(AiDetectionIntegrationTests.payload(event, "2026-10-06T11:25:00Z"), AiDetectionRequest.class);
        assertThat(request.eventId()).isEqualTo(event);
        assertThat(request.maxContaminatedConf()).isEqualTo(0.63);
        assertThat(request.boxes().getFirst().xyxy()).containsExactly(120, 180, 450, 650);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
            var invalid = mapper.readValue(AiDetectionIntegrationTests.payload(event, "2026-10-06T11:25:00Z")
                    .replace("[120,180,450,650]", "[120,180,450]"), AiDetectionRequest.class);
            assertThat(factory.getValidator().validate(invalid)).isNotEmpty();
        }
        var serialized = mapper.writeValueAsString(request);
        assertThat(serialized).contains("\"event_id\"", "\"camera_id\"", "\"captured_at\"", "\"max_contaminated_conf\"")
                .doesNotContain("eventId", "cameraId", "maxContaminatedConf");
    }

    @Test
    void actualPythonPublisherFixtureMatchesRequestDto() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/ai-detection.json")) {
            assertThat(input).isNotNull();
            json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var request = mapper().readValue(json, AiDetectionRequest.class);
        assertThat(request.cameraId()).isEqualTo("esp32-cam");
        assertThat(request.maxContaminatedConf()).isEqualTo(0.799999999);
        assertThat(request.severity()).isEqualTo("AMBER");
        assertThat(request.capturedAt()).isEqualTo(java.time.Instant.parse("2026-10-08T10:00:00.123456Z"));
        assertThat(request.boxes()).hasSize(2);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        assertThat(mapper().readTree(mapper().writeValueAsString(request)))
                .isEqualTo(mapper().readTree(json));
    }

    @Test
    void malformedNumbersAreNotCoerced() {
        var mapper = mapper();
        var valid = AiDetectionIntegrationTests.payload(UUID.randomUUID(), "2026-10-06T11:25:00Z");
        for (var invalid : new String[]{
                valid.replace("\"width\":1280", "\"width\":1280.5"),
                valid.replace("\"width\":1280", "\"width\":\"1280\""),
                valid.replace("[120,180,450,650]", "[120.5,180,450,650]"),
                valid.replace("\"camera_id\":\"webcam-0\"", "\"camera_id\":123")}) {
            assertThatThrownBy(() -> mapper.readValue(invalid, AiDetectionRequest.class)).isInstanceOf(RuntimeException.class);
        }
    }
}
