package com.smart.phset.api.feature.ai.repository;
import com.smart.phset.api.feature.ai.entity.AiDetectionBox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AiDetectionBoxRepository extends JpaRepository<AiDetectionBox, UUID> {}
