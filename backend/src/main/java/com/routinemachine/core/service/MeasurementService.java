package com.routinemachine.core.service;

import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.repository.MeasurementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class MeasurementService {

    private final MeasurementRepository measurementRepository;

    public MeasurementService(MeasurementRepository measurementRepository) {
        this.measurementRepository = measurementRepository;
    }

    @Transactional
    public Measurement recordMeasurement(MeasurementSource source, String metric, LocalDate measurementDate, BigDecimal value) {
        return measurementRepository.findBySourceAndMetricAndMeasurementDate(source, metric, measurementDate)
                .map(existing -> {
                    existing.updateValue(value);
                    return existing;
                })
                .orElseGet(() -> measurementRepository.save(new Measurement(source, metric, measurementDate, value)));
    }
}
