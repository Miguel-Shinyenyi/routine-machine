package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record RoutineItemRequest(@NotBlank String name, String description) {
}
