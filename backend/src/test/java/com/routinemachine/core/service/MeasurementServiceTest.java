package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.repository.MeasurementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MeasurementServiceTest extends AbstractIntegrationTest {

    @Autowired
    private MeasurementRepository measurementRepository;

    private MeasurementService measurementService;

    private MeasurementService service() {
        if (measurementService == null) {
            measurementService = new MeasurementService(measurementRepository);
        }
        return measurementService;
    }

    @Test
    void recordingANewMeasurementCreatesARow() {
        Measurement saved = service().recordMeasurement(
                MeasurementSource.WAKATIME, "coding_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("125"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSource()).isEqualTo(MeasurementSource.WAKATIME);
        assertThat(saved.getMetric()).isEqualTo("coding_minutes");
        assertThat(saved.getMeasurementDate()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(saved.getValue()).isEqualByComparingTo("125");

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
    }

    @Test
    void recordingTheSameSourceMetricAndDateTwiceUpdatesTheExistingRowInsteadOfCreatingASecondOne() {
        service().recordMeasurement(
                MeasurementSource.WAKATIME, "coding_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("90"));
        Measurement secondCall = service().recordMeasurement(
                MeasurementSource.WAKATIME, "coding_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("125"));

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("125");
        assertThat(secondCall.getValue()).isEqualByComparingTo("125");
    }

    @Test
    void differentMetricsOnTheSameSourceAndDateAreRecordedSeparately() {
        service().recordMeasurement(
                MeasurementSource.ACTIVITYWATCH, "active_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("40"));
        service().recordMeasurement(
                MeasurementSource.ACTIVITYWATCH, "idle_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("15"));

        assertThat(measurementRepository.findAll()).hasSize(2);
    }

    @Test
    void differentSourcesOnTheSameMetricAndDateAreRecordedSeparately() {
        service().recordMeasurement(
                MeasurementSource.GITHUB, "commit_count", LocalDate.of(2026, 10, 17), new BigDecimal("3"));
        service().recordMeasurement(
                MeasurementSource.RESCUETIME, "commit_count", LocalDate.of(2026, 10, 17), new BigDecimal("3"));

        assertThat(measurementRepository.findAll()).hasSize(2);
    }

    @Test
    void theSameMetricOnADifferentDateIsRecordedSeparately() {
        service().recordMeasurement(
                MeasurementSource.WAKATIME, "coding_minutes", LocalDate.of(2026, 10, 16), new BigDecimal("60"));
        service().recordMeasurement(
                MeasurementSource.WAKATIME, "coding_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("90"));

        assertThat(measurementRepository.findAll()).hasSize(2);
    }
}
