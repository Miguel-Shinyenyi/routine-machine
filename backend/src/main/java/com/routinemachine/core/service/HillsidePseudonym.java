package com.routinemachine.core.service;

// Deterministic, from a user id alone: never derived from email, display name, or anything
// else that could be traced back. Same user id always gets the same pseudonym within a
// season (there is no rotation yet — see docs/backend.md's open questions).
public final class HillsidePseudonym {

    private static final String[] ADJECTIVES = {
            "Still", "Quiet", "Slow", "Low", "Patient", "Early", "Steady", "Amber", "Soft", "Far",
    };
    private static final String[] NOUNS = {
            "Brook", "Cedar", "Fern", "Hill", "Reed", "Juniper", "Ridge", "Hollow", "Pine", "Meadow",
    };

    private HillsidePseudonym() {
    }

    public static String forUserId(long userId) {
        long h = Long.hashCode(userId * 2654435761L);
        String adjective = ADJECTIVES[Math.floorMod(h, ADJECTIVES.length)];
        String noun = NOUNS[Math.floorMod(h / ADJECTIVES.length, NOUNS.length)];
        int number = Math.floorMod((int) (userId * 7919 + 1013), 10000);
        return "%s %s %04d".formatted(adjective, noun, number);
    }
}
