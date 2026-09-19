package com.routinemachine.core.api;

import com.routinemachine.core.domain.ScheduleTargetType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;

public record ScheduleTemplateRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull ScheduleTargetType targetType,
        Long routineItemId,
        @Min(1) int targetDurationMinutes,
        int sortOrder,
        @NotBlank String label) {
}
