package com.routinemachine.core.service;

// The four pilot measures from docs/product-direction.md's cohort dashboard section, each
// with its own denominator. Totals only — see CohortDashboardService for what's deliberately
// excluded from this type (no per-learner names, notes, money, or interests).
public record CohortDashboardSummary(
        CohortMeasure activatedWithinSevenDays,
        CohortMeasure checkedInThisWeek,
        CohortMeasure returnedAfterAMissedDay,
        CohortMeasure feltSupportive,
        int saidNeutral,
        int saidPressuring) {
}
