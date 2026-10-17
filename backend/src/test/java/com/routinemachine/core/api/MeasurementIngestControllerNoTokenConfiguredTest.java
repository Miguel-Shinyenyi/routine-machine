package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// No health-ingest.token property set here, same as a real deployment that never set
// HEALTH_INGEST_TOKEN: every request must be rejected, not quietly accepted. See
// MeasurementIngestController.requireValidToken's fail-closed comment.
@SpringBootTest
@AutoConfigureMockMvc
class MeasurementIngestControllerNoTokenConfiguredTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsEveryRequestWhenNoTokenIsConfigured() throws Exception {
        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer anything-at-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps\",\"date\":\"2026-10-17\",\"value\":8000}"))
                .andExpect(status().isUnauthorized());
    }
}
