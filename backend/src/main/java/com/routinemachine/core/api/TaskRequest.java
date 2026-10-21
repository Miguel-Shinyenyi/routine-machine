package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TaskRequest(
        @NotNull LocalDate taskDate,
        Long scheduleTemplateId,
        String label,
        Long routineItemId,
        Long learningTopicId) {
}
