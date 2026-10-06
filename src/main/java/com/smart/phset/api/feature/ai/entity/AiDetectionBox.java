package com.smart.phset.api.feature.ai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
@Entity @Table(name="ai_detection_boxes") @Getter @Setter
public class AiDetectionBox {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="scan_id", nullable=false)
    private AiScan scan;
    @Column(nullable=false,length=50) private String label;
    @Column(nullable=false) private double confidence;
    @Column(nullable=false) private int x1;
    @Column(nullable=false) private int y1;
    @Column(nullable=false) private int x2;
    @Column(nullable=false) private int y2;
}
