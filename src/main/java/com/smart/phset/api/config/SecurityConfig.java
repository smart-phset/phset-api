package com.smart.phset.api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/ai/detections", "/api/iot/devices/*/actuators/*"))
                .authorizeHttpRequests(auth -> auth
                        // Preserve sendError statuses when the container dispatches to /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/ai/detections", "/api/ai/detections/latest").permitAll()
                        .requestMatchers("/api/iot/devices/*/state", "/api/iot/devices/*/actuators/*").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
