package com.smart.phset.api.feature.iot;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "iot_device_state")
public class DeviceState {
    @Id @Column(name="device_id",length=64) @JsonProperty("device_id") public String deviceId;
    @Column(name="temperature_c") @JsonProperty("temperature_c") public Double temperatureC;
    @Column(name="humidity_percent") @JsonProperty("humidity_percent") public Double humidityPercent;
    @Column(name="soil_moisture_percent") @JsonProperty("soil_moisture_percent") public Double soilMoisturePercent;
    @Column(name="telemetry_captured_at") @JsonProperty("telemetry_captured_at") public Instant telemetryCapturedAt;
    @Column(name="telemetry_received_at") @JsonProperty("telemetry_received_at") public Instant telemetryReceivedAt;
    public Boolean fan;
    public Boolean light;
    public Boolean pump;
    @Column(name="state_captured_at") @JsonProperty("state_captured_at") public Instant stateCapturedAt;
    @Column(name="state_received_at") @JsonProperty("state_received_at") public Instant stateReceivedAt;
}
