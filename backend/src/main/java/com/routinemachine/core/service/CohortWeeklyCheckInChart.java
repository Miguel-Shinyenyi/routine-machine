package com.routinemachine.core.service;

import java.util.List;

public record CohortWeeklyCheckInChart(int activatedDenominator, List<CohortWeeklyCheckIn> weeks) {
}
