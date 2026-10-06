package com.smart.phset.api.feature.ai.repository;
import com.smart.phset.api.feature.ai.entity.AiScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface AiScanRepository extends JpaRepository<AiScan, UUID> {
    Optional<AiScan> findByEventId(UUID eventId);
    List<AiScan> findByCameraIdOrderByCapturedAtDescCreatedAtDescIdDesc(String cameraId, Pageable pageable);
}
