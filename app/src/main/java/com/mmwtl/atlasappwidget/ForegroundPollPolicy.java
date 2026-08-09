package com.mmwtl.atlasappwidget;

final class ForegroundPollPolicy {
    static final long INITIAL_QUERY_WINDOW_MS = 5L * 60L * 1_000L;
    static final long INCREMENTAL_QUERY_WINDOW_MS = 60_000L;
    static final long QUERY_OVERLAP_MS = 2_000L;
    static final long FAST_PROBE_DURATION_MS = 10_000L;
    static final long FAST_PROBE_DELAY_MS = 250L;
    static final long VISIBLE_DELAY_MS = 1_000L;
    static final long HIDDEN_DELAY_MS = 1_500L;

    private ForegroundPollPolicy() {
    }

    static long queryBegin(long now, long lastQuery) {
        if (lastQuery <= 0L || lastQuery > now) {
            return Math.max(0L, now - INITIAL_QUERY_WINDOW_MS);
        }
        return Math.max(0L,
                Math.max(now - INCREMENTAL_QUERY_WINDOW_MS, lastQuery - QUERY_OVERLAP_MS));
    }

    static long nextDelay(
            boolean homeVisible,
            boolean deviceReady,
            long now,
            long fastProbeUntil
    ) {
        if (!deviceReady) {
            return HIDDEN_DELAY_MS;
        }
        if (homeVisible) {
            return VISIBLE_DELAY_MS;
        }
        return now < fastProbeUntil ? FAST_PROBE_DELAY_MS : HIDDEN_DELAY_MS;
    }
}
