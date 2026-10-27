package com.smart.phset.api.feature.ai;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiIngestSecurityTests {
    @Test void blankKeyFailsClosedBeforePersistence() {
        var service = mock(AiDetectionService.class);
        for (String key : new String[]{"", "  "}) {
            var controller = new AiDetectionController(service, key);
            assertThat(controller.create(null, null).getStatusCode().value()).isEqualTo(503);
            assertThat(controller.create("anything", null).getStatusCode().value()).isEqualTo(503);
        }
        verifyNoInteractions(service);
    }

    @Test void configuredKeyRejectsMissingOrWrongHeaderBeforePersistence() {
        var service = mock(AiDetectionService.class);
        var controller = new AiDetectionController(service, "test-ingest-key");
        for (String supplied : new String[]{null, "", "wrong"}) {
            assertThat(controller.create(supplied, null).getStatusCode().value()).isEqualTo(401);
        }
        verifyNoInteractions(service);
    }
}
