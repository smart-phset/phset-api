package com.smart.phset.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI smartPhsetOpenAPI() {
        return new OpenAPI().info(new Info().title("SmartPhset API").version("Phase 8"))
                .components(new Components()
                        .addSecuritySchemes("iotKey", headerKey("X-SmartPhset-IoT-Key", "Required for IoT endpoints."))
                        .addSecuritySchemes("aiKey", headerKey("X-SmartPhset-AI-Key",
                                "Required for AI ingestion only when the backend ingest key is configured.")));
    }

    private SecurityScheme headerKey(String name, String description) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                .name(name).description(description);
    }

    @Bean
    OpenApiCustomizer machineApiDocumentation() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/iot/devices/")) {
                item.readOperations().forEach(operation -> {
                    operation.setTags(List.of("IoT"));
                    operation.setSecurity(List.of(new SecurityRequirement().addList("iotKey")));
                    // Authorize supplies the header; avoid a second, independent header input.
                    operation.getParameters().removeIf(parameter -> "X-SmartPhset-IoT-Key".equals(parameter.getName()));
                    operation.getResponses().addApiResponse("401", response("Missing or wrong IoT API key."))
                            .addApiResponse("404", response("Unknown device or no reported state."))
                            .addApiResponse("503", response("IoT REST key not configured."));
                });
                if (item.getGet() != null) {
                    item.getGet().setSummary("Read current device state");
                    item.getGet().setDescription("Latest reported state and telemetry, not sensor history. Output booleans are firmware-reported GPIO state, not physical acknowledgement. Without a soil sensor, soil values from floating GPIO34 are invalid.");
                }
                if (item.getPost() != null) {
                    var operation = item.getPost();
                    operation.setSummary("Publish an actuator command");
                    operation.setDescription("202 means accepted for MQTT transmission, not physical acknowledgement. Physical actuator acceptance is deferred while drivers and loads are unavailable.");
                    operation.getResponses().addApiResponse("202", response("Accepted for MQTT transmission; not physical acknowledgement."))
                            .addApiResponse("503", response("MQTT/broker unavailable, or IoT REST key not configured."))
                            .addApiResponse("400", response("Invalid actuator or command body."));
                    operation.getParameters().stream().filter(parameter -> "actuator".equals(parameter.getName()))
                            .forEach(parameter -> parameter.setSchema(new StringSchema()
                                    ._enum(List.of("fan", "light", "pump"))));
                    operation.getRequestBody().getContent().values()
                            .forEach(media -> media.setExample(java.util.Map.of("on", false)));
                }
            }
            if (path.startsWith("/api/ai/detections")) {
                item.readOperations().forEach(operation -> operation.setTags(List.of("AI Detection")));
                if (item.getPost() != null) {
                    var operation = item.getPost();
                    operation.setDescription("AI ingestion requires X-SmartPhset-AI-Key only when the backend ingest key is configured. Existing optional-key behavior is preserved.");
                    operation.setSecurity(List.of(new SecurityRequirement().addList("aiKey"), new SecurityRequirement()));
                    operation.getParameters().removeIf(parameter -> "X-SmartPhset-AI-Key".equals(parameter.getName()));
                }
            }
        });
    }

    private ApiResponse response(String description) {
        return new ApiResponse().description(description);
    }
}
