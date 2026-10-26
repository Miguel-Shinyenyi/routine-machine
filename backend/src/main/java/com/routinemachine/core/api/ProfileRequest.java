package com.routinemachine.core.api;

import java.time.DayOfWeek;
import java.util.List;

// All fields optional: an update only changes what's actually sent, same partial-update
// pattern as the rest of this profile endpoint's own field-by-field defaults in User.updateProfile.
public record ProfileRequest(
        List<String> interests,
        List<String> thinkers,
        List<String> learningPreferences,
        String currency,
        Integer dayEndHour,
        String timeZone,
        Boolean analogiesEnabled,
        DayOfWeek failureModePromptDay) {
}
