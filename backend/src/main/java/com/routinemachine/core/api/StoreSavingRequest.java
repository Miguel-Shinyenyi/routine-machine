package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;

public record StoreSavingRequest(@NotNull Long userId) {
}
