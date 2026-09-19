package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record TaskStatusUpdateRequest(@NotBlank String status) {
}
