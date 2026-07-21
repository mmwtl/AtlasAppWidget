package com.mmwtl.atlasappwidget;

import android.util.Log;
import android.os.SystemClock;

import java.util.concurrent.ConcurrentHashMap;

final class AppLog {
    private static final String TAG = "AtlasAppWidget";
    private static final long RATE_LIMIT_MS = 60_000L;
    private static final ConcurrentHashMap<String, Long> LAST_WARNING =
            new ConcurrentHashMap<>();

    private AppLog() {
    }

    static void warn(String message, Throwable error) {
        Log.w(TAG, message, error);
    }

    static void info(String message) {
        Log.i(TAG, message);
    }

    static void warnRateLimited(String key, String message, Throwable error) {
        long now = SystemClock.elapsedRealtime();
        Long previous = LAST_WARNING.get(key);
        if (previous != null && now - previous < RATE_LIMIT_MS) {
            return;
        }
        LAST_WARNING.put(key, now);
        Log.w(TAG, message, error);
    }
}
