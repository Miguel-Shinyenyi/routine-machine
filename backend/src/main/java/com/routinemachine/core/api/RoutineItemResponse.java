package com.routinemachine.core.api;

import com.routinemachine.core.domain.RoutineItem;

import java.time.OffsetDateTime;

public record RoutineItemResponse(Long id, String name, String description, OffsetDateTime createdAt) {

    public static RoutineItemResponse from(RoutineItem item) {
        return new RoutineItemResponse(item.getId(), item.getName(), item.getDescription(), item.getCreatedAt());
    }
}
