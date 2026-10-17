package com.routinemachine.core.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HillsidePseudonymTest {

    @Test
    void isDeterministicForTheSameUserId() {
        assertThat(HillsidePseudonym.forUserId(42L)).isEqualTo(HillsidePseudonym.forUserId(42L));
    }

    @Test
    void differsForDifferentUserIds() {
        assertThat(HillsidePseudonym.forUserId(1L)).isNotEqualTo(HillsidePseudonym.forUserId(2L));
    }

    @Test
    void neverContainsTheWordUserOrAnyDigitsBeyondTheTrailingNumber() {
        String pseudonym = HillsidePseudonym.forUserId(7L);
        // "Adjective Noun 1234" shape: exactly two words then a number, nothing identifying.
        assertThat(pseudonym).matches("[A-Z][a-z]+ [A-Z][a-z]+ \\d{4}");
    }
}
