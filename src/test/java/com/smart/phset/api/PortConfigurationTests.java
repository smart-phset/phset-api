package com.smart.phset.api;

import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.context.servlet.ApplicationServletEnvironment;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class PortConfigurationTests {
    @ParameterizedTest
    @ValueSource(strings = {"", "render"})
    void defaultsTo9090WithoutPort(String profile) {
        assertBindings(environment(profile, Map.of()), 9090);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "render"})
    void renderPortEnvironmentVariableOverridesLocalDefault(String profile) {
        var environment = environment(profile, Map.of("PORT", "10000"));
        assertThat(environment.getProperty("PORT")).isEqualTo("10000");
        assertBindings(environment, 10000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "render"})
    void explicitServerPortHasHigherPrecedenceThanYamlPlaceholder(String profile) {
        // Reproduces a sourced local .env: an explicit server.port setting is not
        // the YAML fallback, so PORT does not override it.
        assertBindings(environment(profile, Map.of("PORT", "10000", "SERVER_PORT", "9090")), 9090);
    }

    private ApplicationServletEnvironment environment(String profile, Map<String, Object> variables) {
        var environment = new ApplicationServletEnvironment();
        // Isolate both JVM properties and shell variables, including SPRING_CONFIG_*
        // and SERVER_PORT, without changing globals or depending on a database.
        var sources = environment.getPropertySources();
        StreamSupport.stream(sources.spliterator(), false).map(source -> source.getName())
                .toList().forEach(sources::remove);
        sources.addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        if (!profile.isEmpty()) environment.setActiveProfiles(profile);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    private void assertBindings(ApplicationServletEnvironment environment, int expectedPort) {
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(expectedPort);
        var server = Binder.get(environment).bind("server", ServerProperties.class).get();
        assertThat(server.getPort()).isEqualTo(expectedPort);
        assertThat(server.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
    }
}
