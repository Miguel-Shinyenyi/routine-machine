package com.routinemachine.core.api;

import java.time.OffsetDateTime;

public record CohortMemberResponse(Long userId, String email, String studyGroup, OffsetDateTime joinedAt) {
}
