package com.mmwtl.atlasappwidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
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
    private Prefs prefs;
    private final Runnable launcherAppListRefresh = this::requestWindowRefresh;
    /** Held as a field: SharedPreferences keeps its change listeners weakly. */
    private final SharedPreferences.OnSharedPreferenceChangeListener displayModeListener =
            (values, key) -> {
                if (Prefs.KEY_DISPLAY_MODE.equals(key) && !destroyed) {
                    applyEventSubscription();
                    requestWindowRefresh();
                }
            };
    private Boolean subscribedForOverlay;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
        prefs.raw().registerOnSharedPreferenceChangeListener(displayModeListener);
        applyEventSubscription();
        AccessibilityWindowState.markConnected(this);
        requestWindowRefresh();
        startOverlayIfNeeded();
        AppLog.info("Window accessibility service connected");
    }

    /**
     * Widget mode needs no window tracking, so the service unsubscribes from every event type.
     * Apps only emit accessibility event types some enabled service listens to, which removes
     * the event traffic both in other apps and in this process until overlay mode returns.
     */
    private void applyEventSubscription() {
        boolean overlay = !prefs.isWidgetMode();
        if (subscribedForOverlay != null && subscribedForOverlay == overlay) {
            return;
        }
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            return;
        }
        int trackingFlags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        if (overlay) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED;
            info.flags |= trackingFlags;
        } else {
            info.eventTypes = 0;
            info.flags &= ~trackingFlags;
            mainHandler.removeCallbacks(launcherAppListRefresh);
        }
        info.notificationTimeout = 50L;
        setServiceInfo(info);
        subscribedForOverlay = overlay;
        AppLog.info(overlay
                ? "Accessibility window tracking enabled for overlay mode"
                : "Accessibility window tracking paused for widget mode");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || prefs == null || prefs.isWidgetMode()) {
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
        if (prefs != null) {
            prefs.raw().unregisterOnSharedPreferenceChangeListener(displayModeListener);
        }
        mainHandler.removeCallbacks(launcherAppListRefresh);
        windowReader.shutdownNow();
        AccessibilityWindowState.markUnavailable(this);
        notifyOverlayService();
        AppLog.info("Window accessibility service disconnected");
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void requestWindowRefresh() {
        if (destroyed || prefs == null || prefs.isWidgetMode()) {
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
            if (destroyed || prefs.isWidgetMode()) {
                return;
            }
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
            if (destroyed || prefs.isWidgetMode()) {
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
        if (prefs.isWidgetMode() || !prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)
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
