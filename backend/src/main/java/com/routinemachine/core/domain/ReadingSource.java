package com.routinemachine.core.domain;

public enum ReadingSource {
    MANUAL("manual"),
    HUB_SYNC("hub-sync");

    private final String value;

    ReadingSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ReadingSource fromValue(String value) {
        for (ReadingSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown reading source: " + value);
    }
}
