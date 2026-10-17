package com.routinemachine.core.activitywatch;

import java.time.LocalDate;

public record ActivityWatchDailyTotal(LocalDate date, long activeSeconds) {
}
