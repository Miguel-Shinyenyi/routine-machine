package com.routinemachine.core.api;

import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.domain.RoutineItemCategory;
import com.routinemachine.core.domain.RoutineItemSource;
import jakarta.validation.constraints.NotBlank;

public record RoutineItemRequest(
        @NotBlank String name,
        String description,
        RoutineItemCategory category,
        String countRule,
        RoutineItemSource source,
        HillsideField hillsideField,
        Boolean isPrivate,
        Long goalId) {

    public boolean privateOrDefault() {
        return isPrivate == null || isPrivate;
    }
}
