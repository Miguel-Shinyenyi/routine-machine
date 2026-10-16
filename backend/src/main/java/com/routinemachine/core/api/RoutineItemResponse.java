package com.routinemachine.core.api;

import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemCategory;
import com.routinemachine.core.domain.RoutineItemSource;

import java.time.OffsetDateTime;

public record RoutineItemResponse(
        Long id,
        String name,
        String description,
        RoutineItemCategory category,
        String countRule,
        RoutineItemSource source,
        HillsideField hillsideField,
        boolean isPrivate,
        Long goalId,
        String goalName,
        OffsetDateTime createdAt) {

    public static RoutineItemResponse from(RoutineItem item) {
        return new RoutineItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getCountRule(),
                item.getSource(),
                item.getHillsideField(),
                item.isPrivate(),
                item.getGoal() == null ? null : item.getGoal().getId(),
                item.getGoal() == null ? null : item.getGoal().getName(),
                item.getCreatedAt());
    }
}
