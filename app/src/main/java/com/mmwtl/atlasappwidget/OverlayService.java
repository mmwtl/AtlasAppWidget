package com.mmwtl.atlasappwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Insets;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class OverlayService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, PanelView.Listener {
    static final String ACTION_START = "com.mmwtl.atlasappwidget.action.START";
    static final String ACTION_STOP = "com.mmwtl.atlasappwidget.action.STOP";

    private static final String CHANNEL_ID = "atlas_app_widget_service";
    private static final int NOTIFICATION_ID = 2107;
    private static final int POLL_ERROR_MS = 2_000;
    private static final int SYSTEM_STATUS_REFRESH_MS = 2_000;
    private static final int NOTIFICATION_VISIBLE = 1;
    private static final int NOTIFICATION_HIDDEN = 2;
    private static final int NOTIFICATION_PERMISSION_ERROR = 3;
    private static final int NOTIFICATION_NO_APPS = 5;
    static final long FUEL_DETAILS_AUTO_HIDE_DELAY_MS = 10_000L;
    private static volatile boolean running;
    private static volatile OverlayService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService foregroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atlas-home-detector");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService systemStatusExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService panelPreparationExecutor = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "atlas-panel-preparation");
                thread.setDaemon(true);
                return thread;
            });
    private Prefs prefs;
    private WindowManager windowManager;
    private ForegroundAppDetector foregroundDetector;
    private FuelLevelProvider fuelLevelProvider;
    private SystemMetricsSampler systemMetricsSampler;
    private PanelView panel;
    private WindowManager.LayoutParams panelParams;
    private int panelBoundsWidth;
    private int panelBoundsHeight;
    private List<AppEntry> selectedEntriesCache;
    private PanelView panelPreparationTarget;
    private boolean panelPreparationInFlight;
    private FuelDetailsView fuelDetailsView;
    private Runnable fuelDetailsAutoHide;
    private final PanelSuppressionPolicy panelSuppression = new PanelSuppressionPolicy();
    private Boolean lastAppliedHomeVisible;
    private final Runnable applyPreferenceChanges = () -> {
        syncFuelProvider();
        boolean wasAttached = isPanelAttached();
        selectedEntriesCache = null;
        discardPanel();
        if (wasAttached) {
            showPanel();
        }
    };
    private int currentNotificationState;
    private long nextSystemStatusRefresh;
    private long createdAt;
    private long fastProbeUntil;
    private boolean metricsActive;
    private boolean foregroundQueryInFlight;
    private boolean visibilityCheckPending;
    private boolean visibilityCheckPendingFast;
    private boolean deviceWasReady;
    private boolean visibilityReceiverRegistered;
    private boolean packageReceiverRegistered;
    private boolean localeReceiverRegistered;
    private boolean destroyed;

    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartWindowX;
    private int dragStartWindowY;

    private final BroadcastReceiver visibilityWakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                deviceWasReady = false;
                hidePanel();
                scheduleForegroundPoll(ForegroundPollPolicy.HIDDEN_DELAY_MS);
                return;
            }
            AppLog.info("Immediate HOME check requested by " + action);
            requestImmediateVisibilityCheck();
        }
    };

    private final BroadcastReceiver packageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            selectedEntriesCache = null;
            boolean wasAttached = isPanelAttached();
            discardPanel();
            if (wasAttached) {
                requestImmediateVisibilityCheck();
            }
        }
    };

    private final BroadcastReceiver localeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            selectedEntriesCache = null;
            boolean wasAttached = isPanelAttached();
            discardPanel();
            if (wasAttached) {
                showPanel();
            }
        }
    };

    private final Runnable foregroundPoll = () -> runForegroundPoll(false);
    private final Runnable accessibilityFastPoll = () -> runForegroundPoll(true);

    static void start(android.content.Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createdAt = SystemClock.elapsedRealtime();
        prefs = new Prefs(this);
        windowManager = getSystemService(WindowManager.class);
        fuelLevelProvider = new FuelLevelProvider(this, prefs);
        systemMetricsSampler = new SystemMetricsSampler(this, fuelLevelProvider);
        prefs.raw().registerOnSharedPreferenceChangeListener(this);
        createNotificationChannel();
        Notification notification = buildNotification(NOTIFICATION_HIDDEN);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        currentNotificationState = NOTIFICATION_HIDDEN;
        running = true;
        instance = this;
        fastProbeUntil = createdAt + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        registerVisibilityWakeReceiver();
        registerPackageChangeReceiver();
        registerLocaleChangeReceiver();
        AppLog.info("Overlay service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        requestImmediateVisibilityCheck();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        unregisterVisibilityWakeReceiver();
        unregisterPackageChangeReceiver();
        unregisterLocaleChangeReceiver();
        hidePanel();
        foregroundExecutor.shutdownNow();
        systemStatusExecutor.shutdownNow();
        panelPreparationExecutor.shutdownNow();
        if (fuelLevelProvider != null) {
            fuelLevelProvider.stop();
        }
        if (prefs != null) {
            prefs.raw().unregisterOnSharedPreferenceChangeListener(this);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        running = false;
        if (instance == this) {
            instance = null;
        }
        AppLog.info("Overlay service destroyed");
        super.onDestroy();
    }

    private void runForegroundPoll(boolean accessibilityFast) {
        if (destroyed) {
            return;
        }
        if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            stopSelf();
            return;
        }
        if (!Settings.canDrawOverlays(OverlayService.this)
                || !ForegroundAppDetector.hasUsageAccess(OverlayService.this)
                || !AccessibilityWindowState.isEnabled(OverlayService.this)) {
            hidePanel();
            updateNotification(NOTIFICATION_PERMISSION_ERROR);
            scheduleForegroundPoll(POLL_ERROR_MS);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        ForegroundAppDetector detector = foregroundDetector();
        boolean deviceReady = detector.isDeviceReady();
        if (deviceReady && !deviceWasReady) {
            fastProbeUntil = now + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        }
        deviceWasReady = deviceReady;
        if (!deviceReady) {
            hidePanel();
            updateNotification(NOTIFICATION_HIDDEN);
            scheduleForegroundPoll(ForegroundPollPolicy.nextDelay(
                    false, false, now, fastProbeUntil));
            return;
        }
        if (foregroundQueryInFlight) {
            if (!visibilityCheckPending) {
                visibilityCheckPendingFast = accessibilityFast;
            } else {
                visibilityCheckPendingFast &= accessibilityFast;
            }
            visibilityCheckPending = true;
            return;
        }
        foregroundQueryInFlight = true;
        try {
            foregroundExecutor.execute(() -> {
                boolean homeVisible = false;
                String source = accessibilityFast ? "accessibility" : "usage";
                try {
                    if (accessibilityFast) {
                        Boolean fastResult = detector.isHomeVisibleFromAccessibility();
                        if (fastResult != null) {
                            homeVisible = fastResult;
                        } else {
                            homeVisible = detector.isHomeVisible();
                            source = "usage-fallback";
                        }
                    } else {
                        homeVisible = detector.isHomeVisible();
                    }
                } catch (RuntimeException error) {
                    AppLog.warn("HOME visibility query failed", error);
                }
                boolean result = homeVisible;
                String resultSource = source;
                handler.post(() -> applyForegroundResult(result, resultSource));
            });
        } catch (RejectedExecutionException ignored) {
            foregroundQueryInFlight = false;
        }
    }

    private void applyForegroundResult(boolean homeVisible, String source) {
        if (destroyed) {
            return;
        }
        foregroundQueryInFlight = false;
        if (visibilityCheckPending) {
            boolean fast = visibilityCheckPendingFast;
            visibilityCheckPending = false;
            visibilityCheckPendingFast = false;
            handler.post(fast ? accessibilityFastPoll : foregroundPoll);
            return;
        }
        if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            stopSelf();
            return;
        }
        boolean deviceReady = foregroundDetector().isDeviceReady();
        long now = SystemClock.elapsedRealtime();
        panelSuppression.onVisibility(homeVisible, now);
        boolean panelAllowed = panelSuppression.isPanelAllowed(now);
        if (lastAppliedHomeVisible == null || lastAppliedHomeVisible != homeVisible) {
            AccessibilityWindowState.Snapshot snapshot = AccessibilityWindowState.current();
            long snapshotAge = snapshot.updatedAtElapsedRealtime <= 0L
                    ? -1L
                    : Math.max(0L, now - snapshot.updatedAtElapsedRealtime);
            AppLog.info("HOME visibility changed to " + homeVisible
                    + " via " + source + " (snapshot age " + snapshotAge + " ms)");
            lastAppliedHomeVisible = homeVisible;
        }
        boolean hasApps = false;
        if (homeVisible && deviceReady && panelAllowed) {
            hasApps = showPanel();
            if (hasApps) {
                if (!metricsActive) {
                    metricsActive = true;
                    syncFuelProvider();
                }
                refreshSystemStatusIfNeeded();
            }
            updateNotification(hasApps ? NOTIFICATION_VISIBLE : NOTIFICATION_NO_APPS);
        } else if (homeVisible && deviceReady) {
            // A launch suppression keeps a panel that is still visible on HOME in place. The
            // panel is removed only after the detector confirms that HOME is no longer visible.
            updateNotification(isPanelAttached()
                    ? NOTIFICATION_VISIBLE : NOTIFICATION_HIDDEN);
        } else {
            hidePanel();
            updateNotification(NOTIFICATION_HIDDEN);
        }
        long nextDelay = homeVisible && deviceReady && panelAllowed && !hasApps
                ? POLL_ERROR_MS
                : ForegroundPollPolicy.nextDelay(
                        homeVisible && panelAllowed, deviceReady, now, fastProbeUntil);
        if (!panelAllowed && homeVisible) {
            long remaining = Math.max(1L, panelSuppression.deadline() - now);
            nextDelay = Math.min(nextDelay, Math.min(
                    remaining, ForegroundPollPolicy.VISIBLE_DELAY_MS));
        }
        scheduleForegroundPoll(nextDelay);
    }

    private void requestImmediateVisibilityCheck() {
        requestImmediateVisibilityCheck(false);
    }

    private void requestImmediateVisibilityCheck(boolean accessibilityEvent) {
        fastProbeUntil = SystemClock.elapsedRealtime()
                + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        handler.removeCallbacks(foregroundPoll);
        handler.removeCallbacks(accessibilityFastPoll);
        if (foregroundQueryInFlight) {
            if (!visibilityCheckPending) {
                visibilityCheckPendingFast = accessibilityEvent;
            } else {
                visibilityCheckPendingFast &= accessibilityEvent;
            }
            visibilityCheckPending = true;
        } else {
            handler.post(accessibilityEvent ? accessibilityFastPoll : foregroundPoll);
        }
    }

    private void scheduleForegroundPoll(long delayMs) {
        if (destroyed) {
            return;
        }
        handler.removeCallbacks(foregroundPoll);
        handler.removeCallbacks(accessibilityFastPoll);
        handler.postDelayed(foregroundPoll, delayMs);
    }

    @SuppressWarnings("deprecation")
    private void registerVisibilityWakeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(visibilityWakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(visibilityWakeReceiver, filter);
        }
        visibilityReceiverRegistered = true;
    }

    private void unregisterVisibilityWakeReceiver() {
        if (!visibilityReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(visibilityWakeReceiver);
        } catch (IllegalArgumentException ignored) {
            // The process may already have discarded receiver registration.
        }
        visibilityReceiverRegistered = false;
    }

    @SuppressWarnings("deprecation")
    private void registerPackageChangeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageChangeReceiver, filter);
        }
        packageReceiverRegistered = true;
    }

    private void unregisterPackageChangeReceiver() {
        if (!packageReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(packageChangeReceiver);
        } catch (IllegalArgumentException ignored) {
            // The process may already have discarded receiver registration.
        }
        packageReceiverRegistered = false;
    }

    @SuppressWarnings("deprecation")
    private void registerLocaleChangeReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(localeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(localeChangeReceiver, filter);
        }
        localeReceiverRegistered = true;
    }

    private void unregisterLocaleChangeReceiver() {
        if (!localeReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(localeChangeReceiver);
        } catch (IllegalArgumentException ignored) {
            // The process may already have discarded receiver registration.
        }
        localeReceiverRegistered = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean isRunning() {
        return running;
    }

    static void onAccessibilityWindowsChanged() {
        OverlayService service = instance;
        if (service != null && !service.destroyed) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                service.requestImmediateVisibilityCheck(true);
            } else {
                service.handler.post(() -> service.requestImmediateVisibilityCheck(true));
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Prefs.KEY_POSITION_X.equals(key) || Prefs.KEY_POSITION_Y.equals(key)
                || Prefs.KEY_SERVICE_ENABLED.equals(key)
                || Prefs.KEY_AUTO_START.equals(key)) {
            return;
        }
        handler.removeCallbacks(applyPreferenceChanges);
        handler.post(applyPreferenceChanges);
    }

    private boolean showPanel() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (panel != null) {
            if (isPanelAttached()) {
                panel.setVisibility(View.VISIBLE);
                return true;
            }
            Rect bounds = availableBounds();
            if (panelBoundsWidth == bounds.width() && panelBoundsHeight == bounds.height()
                    && panelParams != null) {
                clampPosition(panelParams, panel, bounds);
                try {
                    panel.setVisibility(View.VISIBLE);
                    windowManager.addView(panel, panelParams);
                    nextSystemStatusRefresh = 0;
                    AppLog.info("Overlay panel reattached in "
                            + (SystemClock.elapsedRealtime() - createdAt) + " ms");
                    return true;
                } catch (SecurityException | WindowManager.BadTokenException
                         | IllegalArgumentException | IllegalStateException error) {
                    AppLog.warn("Cannot reattach cached overlay window", error);
                    discardPanel();
                }
            } else {
                discardPanel();
            }
        }
        Rect bounds = availableBounds();
        List<AppEntry> entries = selectedEntriesCache;
        boolean iconsReady = entries != null;
        if (entries == null) {
            entries = AppRepository.placeholderSelectedActivities(this, prefs);
        }
        if (entries.isEmpty()) {
            discardPanel();
            return false;
        }
        PanelView candidate = new PanelView(
                this,
                prefs,
                prefs.panelConfig(),
                entries,
                false,
                bounds.width(),
                bounds.height(),
                this,
                iconsReady
        );

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                candidate.panelWidth(),
                candidate.panelHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        int storedX = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int storedY = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        params.x = storedX == Prefs.POSITION_UNSET
                ? bounds.left + Math.max(0, (bounds.width() - candidate.panelWidth()) / 2)
                : storedX - candidate.outlineInset();
        params.y = storedY == Prefs.POSITION_UNSET
                ? bounds.top + Math.max(0,
                Math.round((bounds.height() - candidate.panelHeight()) * 0.72f))
                : storedY - candidate.outlineInset();
        clampPosition(params, candidate, bounds);

        try {
            windowManager.addView(candidate, params);
            panel = candidate;
            panelParams = params;
            panelBoundsWidth = bounds.width();
            panelBoundsHeight = bounds.height();
            nextSystemStatusRefresh = 0;
            AppLog.info("Overlay panel attached t+"
                    + (SystemClock.elapsedRealtime() - createdAt)
                    + " ms after service creation");
            if (!iconsReady) {
                preparePanelEntries(candidate, candidate.actualIconSize());
            }
            return true;
        } catch (SecurityException | WindowManager.BadTokenException
                 | IllegalArgumentException | IllegalStateException error) {
            panel = null;
            panelParams = null;
            panelBoundsWidth = 0;
            panelBoundsHeight = 0;
            updateNotification(NOTIFICATION_PERMISSION_ERROR);
            AppLog.warn("Cannot attach overlay window", error);
            return false;
        }
    }

    private ForegroundAppDetector foregroundDetector() {
        if (foregroundDetector == null) {
            foregroundDetector = new ForegroundAppDetector(this);
        }
        return foregroundDetector;
    }

    private void preparePanelEntries(PanelView target, int targetPixels) {
        if (destroyed || target == null || panel != target) {
            return;
        }
        if (panelPreparationInFlight && panelPreparationTarget == target) {
            return;
        }
        panelPreparationTarget = target;
        panelPreparationInFlight = true;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            panelPreparationExecutor.execute(() -> {
                List<AppEntry> prepared = List.of();
                try {
                    List<AppEntry> entries = AppRepository.loadSelectedActivities(this, prefs);
                    for (AppEntry entry : entries) {
                        if (!entry.isFuel()) {
                            IconLoader.load(this, prefs, entry, targetPixels);
                        }
                    }
                    prepared = List.copyOf(entries);
                } catch (RuntimeException error) {
                    AppLog.warn("Panel content preparation failed", error);
                }
                List<AppEntry> result = prepared;
                handler.post(() -> {
                    if (panelPreparationTarget == target) {
                        panelPreparationTarget = null;
                        panelPreparationInFlight = false;
                    }
                    if (destroyed || panel != target) {
                        return;
                    }
                    if (result.isEmpty()) {
                        AppLog.info("Panel content preparation returned no activities; retrying");
                        handler.postDelayed(
                                () -> preparePanelEntries(target, targetPixels),
                                ForegroundPollPolicy.HIDDEN_DELAY_MS
                        );
                        return;
                    }
                    selectedEntriesCache = result;
                    target.updateEntries(result);
                    AppLog.info("Panel content prepared in "
                            + (SystemClock.elapsedRealtime() - startedAt) + " ms");
                });
            });
        } catch (RejectedExecutionException ignored) {
            panelPreparationInFlight = false;
            panelPreparationTarget = null;
        }
    }

    private void hidePanel() {
        dismissFuelDetails();
        PanelView target = panel;
        if (target == null) {
            panelParams = null;
            return;
        }
        // isAttachedToWindow() is not a safe gate for removal. During a WindowManager traversal it
        // can briefly be false while the root is still registered. If discardPanel() clears our
        // only reference in that interval, the old window remains clickable and a second panel is
        // added later. Hide synchronously, then always ask WindowManager to remove the known view.
        target.setVisibility(View.GONE);
        if (windowManager != null) {
            try {
                windowManager.removeViewImmediate(target);
            } catch (IllegalArgumentException ignored) {
                // The system already removed the window. It cannot remain as an orphan.
            } catch (IllegalStateException error) {
                // WindowManager may be completing an earlier removal. The view is already hidden
                // and therefore cannot remain as a visible, interactive orphan.
                AppLog.warnRateLimited(
                        "overlay-remove", "Cannot remove overlay panel", error);
            }
        }
        if (systemMetricsSampler != null) {
            try {
                systemStatusExecutor.execute(systemMetricsSampler::resetCpuBaseline);
            } catch (RejectedExecutionException ignored) {
                // Service teardown already stopped the sampler.
            }
        }
        nextSystemStatusRefresh = 0;
    }

    private boolean isPanelAttached() {
        return panel != null && panel.isAttachedToWindow();
    }

    private void discardPanel() {
        hidePanel();
        panel = null;
        panelParams = null;
        panelBoundsWidth = 0;
        panelBoundsHeight = 0;
        panelPreparationTarget = null;
        panelPreparationInFlight = false;
    }

    private void refreshSystemStatusIfNeeded() {
        if (panel == null || !panel.needsMetricUpdates()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < nextSystemStatusRefresh) {
            return;
        }
        nextSystemStatusRefresh = now + SYSTEM_STATUS_REFRESH_MS;
        PanelView target = panel;
        boolean sampleCpu = target.needsCpuUpdates();
        boolean sampleRam = target.needsRamUpdates();
        boolean sampleFuel = target.needsFuelUpdates();
        try {
            systemStatusExecutor.execute(() -> {
                SystemStatusSnapshot snapshot = systemMetricsSampler.sample(
                        sampleCpu,
                        sampleRam,
                        sampleFuel
                );
                FuelLevelProvider.Reading fuelReading = sampleFuel
                        ? fuelLevelProvider.reading()
                        : FuelLevelProvider.Reading.unavailable();
                handler.post(() -> {
                    if (running && panel == target && target.isAttachedToWindow()) {
                        target.updateSystemStatus(snapshot, fuelReading);
                        if (fuelDetailsView != null
                                && fuelDetailsView.isAttachedToWindow()) {
                            fuelDetailsView.update(fuelReading);
                        }
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Service teardown already stopped the sampler.
        }
    }

    @Override
    public boolean onHandleTouch(View view, MotionEvent event) {
        if (panel == null || panelParams == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartWindowX = panelParams.x;
                dragStartWindowY = panelParams.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                panelParams.x = dragStartWindowX + Math.round(event.getRawX() - dragStartRawX);
                panelParams.y = dragStartWindowY + Math.round(event.getRawY() - dragStartRawY);
                Rect bounds = availableBounds();
                clampPosition(panelParams, panel, bounds);
                try {
                    windowManager.updateViewLayout(panel, panelParams);
                } catch (IllegalArgumentException error) {
                    AppLog.warnRateLimited(
                            "overlay-position", "Cannot update overlay position", error);
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                prefs.putInt(Prefs.KEY_POSITION_X, panelParams.x + panel.outlineInset());
                prefs.putInt(Prefs.KEY_POSITION_Y, panelParams.y + panel.outlineInset());
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onAppClicked(AppEntry entry) {
        panelSuppression.suppress(SystemClock.elapsedRealtime(), 1_500L);
        dismissFuelDetails();
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.warn("Cannot launch selected activity " + entry.componentKey, error);
            Toast.makeText(this, getString(R.string.launch_failed, entry.label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFuelTileClicked() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        dismissFuelDetails();
        FuelDetailsView details = new FuelDetailsView(this, prefs, this::dismissFuelDetails);
        details.update(fuelLevelProvider.reading());
        Rect bounds = availableBounds();
        int width = Math.max(1, Math.min(
                Ui.dp(this, 600),
                Math.round(bounds.width() * 0.9f)
        ));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;
        try {
            windowManager.addView(details, params);
            fuelDetailsView = details;
            fuelDetailsAutoHide = () -> {
                if (fuelDetailsView == details) {
                    dismissFuelDetails();
                }
            };
            handler.postDelayed(fuelDetailsAutoHide, FUEL_DETAILS_AUTO_HIDE_DELAY_MS);
        } catch (SecurityException | WindowManager.BadTokenException error) {
            AppLog.warn("Cannot show fuel details overlay", error);
        }
    }

    private void dismissFuelDetails() {
        if (fuelDetailsAutoHide != null) {
            handler.removeCallbacks(fuelDetailsAutoHide);
            fuelDetailsAutoHide = null;
        }
        FuelDetailsView details = fuelDetailsView;
        if (details == null) {
            fuelDetailsView = null;
            return;
        }

        // Hide first so a WindowManager removal race cannot leave the detail content visible for
        // another frame. Clear the service reference before removing the window so metric updates
        // queued on the handler cannot mutate a view that is being dismissed.
        fuelDetailsView = null;
        details.setVisibility(View.GONE);
        if (windowManager == null || !details.isAttachedToWindow()) {
            return;
        }
        try {
            windowManager.removeViewImmediate(details);
        } catch (IllegalArgumentException | IllegalStateException error) {
            // The popup may already be detached, or the window manager may be in a removal
            // traversal while HOME is changing. It is already hidden, so do not reattach it.
            AppLog.warnRateLimited(
                    "fuel-details-dismiss", "Cannot remove fuel details overlay", error);
        }
    }

    private void syncFuelProvider() {
        if (fuelLevelProvider == null || !metricsActive) {
            return;
        }
        if (prefs.needsFuelData()) {
            fuelLevelProvider.start();
        } else {
            fuelLevelProvider.stop();
        }
    }

    private void clampPosition(WindowManager.LayoutParams params, PanelView target, Rect bounds) {
        params.x = Math.max(bounds.left,
                Math.min(params.x, Math.max(bounds.left, bounds.right - target.panelWidth())));
        params.y = Math.max(bounds.top,
                Math.min(params.y, Math.max(bounds.top, bounds.bottom - target.panelHeight())));
    }

    private Rect availableBounds() {
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Rect full = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
        );
        Rect safe = new Rect(
                full.left + insets.left,
                full.top + insets.top,
                full.right - insets.right,
                full.bottom - insets.bottom
        );
        return safe.width() > 0 && safe.height() > 0 ? safe : new Rect(full);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(int state) {
        String description;
        if (state == NOTIFICATION_VISIBLE) {
            description = getString(R.string.notification_visible);
        } else if (state == NOTIFICATION_PERMISSION_ERROR) {
            description = getString(R.string.notification_permission_error);
        } else if (state == NOTIFICATION_NO_APPS) {
            description = getString(R.string.notification_no_apps);
        } else {
            description = getString(R.string.notification_hidden);
        }
        PendingIntent openSettings = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent stop = PendingIntent.getService(
                this,
                1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(description)
                .setContentIntent(openSettings)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        null,
                        getString(R.string.stop),
                        stop
                ).build())
                .build();
    }

    private void updateNotification(int state) {
        if (state == currentNotificationState) {
            return;
        }
        currentNotificationState = state;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(state));
    }
}
