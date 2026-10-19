package com.routinemachine.core.api;

import jakarta.validation.constraints.NotNull;

public record AddCohortMemberRequest(@NotNull Long requestingUserId, @NotNull Long newMemberUserId) {
}
