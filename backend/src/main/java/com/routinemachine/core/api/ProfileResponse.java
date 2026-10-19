package com.routinemachine.core.api;

import com.routinemachine.core.domain.User;

import java.util.List;

// Only ever returned to the user themselves, behind the same JWT-protected /api/users/**
// chain GET /api/users/me already uses — never exposed anywhere cohort- or admin-facing. See
// docs/backend.md's decisions log on why this is its own endpoint, separate from UserResponse.
public record ProfileResponse(
        List<String> interests,
        List<String> thinkers,
        List<String> learningPreferences,
        String currency,
        int dayEndHour,
        String timeZone,
        boolean analogiesEnabled) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getInterests(),
                user.getThinkers(),
                user.getLearningPreferences(),
                user.getCurrency(),
                user.getDayEndHour(),
                user.getTimeZone(),
                user.isAnalogiesEnabled());
    }
}
