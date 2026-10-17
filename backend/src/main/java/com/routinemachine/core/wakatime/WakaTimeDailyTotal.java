package com.routinemachine.core.wakatime;

import java.time.LocalDate;

public record WakaTimeDailyTotal(LocalDate date, long totalSeconds) {
}
