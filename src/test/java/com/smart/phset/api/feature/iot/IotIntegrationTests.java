package com.smart.phset.api.feature.iot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.mqtt.enabled=false","app.mqtt.devices=grow-room-1,grow-room-2","app.mqtt.api-key=test-iot-key"})
@AutoConfigureMockMvc
@Testcontainers
class IotIntegrationTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired IotIngestionService ingestion;
    @Autowired DeviceStateRepository states;
    @Autowired MockMvc mvc;
    @MockitoBean MqttGateway mqtt;
    @BeforeEach void reset() {states.deleteAll();}
    static String telemetry(String device,String time) {
        return "{\"device_id\":\""+device+"\",\"temperature_c\":25.8,\"humidity_percent\":87.0,\"soil_moisture_percent\":68.0,\"captured_at\":\""+time+"\"}";
    }
    void accept(String suffix,String json) {ingestion.accept("smartphset/devices/grow-room-1/"+suffix,json.getBytes(StandardCharsets.UTF_8));}
    @Test void telemetryStatePersistenceIsolationAndOlderMessages() throws Exception {
        String now=java.time.Instant.now().minusSeconds(1).toString();
        accept("telemetry",telemetry("grow-room-1",now));
        accept("state","{\"device_id\":\"grow-room-1\",\"fan\":true,\"light\":false,\"pump\":false,\"captured_at\":\""+now+"\"}");
        accept("telemetry",telemetry("grow-room-1","2020-01-01T00:00:00Z").replace("25.8","10.0"));
        ingestion.accept("smartphset/devices/grow-room-2/telemetry",telemetry("grow-room-2",now).replace("25.8","19.0").getBytes(StandardCharsets.UTF_8));
        assertThat(states.count()).isEqualTo(2);
        var saved=states.findById("grow-room-1").orElseThrow();
        assertThat(saved.temperatureC).isEqualTo(25.8);assertThat(saved.fan).isTrue();assertThat(saved.pump).isFalse();
        mvc.perform(get("/api/iot/devices/grow-room-1/state").header("X-SmartPhset-IoT-Key","test-iot-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.humidity_percent").value(87.0));
        mvc.perform(get("/api/iot/devices/grow-room-2/state").header("X-SmartPhset-IoT-Key","test-iot-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temperature_c").value(19.0));
        mvc.perform(get("/api/iot/devices/unknown/state").header("X-SmartPhset-IoT-Key","test-iot-key"))
                .andExpect(status().isNotFound());
    }
    @Test void malformedMissingRangeAndMismatchedDeviceRejected() {
        String valid=telemetry("grow-room-1","2026-01-01T00:00:00Z");
        for(String invalid:new String[]{"{", "{}",valid.replace("25.8","90.0"),valid.replace("87.0","101.0"),
                valid.replace("68.0","-1.0"),valid.replace("grow-room-1","grow-room-2"),valid.replace("25.8","\"25.8\"")}) {
            assertThatThrownBy(() -> accept("telemetry",invalid)).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> ingestion.accept("smartphset/devices/unknown/telemetry",valid.getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThat(states.count()).isZero();
    }
    @Test void commandsAndAuthenticationAndUnavailable() throws Exception {
        when(mqtt.command("grow-room-1","fan",true)).thenReturn(Map.of("command_id","test-command","on",true));
        mvc.perform(post("/api/iot/devices/grow-room-1/actuators/fan").header("X-SmartPhset-IoT-Key","test-iot-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(status().isAccepted());
        verify(mqtt).command("grow-room-1","fan",true);
        assertThat(states.count()).isZero(); // A request is never reported as actual state.
        for(String key:new String[]{"","wrong"}) mvc.perform(get("/api/iot/devices/grow-room-1/state")
                .header("X-SmartPhset-IoT-Key",key)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/iot/devices/unknown/actuators/fan").header("X-SmartPhset-IoT-Key","test-iot-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/iot/devices/grow-room-1/actuators/fan").header("X-SmartPhset-IoT-Key","test-iot-key")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        when(mqtt.command("grow-room-1","pump",false)).thenThrow(new MqttGateway.BrokerUnavailableException());
        mvc.perform(post("/api/iot/devices/grow-room-1/actuators/pump").header("X-SmartPhset-IoT-Key","test-iot-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"on\":false}")).andExpect(status().isServiceUnavailable());
    }
}
