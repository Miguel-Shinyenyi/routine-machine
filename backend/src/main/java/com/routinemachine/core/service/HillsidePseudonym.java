package com.routinemachine.core.service;

import java.time.LocalDate;

// Deterministic, from a user id plus the current "season" alone: never derived from email,
// display name, or anything else that could be traced back, and nothing is stored. The season
// number (days since the epoch, divided into 90-day blocks — "change every season" per the
// design reference) is folded into the hash input, so the same user id gets the same
// pseudonym for ~90 days, then a different one, with no rotation schedule or mapping to keep
// in a database — see docs/backend.md's decisions log for why this beat a stored, explicitly
// rotated pseudonym.
public final class HillsidePseudonym {

    private static final String[] ADJECTIVES = {
            "Still", "Quiet", "Slow", "Low", "Patient", "Early", "Steady", "Amber", "Soft", "Far",
    };
    private static final String[] NOUNS = {
            "Brook", "Cedar", "Fern", "Hill", "Reed", "Juniper", "Ridge", "Hollow", "Pine", "Meadow",
    };
    private static final int SEASON_LENGTH_DAYS = 90;

    private HillsidePseudonym() {
    }

    public static String forUserId(long userId, LocalDate asOf) {
        long season = Math.floorDiv(asOf.toEpochDay(), SEASON_LENGTH_DAYS);
        long seed = userId * 2654435761L + season * 40503L;
        long h = Long.hashCode(seed);
        String adjective = ADJECTIVES[Math.floorMod(h, ADJECTIVES.length)];
        String noun = NOUNS[Math.floorMod(h / ADJECTIVES.length, NOUNS.length)];
        int number = Math.floorMod((int) (seed * 7919 + 1013), 10000);
        return "%s %s %04d".formatted(adjective, noun, number);
    }
}
