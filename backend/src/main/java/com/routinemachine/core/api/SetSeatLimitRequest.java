package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetSeatLimitRequest(@NotNull Long requestingUserId, @Positive Integer seatLimit) {
}
