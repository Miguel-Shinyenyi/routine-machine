package com.routinemachine.core.api;

import com.routinemachine.core.domain.DailyLogSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EvidenceEventRequest(
        @NotNull Long userId,
        @NotNull Long goalId,
        @NotNull DailyLogSource source,
        @NotBlank String sourceEventId,
        @NotNull Instant occurredAt,
        Integer minutes) {
}
