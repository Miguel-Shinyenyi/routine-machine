package com.routinemachine.core.api;

import com.routinemachine.core.domain.Measurement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MeasurementResponse(Long id, String source, String metric, LocalDate measurementDate, BigDecimal value) {

    public static MeasurementResponse from(Measurement measurement) {
        return new MeasurementResponse(
                measurement.getId(),
                measurement.getSource().name(),
                measurement.getMetric(),
                measurement.getMeasurementDate(),
                measurement.getValue());
    }
}
