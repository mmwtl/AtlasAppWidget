package com.mmwtl.atlasappwidget;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.Collections;
import java.util.List;

final class AccessibilityWindowState {
    private static volatile Snapshot current = Snapshot.unavailable();
    private static volatile Object connectionToken;
    private static volatile Runnable stateListener;

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

    static void markUnavailable(Object token) {
        if (connectionToken != token) {
            return;
        }
        connectionToken = null;
        current = Snapshot.unavailable();
        notifyStateListener();
    }

    static void markConnected(Object token) {
        connectionToken = token;
        notifyStateListener();
    }

    static void setStateListener(Runnable listener) {
        stateListener = listener;
    }

    static void clearStateListener(Runnable listener) {
        if (stateListener == listener) {
            stateListener = null;
        }
    }

    static boolean isEnabled(Context context) {
        if (connectionToken != null) {
            return true;
        }
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

    private static void notifyStateListener() {
        Runnable listener = stateListener;
        if (listener != null) {
            listener.run();
        }
    }

    static boolean isConfigured(Context context) {
        if (Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            return false;
        }
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null || enabledServices.isEmpty()) {
            return false;
        }
        ComponentName expected = new ComponentName(context, WindowAccessibilityService.class);
        for (String flattened : enabledServices.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(flattened);
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
