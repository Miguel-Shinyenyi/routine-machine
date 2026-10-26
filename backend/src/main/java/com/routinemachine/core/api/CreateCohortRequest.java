package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateCohortRequest(
        @NotBlank String name, @NotNull LocalDate startsOn, @Positive int weeks) {
}
