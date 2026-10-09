package com.smart.phset.api.feature.iot;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class IotMessages {
    private IotMessages() {}
    public record Telemetry(
            @JsonProperty("device_id") @NotBlank String deviceId,
            @JsonProperty("temperature_c") @NotNull @DecimalMin("-20") @DecimalMax("60") Double temperatureC,
            @JsonProperty("humidity_percent") @NotNull @DecimalMin("0") @DecimalMax("100") Double humidityPercent,
            @JsonProperty("soil_moisture_percent") @NotNull @DecimalMin("0") @DecimalMax("100") Double soilMoisturePercent,
            @JsonProperty("captured_at") @NotNull Instant capturedAt) {}
    public record State(@JsonProperty("device_id") @NotBlank String deviceId,
            @NotNull Boolean fan, @NotNull Boolean light, @NotNull Boolean pump,
            @JsonProperty("captured_at") @NotNull Instant capturedAt) {}
    public record Command(@NotNull Boolean on) {}
}
