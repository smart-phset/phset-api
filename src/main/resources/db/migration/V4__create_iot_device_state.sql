CREATE TABLE iot_device_state (
    device_id VARCHAR(64) PRIMARY KEY,
    temperature_c DOUBLE PRECISION CHECK (temperature_c BETWEEN -20 AND 60),
    humidity_percent DOUBLE PRECISION CHECK (humidity_percent BETWEEN 0 AND 100),
    soil_moisture_percent DOUBLE PRECISION CHECK (soil_moisture_percent BETWEEN 0 AND 100),
    telemetry_captured_at TIMESTAMPTZ,
    telemetry_received_at TIMESTAMPTZ,
    fan BOOLEAN,
    light BOOLEAN,
    pump BOOLEAN,
    state_captured_at TIMESTAMPTZ,
    state_received_at TIMESTAMPTZ
);
