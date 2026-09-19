package com.routinemachine.core.api;

import com.routinemachine.core.domain.ScheduleTemplate;

public record ScheduleTemplateResponse(
        Long id,
        String dayOfWeek,
        String targetType,
        Long routineItemId,
        int targetDurationMinutes,
        int sortOrder,
        String label) {

    public static ScheduleTemplateResponse from(ScheduleTemplate template) {
        return new ScheduleTemplateResponse(
                template.getId(),
                template.getDayOfWeek().name(),
                template.getTargetType().name(),
                template.getRoutineItem() == null ? null : template.getRoutineItem().getId(),
                template.getTargetDurationMinutes(),
                template.getSortOrder(),
                template.getLabel());
    }
}
