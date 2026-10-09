package com.smart.phset.api.feature.iot;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IotIngestionService {
    private final JsonMapper mapper;
    private final Validator validator;
    private final IotProperties properties;
    private final JdbcTemplate jdbc;
    public IotIngestionService(JsonMapper mapper, Validator validator, IotProperties properties, JdbcTemplate jdbc) {
        this.mapper=mapper; this.validator=validator; this.properties=properties; this.jdbc=jdbc;
    }
    @Transactional
    public void accept(String topic, byte[] bytes) {
        if (bytes.length > 2048) throw new IllegalArgumentException("Oversized telemetry");
        var segments=topic.split("/", -1);
        if (segments.length != 4 || !segments[0].equals("smartphset") || !segments[1].equals("devices")
                || !properties.known(segments[2])) throw new IllegalArgumentException("Unknown device/topic");
        String json=new String(bytes, StandardCharsets.UTF_8);
        if (segments[3].equals("telemetry")) {
            var data=mapper.readValue(json, IotMessages.Telemetry.class);
            validate(data, data.deviceId(), segments[2], data.capturedAt());
            if (!Double.isFinite(data.temperatureC()) || !Double.isFinite(data.humidityPercent())
                    || !Double.isFinite(data.soilMoisturePercent())) throw new IllegalArgumentException("Nonfinite reading");
            jdbc.update("""
                INSERT INTO iot_device_state(device_id,temperature_c,humidity_percent,soil_moisture_percent,
                    telemetry_captured_at,telemetry_received_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)
                ON CONFLICT (device_id) DO UPDATE SET temperature_c=EXCLUDED.temperature_c,
                    humidity_percent=EXCLUDED.humidity_percent,soil_moisture_percent=EXCLUDED.soil_moisture_percent,
                    telemetry_captured_at=EXCLUDED.telemetry_captured_at,telemetry_received_at=EXCLUDED.telemetry_received_at
                WHERE iot_device_state.telemetry_captured_at IS NULL
                    OR EXCLUDED.telemetry_captured_at > iot_device_state.telemetry_captured_at
                """, data.deviceId(),data.temperatureC(),data.humidityPercent(),data.soilMoisturePercent(),Timestamp.from(data.capturedAt()));
        } else if (segments[3].equals("state")) {
            var data=mapper.readValue(json,IotMessages.State.class);
            validate(data,data.deviceId(),segments[2],data.capturedAt());
            jdbc.update("""
                INSERT INTO iot_device_state(device_id,fan,light,pump,state_captured_at,state_received_at)
                    VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)
                ON CONFLICT (device_id) DO UPDATE SET fan=EXCLUDED.fan,light=EXCLUDED.light,pump=EXCLUDED.pump,
                    state_captured_at=EXCLUDED.state_captured_at,state_received_at=EXCLUDED.state_received_at
                WHERE iot_device_state.state_captured_at IS NULL
                    OR EXCLUDED.state_captured_at > iot_device_state.state_captured_at
                """,data.deviceId(),data.fan(),data.light(),data.pump(),Timestamp.from(data.capturedAt()));
        } else throw new IllegalArgumentException("Unsupported topic");
    }
    private void validate(Object data,String id,String topicId,Instant capturedAt) {
        if (!validator.validate(data).isEmpty() || !topicId.equals(id)
                || capturedAt.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Invalid device message");
    }
}
