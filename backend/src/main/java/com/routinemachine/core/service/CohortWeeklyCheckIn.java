package com.routinemachine.core.service;

// One bar of the "weekly check-ins" chart (docs/design-reference's cohort.html). `checkedIn`
// is null for a week that hasn't started yet — nothing to report, not a zero. The shared
// denominator for every week is the cohort's current activated-member count, matching the
// design reference's single "of 34 activated" caption rather than a denominator per week.
public record CohortWeeklyCheckIn(int week, Integer checkedIn, boolean isCurrentWeek, boolean isFuture) {
}
