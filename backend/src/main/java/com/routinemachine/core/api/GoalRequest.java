package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record GoalRequest(@NotBlank String name, String description) {
}
