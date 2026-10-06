package com.smart.phset.api.feature.ai.dto;
import java.util.*;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiDetectionRequest(
    @NotNull UUID eventId,
    @NotBlank @Size(max=100) String cameraId,
    @NotNull Instant capturedAt,
    @NotNull @Pattern(regexp="contamination_suspected|no_contamination_seen|no_detection|camera_error") String verdict,
    @NotNull @Pattern(regexp="RED|AMBER|GREEN|GREY") String severity,
    @Size(max=255) String message,
    @NotNull @Min(0) Integer nContaminated,
    @NotNull @Min(0) Integer nHealthy,
    @NotNull @DecimalMin("0") @DecimalMax("1") Double maxConf,
    @NotNull @DecimalMin("0") @DecimalMax("1") Double maxContaminatedConf,
    @NotNull @Min(1) Integer width,
    @NotNull @Min(1) Integer height,
    @NotNull @Min(0) Integer inferenceMs,
    @NotNull @Size(max=1000) List<@NotNull @Valid AiDetectionBoxRequest> boxes) {}
