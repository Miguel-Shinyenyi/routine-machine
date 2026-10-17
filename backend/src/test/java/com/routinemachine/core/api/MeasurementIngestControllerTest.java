package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.repository.MeasurementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "health-ingest.token=the-real-token")
class MeasurementIngestControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeasurementRepository measurementRepository;

    @Test
    void recordsAHealthMeasurementWithAValidToken() throws Exception {
        long before = measurementRepository.count();

        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps-valid-token-case\",\"date\":\"2026-10-17\",\"value\":8000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("HEALTH"))
                .andExpect(jsonPath("$.metric").value("steps-valid-token-case"))
                .andExpect(jsonPath("$.measurementDate").value("2026-10-17"))
                .andExpect(jsonPath("$.value").value(8000));

        assertThat(measurementRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void rejectsAMissingToken() throws Exception {
        long before = measurementRepository.count();

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps-missing-token-case\",\"date\":\"2026-10-17\",\"value\":8000}"))
                .andExpect(status().isUnauthorized());

        assertThat(measurementRepository.count()).isEqualTo(before);
    }

    @Test
    void rejectsAWrongToken() throws Exception {
        long before = measurementRepository.count();

        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer not-the-right-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps-wrong-token-case\",\"date\":\"2026-10-17\",\"value\":8000}"))
                .andExpect(status().isUnauthorized());

        assertThat(measurementRepository.count()).isEqualTo(before);
    }

    @Test
    void rejectsAMissingMetric() throws Exception {
        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-17\",\"value\":8000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANegativeValue() throws Exception {
        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps\",\"date\":\"2026-10-17\",\"value\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postingTheSameDayAndMetricTwiceUpdatesTheMeasurementInsteadOfDuplicatingIt() throws Exception {
        long before = measurementRepository.count();

        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps-repeat-case\",\"date\":\"2026-10-17\",\"value\":4000}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/measurements")
                        .header("Authorization", "Bearer the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"steps-repeat-case\",\"date\":\"2026-10-17\",\"value\":8000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(8000));

        assertThat(measurementRepository.count()).isEqualTo(before + 1);
    }
}
