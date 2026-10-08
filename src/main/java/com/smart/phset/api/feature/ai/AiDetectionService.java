package com.smart.phset.api.feature.ai;
import com.smart.phset.api.feature.ai.dto.*;
import com.smart.phset.api.feature.ai.entity.*;
import com.smart.phset.api.feature.ai.repository.AiScanRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.*;
@Service
public class AiDetectionService {
    private final AiScanRepository scans;
    private final EntityManager em;

    public AiDetectionService(AiScanRepository scans, EntityManager em) {
        this.scans = scans;
        this.em = em;
    }

    public record Saved(AiDetectionResponse detection, boolean created) {}
    @Transactional
    public Saved save(AiDetectionRequest r) {
        validate(r);
        // Serialize duplicate events across backend instances, within this transaction.
        long lockKey = r.eventId().getMostSignificantBits() ^ r.eventId().getLeastSignificantBits();
        em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:key)")
                .setParameter("key", lockKey).getSingleResult();
        var existing = scans.findByEventId(r.eventId());
        if (existing.isPresent()) return new Saved(response(existing.get()), false);
        AiScan s = new AiScan();
        s.setEventId(r.eventId());
        s.setCameraId(r.cameraId());
        s.setCapturedAt(r.capturedAt());
        s.setVerdict(r.verdict());
        s.setSeverity(r.severity());
        s.setMessage(r.message());
        s.setNContaminated(r.nContaminated());
        s.setNHealthy(r.nHealthy());
        s.setMaxConf(r.maxConf());
        s.setMaxContaminatedConf(r.maxContaminatedConf());
        s.setWidth(r.width());
        s.setHeight(r.height());
        s.setInferenceMs(r.inferenceMs());
        for (var b : r.boxes()) {
            AiDetectionBox box = new AiDetectionBox();
            box.setScan(s);
            box.setLabel(b.label());
            box.setConfidence(b.conf());
            box.setX1(b.xyxy().get(0));
            box.setY1(b.xyxy().get(1));
            box.setX2(b.xyxy().get(2));
            box.setY2(b.xyxy().get(3));
            s.getBoxes().add(box);
        }
        // Assigned UUIDs can make Spring Data merge: refresh the returned managed
        // instance, not the original object. Flush alone leaves nanosecond Instants
        // in memory while PostgreSQL stores microseconds (potentially rounded).
        var persisted = scans.saveAndFlush(s);
        em.refresh(persisted);
        return new Saved(response(persisted), true);
    }

    private void validate(AiDetectionRequest r) {
        String expected;
        switch (r.verdict()) {
            case "contamination_suspected" -> {
                if(r.nContaminated()==0) throw new IllegalArgumentException("Contamination verdict requires contaminated detections");
                expected = r.maxContaminatedConf() >= 0.8 ? "RED"
                        : r.maxContaminatedConf() >= 0.4 ? "AMBER" : "GREY";
            }
            case "no_contamination_seen" -> {
                if(r.nContaminated()!=0 || r.nHealthy()==0) throw new IllegalArgumentException("GREEN requires healthy detections without contamination");
                expected = "GREEN";
            }
            default -> {
                if(r.nContaminated()!=0 || r.nHealthy()!=0) throw new IllegalArgumentException("Unavailable verdict cannot report healthy or contaminated bags");
                expected = "GREY";
            }
        }
        if(!expected.equals(r.severity())) throw new IllegalArgumentException("Severity contradicts verdict or max_contaminated_conf");
        if(!Double.isFinite(r.maxConf()) || !Double.isFinite(r.maxContaminatedConf()) || r.maxContaminatedConf()>r.maxConf() || (r.nContaminated()==0 && r.maxContaminatedConf()!=0))
            throw new IllegalArgumentException("Invalid confidence summary");
        for (var b : r.boxes()) {
            var p = b.xyxy();
            if(!Double.isFinite(b.conf()) || p.get(0)<0 || p.get(1)<0 || p.get(2)<p.get(0) || p.get(3)<p.get(1) || p.get(2)>r.width() || p.get(3)>r.height())
                throw new IllegalArgumentException("Invalid bounding box");
        }
    }

    @Transactional(readOnly = true)
    public List<AiDetectionResponse> history(String camera, int limit) {
        return scans.findByCameraIdOrderByCapturedAtDescCreatedAtDescIdDesc(camera, PageRequest.of(0, limit))
                .stream().map(this::response).toList();
    }
    private AiDetectionResponse response(AiScan s) {
        return new AiDetectionResponse(s.getId(),
            s.getEventId(),
            s.getCameraId(),
            s.getCapturedAt(),
            s.getVerdict(),
            s.getSeverity(),
            s.getMessage(),
            s.getNContaminated(),
            s.getNHealthy(),
            s.getMaxConf(),
            s.getMaxContaminatedConf(),
            s.getWidth(),
            s.getHeight(),
            s.getInferenceMs(), s.getCreatedAt(),
            s.getBoxes().stream().map(b -> new AiDetectionBoxRequest(b.getLabel(),b.getConfidence(),List.of(b.getX1(),b.getY1(),b.getX2(),b.getY2()))).toList());
    }
}
