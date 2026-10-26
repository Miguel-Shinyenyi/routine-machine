package com.routinemachine.core.api;

import com.routinemachine.core.domain.FailureMode;
import jakarta.validation.constraints.NotNull;

public record FailureModeRequest(@NotNull FailureMode failureMode) {
}
