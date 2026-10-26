package com.routinemachine.core.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyFailureModeServiceTest {

    @Test
    void aMondayIsTheStartOfItsOwnWeek() {
        assertThat(WeeklyFailureModeService.weekStart(LocalDate.of(2026, 10, 26))).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void everyDayOfTheWeekMapsToItsMonday() {
        for (int d = 26; d <= 31; d++) {
            assertThat(WeeklyFailureModeService.weekStart(LocalDate.of(2026, 10, d))).isEqualTo(LocalDate.of(2026, 10, 26));
        }
        assertThat(WeeklyFailureModeService.weekStart(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(WeeklyFailureModeService.weekStart(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void aWeekCanStartInTheYearBefore() {
        assertThat(WeeklyFailureModeService.weekStart(LocalDate.of(2027, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 28));
    }
}
