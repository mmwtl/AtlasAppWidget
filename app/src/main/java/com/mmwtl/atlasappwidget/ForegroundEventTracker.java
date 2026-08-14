package com.mmwtl.atlasappwidget;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Reconstructs which activities are still visible from the usage-event lifecycle stream. */
final class ForegroundEventTracker {
    private static final String UNKNOWN_ACTIVITY = "<unknown>";

    private final Map<ActivityKey, ActivityState> activities = new HashMap<>();
    private long lastTimestamp;
    private long lastResetTimestamp;
    private boolean observedEvent;

    void onResumed(long timestamp, String packageName, String className) {
        update(timestamp, packageName, className, true);
    }

    void onPaused(long timestamp, String packageName, String className) {
        // Since Android 10, a paused activity may remain visible beside a focused activity.
        // Visibility ends at onStop(), not at onPause().
        update(timestamp, packageName, className, true);
    }

    void onStopped(long timestamp, String packageName, String className) {
        update(timestamp, packageName, className, false);
    }

    void onReset(long timestamp) {
        if (timestamp < lastResetTimestamp) {
            return;
        }
        lastResetTimestamp = timestamp;
        lastTimestamp = Math.max(lastTimestamp, timestamp);
        activities.clear();
        observedEvent = true;
    }

    void seed(long timestamp, String packageName) {
        if (!observedEvent && timestamp >= lastTimestamp && packageName != null) {
            lastTimestamp = timestamp;
            activities.clear();
            activities.put(
                    new ActivityKey(packageName, UNKNOWN_ACTIVITY),
                    new ActivityState(timestamp, true)
            );
        }
    }

    boolean isAnyPackageVisible(Set<String> packageNames) {
        if (packageNames == null || packageNames.isEmpty()) {
            return false;
        }
        for (Map.Entry<ActivityKey, ActivityState> entry : activities.entrySet()) {
            if (entry.getValue().visible && packageNames.contains(entry.getKey().packageName)) {
                return true;
            }
        }
        return false;
    }

    boolean hasObservedEvent() {
        return observedEvent;
    }

    VisibleActivity mostRecentVisibleActivity() {
        ActivityKey newestKey = null;
        ActivityState newestState = null;
        for (Map.Entry<ActivityKey, ActivityState> entry : activities.entrySet()) {
            ActivityState state = entry.getValue();
            if (!state.visible || (newestState != null && state.timestamp < newestState.timestamp)) {
                continue;
            }
            newestKey = entry.getKey();
            newestState = state;
        }
        return newestKey == null ? null : new VisibleActivity(
                newestKey.packageName,
                UNKNOWN_ACTIVITY.equals(newestKey.className) ? "" : newestKey.className,
                newestState.timestamp
        );
    }

    private void update(
            long timestamp,
            String packageName,
            String className,
            boolean visible
    ) {
        if (timestamp < lastResetTimestamp || packageName == null) {
            return;
        }
        if (!observedEvent) {
            // A UsageStats seed is only a startup guess. The first real lifecycle event starts
            // an authoritative reconstruction and must not leave that guess visible forever.
            activities.clear();
        }
        if (!visible && className == null) {
            for (Map.Entry<ActivityKey, ActivityState> entry : activities.entrySet()) {
                if (packageName.equals(entry.getKey().packageName)
                        && timestamp >= entry.getValue().timestamp) {
                    entry.setValue(new ActivityState(timestamp, false));
                }
            }
        }
        ActivityKey key = new ActivityKey(
                packageName,
                className == null ? UNKNOWN_ACTIVITY : className
        );
        ActivityState previous = activities.get(key);
        if (previous != null && timestamp < previous.timestamp) {
            return;
        }
        activities.put(key, new ActivityState(timestamp, visible));
        lastTimestamp = Math.max(lastTimestamp, timestamp);
        observedEvent = true;
    }

    private static final class ActivityKey {
        final String packageName;
        final String className;

        ActivityKey(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ActivityKey)) return false;
            ActivityKey that = (ActivityKey) other;
            return packageName.equals(that.packageName) && className.equals(that.className);
        }

        @Override
        public int hashCode() {
            return 31 * packageName.hashCode() + className.hashCode();
        }
    }

    private static final class ActivityState {
        final long timestamp;
        final boolean visible;

        ActivityState(long timestamp, boolean visible) {
            this.timestamp = timestamp;
            this.visible = visible;
        }
    }

    static final class VisibleActivity {
        final String packageName;
        final String className;
        final long timestamp;

        VisibleActivity(String packageName, String className, long timestamp) {
            this.packageName = packageName;
            this.className = className;
            this.timestamp = timestamp;
        }
    }
}
