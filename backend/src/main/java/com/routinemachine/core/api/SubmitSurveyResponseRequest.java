package com.routinemachine.core.api;

import com.routinemachine.core.domain.CohortSurveyAnswer;
import jakarta.validation.constraints.NotNull;

public record SubmitSurveyResponseRequest(@NotNull Long requestingUserId, @NotNull CohortSurveyAnswer answer) {
}
