package com.routinemachine.core.service;

import java.util.List;

public record GranaryMonthSummary(
        int storedThisMonth,
        int targetThisMonth,
        int daysStored,
        int elapsedDays,
        int totalStoredEver,
        List<Boolean> jar) {
}
