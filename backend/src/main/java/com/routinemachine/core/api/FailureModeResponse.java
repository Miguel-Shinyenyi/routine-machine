package com.routinemachine.core.api;

import com.routinemachine.core.domain.FailureMode;
import com.routinemachine.core.service.WeeklyFailureModeService;

import java.time.LocalDate;

public record FailureModeResponse(LocalDate weekStart, boolean answered, FailureMode failureMode) {

    public static FailureModeResponse from(WeeklyFailureModeService.Current current) {
        return new FailureModeResponse(current.weekStart(), current.answered(), current.failureMode());
    }
}
