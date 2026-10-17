package com.routinemachine.core.api;

import java.time.OffsetDateTime;

public record WateringHoleStatusResponse(
        String source, boolean enabled, OffsetDateTime lastActivityAt, String description) {
}
