package com.routinemachine.core.domain;

public enum DailyLogSource {
    MANUAL("manual"),
    HUB_SYNC("hub-sync");

    private final String value;

    DailyLogSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DailyLogSource fromValue(String value) {
        for (DailyLogSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown daily log source: " + value);
    }
}
