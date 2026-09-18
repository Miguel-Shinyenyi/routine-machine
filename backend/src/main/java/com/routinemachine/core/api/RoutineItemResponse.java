package com.routinemachine.core.api;

import com.routinemachine.core.domain.RoutineItem;

public record RoutineItemResponse(Long id, String name, String description) {

    public static RoutineItemResponse from(RoutineItem item) {
        return new RoutineItemResponse(item.getId(), item.getName(), item.getDescription());
    }
}
