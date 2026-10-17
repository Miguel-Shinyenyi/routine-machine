package com.routinemachine.core.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// No `source` field: every measurement written through this endpoint is HEALTH, see
// MeasurementIngestController. A companion app choosing its own source would defeat the point
// of this endpoint being the one, narrow way health data gets in.
public record MeasurementIngestRequest(
        @NotBlank String metric,
        @NotNull LocalDate date,
        @NotNull @DecimalMin("0") BigDecimal value) {
}
