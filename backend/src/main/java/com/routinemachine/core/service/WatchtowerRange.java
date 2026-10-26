package com.routinemachine.core.service;

// The time windows the owner-only page offers (docs/observability.md, part 5), shared by its charts and its logs.
// A request chooses one of these three by name and nothing else about time. The step is the gap between chart
// points, so every window is a few hundred points per series.
public enum WatchtowerRange {
    ONE_HOUR("1h", 3_600, 30),
    SIX_HOURS("6h", 21_600, 60),
    ONE_DAY("24h", 86_400, 300);

    public final String label;
    public final long seconds;
    public final long step;

    WatchtowerRange(String label, long seconds, long step) {
        this.label = label;
        this.seconds = seconds;
        this.step = step;
    }

    public static WatchtowerRange of(String label) {
        for (WatchtowerRange range : values()) {
            if (range.label.equals(label)) {
                return range;
            }
        }
        throw new IllegalArgumentException("range must be one of 1h, 6h, 24h");
    }
}
