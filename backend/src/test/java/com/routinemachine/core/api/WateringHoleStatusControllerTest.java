package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.HubSyncFileKind;
import com.routinemachine.core.domain.HubSyncProcessedFile;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.repository.HubSyncProcessedFileRepository;
import com.routinemachine.core.repository.MeasurementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "hub-sync.enabled=true", "wakatime-sync.enabled=false", "activitywatch-sync.enabled=true",
        "github-sync.enabled=false", "rescuetime-sync.enabled=true"})
class WateringHoleStatusControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeasurementRepository measurementRepository;

    @Autowired
    private HubSyncProcessedFileRepository hubSyncProcessedFileRepository;

    @Test
    void eachSourcesEnabledFlagReflectsItsOwnConfiguredProperty() throws Exception {
        mockMvc.perform(get("/api/watering-holes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='HUB')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.source=='WAKATIME')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.source=='ACTIVITYWATCH')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.source=='GITHUB')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.source=='RESCUETIME')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.source=='HEALTH')].enabled").value(false));
    }

    @Test
    void everySourceHasADescription() throws Exception {
        mockMvc.perform(get("/api/watering-holes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[5].description").isNotEmpty());
    }

    @Test
    void aSourceWithARecordedMeasurementReportsItsLatestUpdatedAtAsLastActivity() throws Exception {
        measurementRepository.save(new Measurement(
                MeasurementSource.GITHUB, "commit_count", LocalDate.of(2026, 10, 17), new BigDecimal("3")));

        mockMvc.perform(get("/api/watering-holes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='GITHUB')].lastActivityAt").isNotEmpty());
    }

    @Test
    void hubReportsItsLatestProcessedFilesCreatedAtAsLastActivity() throws Exception {
        hubSyncProcessedFileRepository.save(new HubSyncProcessedFile(
                "journal/entries/2026-10-17.md", "deadbeef", HubSyncFileKind.JOURNAL, LocalDate.of(2026, 10, 17)));

        mockMvc.perform(get("/api/watering-holes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='HUB')].lastActivityAt").isNotEmpty());
    }

    @Test
    void aSourceWithNoRecordedActivityHasANullLastActivityAt() throws Exception {
        mockMvc.perform(get("/api/watering-holes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='ACTIVITYWATCH')].lastActivityAt").value(contains(nullValue())));
    }
}
