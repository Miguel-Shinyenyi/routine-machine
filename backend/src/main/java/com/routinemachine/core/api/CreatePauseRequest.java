package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePauseRequest(
        @NotBlank String what,
        String amount,
        String why,
        String heardFrom) {
}
