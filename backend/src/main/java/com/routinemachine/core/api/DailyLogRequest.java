package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DailyLogRequest(@NotNull LocalDate logDate, Long routineItemId, Long learningTopicId, String source) {
}
