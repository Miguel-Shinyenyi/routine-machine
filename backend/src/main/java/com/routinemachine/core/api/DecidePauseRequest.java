package com.routinemachine.core.api;

import com.routinemachine.core.domain.PauseOutcome;
import jakarta.validation.constraints.NotNull;

public record DecidePauseRequest(@NotNull PauseOutcome outcome) {
}
