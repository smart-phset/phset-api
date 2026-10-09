package com.smart.phset.api.feature.ai;

import com.smart.phset.api.feature.ai.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties={"app.ai.ingest-key=test-ingest-key","app.mqtt.enabled=false"})
@AutoConfigureMockMvc
class AiDetectionIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired AiScanRepository scans;
    @Autowired AiDetectionBoxRepository boxes;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.json.JsonMapper mapper;
    @BeforeEach void reset() { scans.deleteAll(); }
    static String payload(UUID event, String time) {
        return """
            {"event_id":"%s","camera_id":"webcam-0","captured_at":"%s",
             "verdict":"contamination_suspected","severity":"AMBER","message":"Possible contamination",
             "n_contaminated":1,"n_healthy":1,"max_conf":0.99,"max_contaminated_conf":0.63,
             "width":1280,"height":720,"inference_ms":84,
             "boxes":[{"label":"contaminated","conf":0.63,"xyxy":[120,180,450,650]},
                      {"label":"healthy","conf":0.99,"xyxy":[500,180,800,650]}]}
            """.formatted(event,time);
    }
    void insert(UUID event,String time) throws Exception {
        mvc.perform(post("/api/ai/detections").header("X-SmartPhset-AI-Key","test-ingest-key")
            .contentType(MediaType.APPLICATION_JSON).content(payload(event,time)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.event_id").value(event.toString()))
            .andExpect(jsonPath("$.severity").value("AMBER"))
            .andExpect(jsonPath("$.boxes.length()").value(2));
    }
    @Test void insertAndDuplicate() throws Exception {
        var event=UUID.randomUUID(); insert(event,"2026-10-06T11:25:00Z");
        mvc.perform(post("/api/ai/detections").header("X-SmartPhset-AI-Key","test-ingest-key")
            .contentType(MediaType.APPLICATION_JSON).content(payload(event,"2026-10-06T11:25:00Z")))
            .andExpect(status().isOk());
        assertThat(scans.count()).isEqualTo(1); assertThat(boxes.count()).isEqualTo(2);
    }
    @Test void initialDuplicateAndQueriesExposeDatabaseCanonicalTimestamps() throws Exception {
        for (String time : new String[]{"2026-10-08T10:00:00.123456100Z",
                "2026-10-08T10:00:00.123456900Z", "2026-10-08T10:00:00.999999900Z"}) {
            var event = UUID.randomUUID();
            var body = payload(event, time).replace("webcam-0", event.toString());
            var initial = mvc.perform(post("/api/ai/detections")
                    .header("X-SmartPhset-AI-Key", "test-ingest-key")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            var duplicate = mvc.perform(post("/api/ai/detections")
                    .header("X-SmartPhset-AI-Key", "test-ingest-key")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var firstJson = mapper.readTree(initial);
            assertThat(mapper.readTree(duplicate)).isEqualTo(firstJson);
            var stored = jdbc.queryForMap("SELECT captured_at, created_at FROM ai_scans WHERE event_id = ?", event);
            for (var field : java.util.List.of("captured_at", "created_at")) {
                assertThat(java.time.Instant.parse(firstJson.get(field).asText()))
                        .isEqualTo(((java.sql.Timestamp) stored.get(field)).toInstant());
            }
            // PostgreSQL itself is the rounding oracle, including rollover to the
            // next second; do not duplicate its behavior with Java truncation.
            var canonicalCapture = jdbc.queryForObject("SELECT CAST(? AS TIMESTAMPTZ)",
                    java.time.OffsetDateTime.class, time).toInstant();
            assertThat(java.time.Instant.parse(firstJson.get("captured_at").asText()))
                    .isEqualTo(canonicalCapture);
            var latest = mvc.perform(get("/api/ai/detections/latest").param("camera", event.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(latest)).isEqualTo(firstJson);
            var history = mvc.perform(get("/api/ai/detections").param("camera", event.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(history).get(0)).isEqualTo(firstJson);
        }
        assertThat(scans.count()).isEqualTo(3);
        assertThat(boxes.count()).isEqualTo(6);
    }

    @Test void concurrentDuplicateDoesNotCreateExtraRows() throws Exception {
        var event = UUID.randomUUID();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> post = () -> {
                start.await();
                return mvc.perform(post("/api/ai/detections")
                        .header("X-SmartPhset-AI-Key", "test-ingest-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(event, "2026-10-06T11:25:00Z")))
                        .andReturn().getResponse().getStatus();
            };
            var first = workers.submit(post);
            var second = workers.submit(post);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 200);
        }
        assertThat(scans.count()).isEqualTo(1);
        assertThat(boxes.count()).isEqualTo(2);
    }
    @Test void latestAndHistoryOrderedByCaptureTime() throws Exception {
        var newer=UUID.randomUUID(); var older=UUID.randomUUID();
        insert(newer,"2026-10-06T11:26:00Z"); insert(older,"2026-10-06T11:25:00Z");
        mvc.perform(get("/api/ai/detections/latest").param("camera","webcam-0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.event_id").value(newer.toString()));
        mvc.perform(get("/api/ai/detections").param("camera","webcam-0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].event_id").value(newer.toString()))
            .andExpect(jsonPath("$[1].event_id").value(older.toString()));
        mvc.perform(get("/api/ai/detections").param("camera","webcam-0").param("limit","1"))
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/ai/detections/latest").param("camera","missing"))
            .andExpect(status().isNotFound());
    }
    @Test void cameraIsolationTimestampAndBoxRoundTrip() throws Exception {
        var first = UUID.randomUUID();
        insert(first, "2026-10-08T10:00:00.123456Z");
        var other = UUID.randomUUID();
        mvc.perform(post("/api/ai/detections").header("X-SmartPhset-AI-Key", "test-ingest-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(other, "2026-10-08T11:00:00Z").replace("webcam-0", "esp32-cam")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/ai/detections/latest").param("camera", "webcam-0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(first.toString()))
                .andExpect(jsonPath("$.captured_at").value("2026-10-08T10:00:00.123456Z"));
        var history = scans.findByCameraIdOrderByCapturedAtDescCreatedAtDescIdDesc(
                "webcam-0", org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getCapturedAt())
                .isEqualTo(java.time.Instant.parse("2026-10-08T10:00:00.123456Z"));
        assertThat(boxes.findAll()).hasSize(4).anySatisfy(box -> {
            assertThat(box.getLabel()).isEqualTo("contaminated");
            assertThat(box.getConfidence()).isEqualTo(0.63);
            assertThat(java.util.List.of(box.getX1(), box.getY1(), box.getX2(), box.getY2()))
                    .containsExactly(120, 180, 450, 650);
        });
        mvc.perform(get("/api/ai/detections").param("camera", "esp32-cam").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].event_id").value(other.toString()));
    }

    @Test void tiedCaptureTimesHaveStableOrdering() throws Exception {
        insert(UUID.randomUUID(), "2026-10-08T10:00:00Z");
        insert(UUID.randomUUID(), "2026-10-08T10:00:00Z");
        var fixed = java.time.Instant.parse("2026-10-08T10:01:00Z");
        var all = scans.findAll();
        all.forEach(scan -> scan.setCreatedAt(fixed));
        scans.saveAllAndFlush(all);
        var expected = all.stream().map(scan -> scan.getId().toString())
                .sorted(java.util.Comparator.reverseOrder()).toList();
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/ai/detections").param("camera", "webcam-0"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(expected.getFirst()))
                    .andExpect(jsonPath("$[1].id").value(expected.get(1)));
        }
    }

    @Test void invalidRequests() throws Exception {
        String valid=payload(UUID.randomUUID(),"2026-10-06T11:25:00Z");
        for(String bad:new String[]{"{}",
            valid.replace("contamination_suspected", "unknown_verdict"),
            valid.replace("AMBER", "PURPLE"),
            valid.replace("2026-10-06T11:25:00Z", "not-a-timestamp"),valid.replace("\"width\":1280","\"width\":0"),
            valid.replace("\"severity\":\"AMBER\"","\"severity\":\"RED\""),
            valid.replace("[120,180,450,650]","[120,180,450]"),
            valid.replace("[120,180,450,650]","[120.5,180,450,650]"),
            valid.replace("\"width\":1280","\"width\":1280.5"),
            valid.replace("\"width\":1280","\"width\":\"1280\""),
            valid.replace("\"conf\":0.63","\"conf\":1.1"),
            valid.replace("\"n_contaminated\":1","\"n_contaminated\":-1")}) {
            mvc.perform(post("/api/ai/detections").header("X-SmartPhset-AI-Key","test-ingest-key")
                .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_detection"));
        }
        mvc.perform(get("/api/ai/detections").param("camera","webcam-0").param("limit","101"))
            .andExpect(status().isBadRequest());
        assertThat(scans.count()).isZero();
    }
    @Test void ingestKeyRequired() throws Exception {
        for(String key:new String[]{"","wrong"}) {
            mvc.perform(post("/api/ai/detections").header("X-SmartPhset-AI-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(payload(UUID.randomUUID(),"2026-10-06T11:25:00Z")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthorized"));
        }
        assertThat(scans.count()).isZero();
    }
}
