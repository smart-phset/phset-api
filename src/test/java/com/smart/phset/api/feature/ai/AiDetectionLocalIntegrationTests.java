package com.smart.phset.api.feature.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties="app.ai.ingest-key=")
@AutoConfigureMockMvc
class AiDetectionLocalIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Test void unsetKeyAllowsLocalIngest() throws Exception {
        mvc.perform(post("/api/ai/detections").contentType(MediaType.APPLICATION_JSON)
            .content(AiDetectionIntegrationTests.payload(UUID.randomUUID(),"2026-10-06T11:25:00Z")))
            .andExpect(status().isCreated());
    }
}
