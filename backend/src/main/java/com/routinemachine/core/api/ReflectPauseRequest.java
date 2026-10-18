package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record ReflectPauseRequest(@NotBlank String reflection) {
}
