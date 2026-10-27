package com.smart.phset.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@ActiveProfiles("render")
@SpringBootTest(properties = "app.mqtt.enabled=false")
@ContextConfiguration(initializers = DeploymentIntegrationTests.RenderEnvironment.class)
@AutoConfigureMockMvc
class DeploymentIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @Autowired ServerProperties serverProperties;

    static class RenderEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            // A sourced local .env can export SERVER_PORT=9090. That direct server.port
            // override wins over application.yaml, even when the test supplies PORT.
            // Model Render's environment without modifying the process environment.
            var variables = new HashMap<String, Object>(System.getenv());
            variables.remove("SERVER_PORT");
            variables.put("PORT", "10000");
            context.getEnvironment().getPropertySources().replace(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        }
    }

    @Test void healthIsPublicAndStatusOnlyEvenForAuthenticatedUsers() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        mvc.perform(get("/actuator/health").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test void privateActuatorAndCloudDocumentationAreUnavailable() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/configprops", "/actuator/health/db", "/actuator/info"}) {
            mvc.perform(get(path).with(user("operator").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test void cloudBindingsAndPoolSettingsAreApplied() {
        assertThat(environment.getProperty("PORT")).isEqualTo("10000");
        assertThat(environment.getProperty("server.port")).isEqualTo("10000");
        assertThat(serverProperties.getPort()).isEqualTo(10000);
        assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("2");
        assertThat(environment.getProperty("app.mqtt.enabled")).isEqualTo("false");
    }
}
