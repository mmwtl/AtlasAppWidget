package com.mmwtl.atlasappwidget;

final class ForegroundEventTracker {
    private long lastTimestamp;
    private String foregroundPackage;
    private boolean observedEvent;

    void onResumed(long timestamp, String packageName) {
        if (timestamp < lastTimestamp || packageName == null) {
            return;
        }
        lastTimestamp = timestamp;
        foregroundPackage = packageName;
        observedEvent = true;
    }

    void onStopped(long timestamp, String packageName) {
        if (timestamp < lastTimestamp || packageName == null) {
            return;
        }
        lastTimestamp = timestamp;
        if (packageName.equals(foregroundPackage)) {
            foregroundPackage = null;
        }
        observedEvent = true;
    }

    void seed(long timestamp, String packageName) {
        if (!observedEvent && timestamp >= lastTimestamp && packageName != null) {
            lastTimestamp = timestamp;
            foregroundPackage = packageName;
        }
    }

    long lastTimestamp() {
        return lastTimestamp;
    }

    String foregroundPackage() {
        return foregroundPackage;
    }

    boolean hasObservedEvent() {
        return observedEvent;
    }
}
