package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record CurrentReadingRequest(@NotBlank String title) {
}
