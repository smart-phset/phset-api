package com.smart.phset.api.feature.iot;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IotProperties {
    private final Set<String> devices;
    public IotProperties(@Value("${app.mqtt.devices:grow-room-1}") String configured) {
        devices = Arrays.stream(configured.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
        if (devices.isEmpty() || devices.stream().anyMatch(id -> !id.matches("[A-Za-z0-9_-]{1,64}")))
            throw new IllegalArgumentException("Invalid MQTT device allowlist");
    }
    public boolean known(String id) { return devices.contains(id); }
    public static String topic(String id, String suffix) { return "smartphset/devices/" + id + "/" + suffix; }
}
