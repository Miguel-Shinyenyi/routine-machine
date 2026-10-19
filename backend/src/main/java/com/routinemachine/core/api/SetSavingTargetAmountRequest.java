package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetSavingTargetAmountRequest(@NotNull Long userId, @NotNull @Positive Integer amount) {
}
