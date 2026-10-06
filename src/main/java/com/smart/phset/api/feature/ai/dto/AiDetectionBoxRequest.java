package com.smart.phset.api.feature.ai.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public record AiDetectionBoxRequest(@NotBlank @Size(max=50) String label,
    @NotNull @DecimalMin("0") @DecimalMax("1") Double conf,
    @NotNull @Size(min=4,max=4) List<@NotNull Integer> xyxy) {}
