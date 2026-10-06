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
@SpringBootTest(properties="app.ai.ingest-key=test-ingest-key")
@AutoConfigureMockMvc
class AiDetectionIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired AiScanRepository scans;
    @Autowired AiDetectionBoxRepository boxes;
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
    @Test void invalidRequests() throws Exception {
        String valid=payload(UUID.randomUUID(),"2026-10-06T11:25:00Z");
        for(String bad:new String[]{"{}",valid.replace("\"width\":1280","\"width\":0"),
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
