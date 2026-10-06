package com.smart.phset.api.feature.ai;

import com.smart.phset.api.feature.ai.dto.AiDetectionRequest;
import com.smart.phset.api.feature.ai.dto.AiDetectionResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/detections")
public class AiDetectionController {
    private final AiDetectionService service;
    private final String key;

    public AiDetectionController(AiDetectionService service, @Value("${app.ai.ingest-key:}") String key) {
        this.service = service;
        this.key = key;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-SmartPhset-AI-Key", required = false) String supplied,
            @Valid @RequestBody AiDetectionRequest request) {
        if (!key.isBlank() && (supplied == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "unauthorized", "message", "Invalid AI ingest key"));
        }
        var saved = service.save(request);
        return ResponseEntity.status(saved.created() ? 201 : 200).body(saved.detection());
    }

    @GetMapping("/latest")
    public ResponseEntity<?> latest(@RequestParam(name = "camera") String camera) {
        validateCamera(camera);
        var records = service.history(camera, 1);
        if (records.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "detection_not_found", "message", "No detection for camera"));
        }
        return ResponseEntity.ok(records.getFirst());
    }

    @GetMapping
    public List<AiDetectionResponse> history(
            @RequestParam(name = "camera") String camera, @RequestParam(name = "limit", defaultValue = "20") int limit) {
        validateCamera(camera);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return service.history(camera, limit);
    }

    private void validateCamera(String camera) {
        if (camera.isBlank() || camera.length() > 100) {
            throw new IllegalArgumentException("camera must contain 1–100 characters");
        }
    }
}
