package com.routinemachine.core.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HillsidePseudonymTest {

    private static final LocalDate A_DATE = LocalDate.of(2026, 10, 18);

    @Test
    void isDeterministicForTheSameUserIdAndSeason() {
        assertThat(HillsidePseudonym.forUserId(42L, A_DATE)).isEqualTo(HillsidePseudonym.forUserId(42L, A_DATE));
    }

    @Test
    void differsForDifferentUserIds() {
        assertThat(HillsidePseudonym.forUserId(1L, A_DATE)).isNotEqualTo(HillsidePseudonym.forUserId(2L, A_DATE));
    }

    @Test
    void neverContainsTheWordUserOrAnyDigitsBeyondTheTrailingNumber() {
        String pseudonym = HillsidePseudonym.forUserId(7L, A_DATE);
        // "Adjective Noun 1234" shape: exactly two words then a number, nothing identifying.
        assertThat(pseudonym).matches("[A-Z][a-z]+ [A-Z][a-z]+ \\d{4}");
    }

    @Test
    void rotatesToADifferentPseudonymOnceASeasonHasPassed() {
        // Same user, same day-of-year window otherwise, but far enough apart (a full season,
        // 90 days) that the "which season is it" input to the hash has changed.
        String thisSeason = HillsidePseudonym.forUserId(42L, LocalDate.of(2026, 1, 1));
        String nextSeason = HillsidePseudonym.forUserId(42L, LocalDate.of(2026, 4, 5));
        assertThat(thisSeason).isNotEqualTo(nextSeason);
    }

    @Test
    void staysTheSamePseudonymWithinTheSameSeason() {
        String early = HillsidePseudonym.forUserId(42L, LocalDate.of(2026, 1, 1));
        String late = HillsidePseudonym.forUserId(42L, LocalDate.of(2026, 3, 1));
        assertThat(early).isEqualTo(late);
    }
}
