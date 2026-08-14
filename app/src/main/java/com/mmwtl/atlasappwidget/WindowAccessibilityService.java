package com.mmwtl.atlasappwidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

public final class WindowAccessibilityService extends AccessibilityService {
    private String lastEventPackage = "";
    private String lastEventClass = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 50L;
        setServiceInfo(info);
        refreshWindows();
        AppLog.info("Window accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastEventPackage = text(event.getPackageName());
            lastEventClass = text(event.getClassName());
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            refreshWindows();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        AccessibilityWindowState.markUnavailable();
        notifyOverlayService();
        AppLog.info("Window accessibility service disconnected");
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void refreshWindows() {
        List<WindowObservation> observations = new ArrayList<>();
        boolean eventWindowPresent = false;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                Rect bounds = new Rect();
                window.getBoundsInScreen(bounds);
                AccessibilityNodeInfo root = null;
                String packageName = "";
                String className = "";
                try {
                    root = window.getRoot();
                    if (root != null) {
                        packageName = text(root.getPackageName());
                        className = text(root.getClassName());
                    }
                } catch (RuntimeException error) {
                    AppLog.warnRateLimited(
                            "accessibility-window-root",
                            "Cannot inspect accessibility window root",
                            error
                    );
                } finally {
                    if (root != null) {
                        // AccessibilityNodeInfo is pooled through API 32. The call is a
                        // no-op on newer releases, where pooling was removed.
                        root.recycle();
                    }
                }
                if (packageName.equals(lastEventPackage)
                        && (window.isActive() || window.isFocused())
                        && !lastEventClass.isEmpty()) {
                    className = lastEventClass;
                    eventWindowPresent = true;
                }
                observations.add(new WindowObservation(
                        packageName,
                        className,
                        window.getType(),
                        window.isActive(),
                        window.isFocused(),
                        window.getLayer(),
                        bounds.left,
                        bounds.top,
                        bounds.right,
                        bounds.bottom
                ));
            }
        }
        WindowManager manager = getSystemService(WindowManager.class);
        WindowMetrics metrics = manager.getCurrentWindowMetrics();
        Rect display = metrics.getBounds();
        AccessibilityWindowState.update(
                observations,
                display.width(),
                display.height(),
                eventWindowPresent ? lastEventPackage : "",
                eventWindowPresent ? lastEventClass : ""
        );
        notifyOverlayService();
    }

    private void notifyOverlayService() {
        OverlayService.onAccessibilityWindowsChanged();
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
