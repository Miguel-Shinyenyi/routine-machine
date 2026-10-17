package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    Optional<Measurement> findBySourceAndMetricAndMeasurementDate(
            MeasurementSource source, String metric, LocalDate measurementDate);

    Optional<Measurement> findTopBySourceOrderByUpdatedAtDesc(MeasurementSource source);
}
