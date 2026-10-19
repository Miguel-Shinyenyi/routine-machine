package com.routinemachine.core.api;

import com.routinemachine.core.service.GranaryMonthSummary;

import java.util.List;

public record GranaryMonthSummaryResponse(
        int storedThisMonth,
        int targetThisMonth,
        int daysStored,
        int elapsedDays,
        int totalStoredEver,
        List<Boolean> jar,
        Integer savingTargetAmount,
        String currency) {

    public static GranaryMonthSummaryResponse from(GranaryMonthSummary summary, Integer savingTargetAmount, String currency) {
        return new GranaryMonthSummaryResponse(
                summary.storedThisMonth(), summary.targetThisMonth(), summary.daysStored(),
                summary.elapsedDays(), summary.totalStoredEver(), summary.jar(), savingTargetAmount, currency);
    }
}
