package com.smart.phset.api.feature.iot;

import com.smart.phset.api.config.OpenApiConfig;
import com.smart.phset.api.config.SecurityConfig;
import com.smart.phset.api.feature.ai.AiDetectionController;
import com.smart.phset.api.feature.ai.AiDetectionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = IotOpenApiTests.DocumentationApplication.class,
        properties = {"app.mqtt.api-key=test-iot-key", "app.ai.ingest-key=test-ai-key"})
@AutoConfigureMockMvc
class IotOpenApiTests {
    // Real springdoc MVC/resources and security, without database or broker connections.
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
    @Import({SecurityConfig.class, OpenApiConfig.class, IotController.class, IotProperties.class,
            AiDetectionController.class})
    static class DocumentationApplication {}

    @Autowired MockMvc mvc;
    @MockitoBean DeviceStateRepository states;
    @MockitoBean MqttGateway mqtt;
    @MockitoBean AiDetectionService ai;

    @Test void documentationResourcesAreReachableWithoutKeys() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    }

    @Test void generatedDocumentHasSeparateKeysAndUsableIotOperations() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.iotKey.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.iotKey.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.iotKey.name").value("X-SmartPhset-IoT-Key"))
                .andExpect(jsonPath("$.components.securitySchemes.aiKey.name").value("X-SmartPhset-AI-Key"))
                .andExpect(jsonPath("$.paths['/api/iot/devices/{device}/state'].get.security[0].iotKey").isArray())
                .andExpect(jsonPath("$.paths['/api/iot/devices/{device}/actuators/{actuator}'].post.security[0].iotKey").isArray())
                .andExpect(jsonPath("$.paths['/api/iot/devices/{device}/actuators/{actuator}'].post.requestBody.content['application/json'].example.on").value(false))
                .andExpect(jsonPath("$.paths['/api/iot/devices/{device}/actuators/{actuator}'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/iot/devices/{device}/actuators/{actuator}'].post.parameters[1].name").value("actuator"))
                .andExpect(jsonPath("$.paths['/api/iot/devices/{device}/actuators/{actuator}'].post.parameters[1].schema.enum").value(contains("fan", "light", "pump")))
                .andExpect(jsonPath("$.paths['/api/ai/detections'].post.security[0].aiKey").isArray())
                .andExpect(jsonPath("$.paths['/api/ai/detections'].post.security[1]").isEmpty())
                .andExpect(jsonPath("$.paths['/api/ai/detections'].get.security").doesNotExist())
                .andExpect(content().string(not(containsString("test-iot-key"))))
                .andExpect(content().string(not(containsString("test-ai-key"))));
    }

    @Test void documentationAccessDoesNotBypassIotAuthentication() throws Exception {
        for (String key : new String[]{"", "wrong"}) {
            var read = get("/api/iot/devices/grow-room-1/state");
            var write = post("/api/iot/devices/grow-room-1/actuators/fan")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"on\":false}");
            if (!key.isEmpty()) {read.header("X-SmartPhset-IoT-Key", key); write.header("X-SmartPhset-IoT-Key", key);}
            mvc.perform(read).andExpect(status().isUnauthorized());
            mvc.perform(write).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/iot/private")).andExpect(status().isForbidden());
        mvc.perform(get("/api/private")).andExpect(status().isForbidden());
        verifyNoInteractions(states, mqtt);
    }

    @Test void authenticatedCommandRetainsSuccessUnavailableAndUnknownDeviceStatuses() throws Exception {
        when(mqtt.command("grow-room-1", "fan", false)).thenReturn(Map.of("on", false));
        mvc.perform(command("grow-room-1")).andExpect(status().isAccepted());
        when(mqtt.command("grow-room-1", "fan", false)).thenThrow(new MqttGateway.BrokerUnavailableException());
        mvc.perform(command("grow-room-1")).andExpect(status().isServiceUnavailable());
        mvc.perform(command("unknown")).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(String device) {
        return post("/api/iot/devices/" + device + "/actuators/fan")
                .header("X-SmartPhset-IoT-Key", "test-iot-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"on\":false}");
    }
}
