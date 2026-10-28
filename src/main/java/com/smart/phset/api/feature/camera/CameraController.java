package com.smart.phset.api.feature.camera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Operator-managed registry. Public responses never contain capture URLs. */
@RestController
@RequestMapping("/api/cameras")
public class CameraController {
    private final String key;
    private final List<Map<String, String>> cameras;

    public CameraController(@Value("${app.camera.registry:}") String registry,
                            @Value("${app.camera.api-key:}") String key) {
        this.key = key;
        var entries = new ArrayList<Map<String, String>>();
        var ids = new HashSet<String>();
        for (String item : registry.split(",")) {
            if (item.isBlank()) continue;
            String[] pair = item.trim().split("=", 2);
            if (pair.length != 2 || pair[0].equals("browser") || !pair[0].matches("[A-Za-z0-9_-]{1,100}") || pair[1].isBlank() || !ids.add(pair[0]))
                throw new IllegalArgumentException("Invalid camera registry");
            entries.add(Map.of("id", pair[0], "label", pair[1], "availability", "unknown"));
        }
        cameras = List.copyOf(entries);
    }

    private void authorize(String supplied) {
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (supplied == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    @GetMapping
    public List<Map<String, String>> list(@RequestHeader(value="X-SmartPhset-Camera-Key", required=false) String supplied) {
        authorize(supplied);
        return cameras;
    }

    @GetMapping("/{id}/availability")
    public Map<String, String> availability(@PathVariable String id,
            @RequestHeader(value="X-SmartPhset-Camera-Key", required=false) String supplied) {
        authorize(supplied);
        return cameras.stream().filter(c -> c.get("id").equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
