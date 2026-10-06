package com.smart.phset.api.feature.ai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;
import java.time.Instant;
@Entity @Table(name="ai_scans") @Getter @Setter
public class AiScan {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false, unique=true) private UUID eventId;
    @Column(nullable=false, length=100) private String cameraId;
    @Column(nullable=false) private Instant capturedAt;
    @Column(nullable=false, length=50) private String verdict;
    @Column(nullable=false, length=20) private String severity;
    @Column(nullable=true) private String message;
    @Column(nullable=false) private Integer nContaminated;
    @Column(nullable=false) private Integer nHealthy;
    @Column(nullable=false) private Double maxConf;
    @Column(nullable=false) private Double maxContaminatedConf;
    @Column(nullable=false) private Integer width;
    @Column(nullable=false) private Integer height;
    @Column(nullable=false) private Integer inferenceMs;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @OneToMany(mappedBy="scan", cascade=CascadeType.ALL, orphanRemoval=true)
    private List<AiDetectionBox> boxes = new ArrayList<>();
}
