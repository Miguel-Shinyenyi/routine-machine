package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetSeatLimitRequest(@Positive Integer seatLimit) {
}
