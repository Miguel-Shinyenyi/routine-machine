package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrganisationRequest(@NotBlank String name) {
}
