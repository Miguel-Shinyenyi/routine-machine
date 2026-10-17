package com.routinemachine.core.rescuetime;

import java.time.LocalDate;

public record RescueTimeDailyTotal(LocalDate date, long activeSeconds) {
}
