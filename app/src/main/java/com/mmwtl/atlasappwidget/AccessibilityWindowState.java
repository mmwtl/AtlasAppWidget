package com.mmwtl.atlasappwidget;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityManager;

import java.util.Collections;
import java.util.List;

final class AccessibilityWindowState {
    private static volatile Snapshot current = Snapshot.unavailable();

    private AccessibilityWindowState() {
    }

    static Snapshot current() {
        return current;
    }

    static void update(
            List<WindowObservation> windows,
            int displayWidth,
            int displayHeight,
            String eventPackage,
            String eventClass
    ) {
        current = new Snapshot(
                true,
                List.copyOf(windows),
                displayWidth,
                displayHeight,
                value(eventPackage),
                value(eventClass),
                SystemClock.elapsedRealtime()
        );
    }

    static void markUnavailable() {
        current = Snapshot.unavailable();
    }

    static boolean isEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()) {
            return false;
        }
        ComponentName expected = new ComponentName(context, WindowAccessibilityService.class);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : services) {
            if (service.getResolveInfo() == null || service.getResolveInfo().serviceInfo == null) {
                continue;
            }
            ComponentName component = new ComponentName(
                    service.getResolveInfo().serviceInfo.packageName,
                    service.getResolveInfo().serviceInfo.name
            );
            if (expected.equals(component)) {
                return true;
            }
        }
        return false;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    static final class Snapshot {
        final boolean available;
        final List<WindowObservation> windows;
        final int displayWidth;
        final int displayHeight;
        final String eventPackage;
        final String eventClass;
        final long updatedAtElapsedRealtime;

        Snapshot(
                boolean available,
                List<WindowObservation> windows,
                int displayWidth,
                int displayHeight,
                String eventPackage,
                String eventClass,
                long updatedAtElapsedRealtime
        ) {
            this.available = available;
            this.windows = windows;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.eventPackage = eventPackage;
            this.eventClass = eventClass;
            this.updatedAtElapsedRealtime = updatedAtElapsedRealtime;
        }

        static Snapshot unavailable() {
            return new Snapshot(false, Collections.emptyList(), 0, 0, "", "", 0L);
        }
    }
}
