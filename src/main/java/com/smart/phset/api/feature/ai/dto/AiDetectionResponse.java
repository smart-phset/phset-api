package com.smart.phset.api.feature.ai.dto;
import java.util.*;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.PropertyNamingStrategies;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiDetectionResponse(UUID id,
    UUID eventId,
    String cameraId,
    Instant capturedAt,
    String verdict,
    String severity,
    String message,
    Integer nContaminated,
    Integer nHealthy,
    Double maxConf,
    Double maxContaminatedConf,
    Integer width,
    Integer height,
    Integer inferenceMs, Instant createdAt, List<AiDetectionBoxRequest> boxes) {}
