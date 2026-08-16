package com.mmwtl.atlasappwidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class WindowAccessibilityService extends AccessibilityService {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService windowReader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atlas-accessibility-windows");
        thread.setDaemon(true);
        return thread;
    });
    private String lastEventPackage = "";
    private String lastEventClass = "";
    private boolean refreshInFlight;
    private boolean refreshPending;
    private volatile boolean destroyed;

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
        requestWindowRefresh();
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
            requestWindowRefresh();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        refreshPending = false;
        windowReader.shutdownNow();
        AccessibilityWindowState.markUnavailable();
        notifyOverlayService();
        AppLog.info("Window accessibility service disconnected");
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void requestWindowRefresh() {
        if (destroyed) {
            return;
        }
        if (refreshInFlight) {
            refreshPending = true;
            return;
        }
        refreshInFlight = true;
        String eventPackage = lastEventPackage;
        String eventClass = lastEventClass;
        try {
            windowReader.execute(() -> refreshWindows(eventPackage, eventClass));
        } catch (RejectedExecutionException ignored) {
            refreshInFlight = false;
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshWindows(String eventPackage, String eventClass) {
        long startedAt = SystemClock.elapsedRealtime();
        List<WindowObservation> observations = new ArrayList<>();
        boolean eventWindowPresent = false;
        try {
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
                    if (packageName.equals(eventPackage)
                            && (window.isActive() || window.isFocused())
                            && !eventClass.isEmpty()) {
                        className = eventClass;
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
            if (destroyed) {
                return;
            }
            AccessibilityWindowState.update(
                    observations,
                    display.width(),
                    display.height(),
                    eventWindowPresent ? eventPackage : "",
                    eventWindowPresent ? eventClass : ""
            );
            notifyOverlayService();
        } catch (RuntimeException error) {
            AppLog.warnRateLimited(
                    "accessibility-windows",
                    "Cannot inspect accessibility windows",
                    error
            );
        } finally {
            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            if (elapsed >= 100L) {
                AppLog.info("Accessibility window snapshot completed in " + elapsed + " ms");
            }
            mainHandler.post(this::finishWindowRefresh);
        }
    }

    private void finishWindowRefresh() {
        refreshInFlight = false;
        if (destroyed || !refreshPending) {
            return;
        }
        refreshPending = false;
        requestWindowRefresh();
    }

    private void notifyOverlayService() {
        OverlayService.onAccessibilityWindowsChanged();
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
