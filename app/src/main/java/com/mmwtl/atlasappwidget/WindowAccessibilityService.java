package com.mmwtl.atlasappwidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
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
    private static final long LAUNCHER_APP_LIST_REFRESH_MS = 250L;
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
    private final Runnable launcherAppListRefresh = this::requestWindowRefresh;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 50L;
        setServiceInfo(info);
        requestWindowRefresh();
        startOverlayIfNeeded();
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
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
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
        mainHandler.removeCallbacks(launcherAppListRefresh);
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
        boolean launcherAppListVisible = false;
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    Rect bounds = new Rect();
                    window.getBoundsInScreen(bounds);
                    AccessibilityNodeInfo root = null;
                    String packageName = "";
                    String className = "";
                    boolean windowAppListVisible = false;
                    try {
                        root = window.getRoot();
                        if (root != null) {
                            packageName = text(root.getPackageName());
                            className = text(root.getClassName());
                            if (LauncherAllAppsViewDetector.isLauncherPackage(packageName)) {
                                windowAppListVisible = containsAllAppsMarker(root);
                            }
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
                            bounds.bottom,
                            windowAppListVisible
                    ));
                    launcherAppListVisible |= windowAppListVisible;
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
            scheduleLauncherAppListRefresh(launcherAppListVisible);
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

    private void startOverlayIfNeeded() {
        Prefs prefs = new Prefs(this);
        if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)
                || !Settings.canDrawOverlays(this)
                || !ForegroundAppDetector.hasUsageAccess(this)) {
            return;
        }
        try {
            OverlayService.start(this);
            AppLog.info("Accessibility service connected; requesting overlay service start");
        } catch (RuntimeException error) {
            AppLog.warn("Cannot start overlay service after accessibility connection", error);
        }
    }

    private void scheduleLauncherAppListRefresh(boolean appListVisible) {
        mainHandler.removeCallbacks(launcherAppListRefresh);
        if (appListVisible && !destroyed) {
            mainHandler.postDelayed(launcherAppListRefresh, LAUNCHER_APP_LIST_REFRESH_MS);
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean containsAllAppsMarker(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        try {
            if (!node.isVisibleToUser()) {
                return false;
            }
        } catch (RuntimeException error) {
            AppLog.warnRateLimited(
                    "accessibility-app-list-visibility",
                    "Cannot inspect launcher app-list node visibility",
                    error
            );
            return false;
        }
        if (LauncherAllAppsViewDetector.isAllAppsMarker(
                text(node.getViewIdResourceName()),
                text(node.getClassName()),
                text(node.getText()))) {
            return true;
        }
        int childCount;
        try {
            childCount = node.getChildCount();
        } catch (RuntimeException error) {
            AppLog.warnRateLimited(
                    "accessibility-app-list-child-count",
                    "Cannot inspect launcher app-list child count",
                    error
            );
            return false;
        }
        for (int index = 0; index < childCount; index++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(index);
                if (containsAllAppsMarker(child)) {
                    return true;
                }
            } catch (RuntimeException error) {
                AppLog.warnRateLimited(
                        "accessibility-app-list",
                        "Cannot inspect launcher app-list node",
                        error
                );
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
