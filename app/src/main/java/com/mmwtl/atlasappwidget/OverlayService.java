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
    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService foregroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atlas-home-detector");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService systemStatusExecutor = Executors.newSingleThreadExecutor();
    private Prefs prefs;
    private WindowManager windowManager;
    private ForegroundAppDetector foregroundDetector;
    private FuelLevelProvider fuelLevelProvider;
    private SystemMetricsSampler systemMetricsSampler;
    private PanelView panel;
    private WindowManager.LayoutParams panelParams;
    private FuelDetailsView fuelDetailsView;
    private final Runnable applyPreferenceChanges = () -> {
        syncFuelProvider();
        if (panel != null) {
            hidePanel();
            showPanel();
        }
    };
    private int currentNotificationState;
    private long suppressPanelUntil;
    private long nextSystemStatusRefresh;
    private long createdAt;
    private long fastProbeUntil;
    private boolean metricsActive;
    private boolean foregroundQueryInFlight;
    private boolean visibilityCheckPending;
    private boolean deviceWasReady;
    private boolean visibilityReceiverRegistered;
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

    private final Runnable foregroundPoll = new Runnable() {
        @Override
        public void run() {
            if (destroyed) {
                return;
            }
            if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                stopSelf();
                return;
            }
            if (!Settings.canDrawOverlays(OverlayService.this)
                    || !ForegroundAppDetector.hasUsageAccess(OverlayService.this)) {
                hidePanel();
                updateNotification(NOTIFICATION_PERMISSION_ERROR);
                scheduleForegroundPoll(POLL_ERROR_MS);
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now < suppressPanelUntil) {
                hidePanel();
                updateNotification(NOTIFICATION_HIDDEN);
                scheduleForegroundPoll(ForegroundPollPolicy.VISIBLE_DELAY_MS);
                return;
            }
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
                visibilityCheckPending = true;
                return;
            }
            foregroundQueryInFlight = true;
            try {
                foregroundExecutor.execute(() -> {
                    boolean homeForeground = false;
                    try {
                        homeForeground = detector.isHomeForeground();
                    } catch (RuntimeException error) {
                        AppLog.warn("HOME visibility query failed", error);
                    }
                    boolean result = homeForeground;
                    handler.post(() -> applyForegroundResult(result));
                });
            } catch (RejectedExecutionException ignored) {
                foregroundQueryInFlight = false;
            }
        }
    };

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
        fastProbeUntil = createdAt + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        registerVisibilityWakeReceiver();
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
        hidePanel();
        foregroundExecutor.shutdownNow();
        systemStatusExecutor.shutdownNow();
        if (fuelLevelProvider != null) {
            fuelLevelProvider.stop();
        }
        if (prefs != null) {
            prefs.raw().unregisterOnSharedPreferenceChangeListener(this);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        running = false;
        AppLog.info("Overlay service destroyed");
        super.onDestroy();
    }

    private void applyForegroundResult(boolean homeForeground) {
        if (destroyed) {
            return;
        }
        foregroundQueryInFlight = false;
        if (visibilityCheckPending) {
            visibilityCheckPending = false;
            handler.post(foregroundPoll);
            return;
        }
        if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            stopSelf();
            return;
        }
        boolean deviceReady = foregroundDetector().isDeviceReady();
        boolean panelAllowed = SystemClock.elapsedRealtime() >= suppressPanelUntil;
        boolean hasApps = false;
        if (homeForeground && deviceReady && panelAllowed) {
            hasApps = showPanel();
            if (hasApps) {
                if (!metricsActive) {
                    metricsActive = true;
                    syncFuelProvider();
                }
                refreshSystemStatusIfNeeded();
            }
            updateNotification(hasApps ? NOTIFICATION_VISIBLE : NOTIFICATION_NO_APPS);
        } else {
            hidePanel();
            updateNotification(NOTIFICATION_HIDDEN);
        }
        long now = SystemClock.elapsedRealtime();
        long nextDelay = homeForeground && deviceReady && panelAllowed && !hasApps
                ? POLL_ERROR_MS
                : ForegroundPollPolicy.nextDelay(
                        homeForeground && panelAllowed, deviceReady, now, fastProbeUntil);
        scheduleForegroundPoll(nextDelay);
    }

    private void requestImmediateVisibilityCheck() {
        fastProbeUntil = SystemClock.elapsedRealtime()
                + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        handler.removeCallbacks(foregroundPoll);
        if (foregroundQueryInFlight) {
            visibilityCheckPending = true;
        } else {
            handler.post(foregroundPoll);
        }
    }

    private void scheduleForegroundPoll(long delayMs) {
        if (destroyed) {
            return;
        }
        handler.removeCallbacks(foregroundPoll);
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean isRunning() {
        return running;
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
            if (panel.isAttachedToWindow()) {
                return true;
            }
            // Some head-unit shells can detach an overlay while switching tasks
            // without going through our removal path. Do not keep a stale view
            // reference, otherwise the panel never returns when HOME is resumed.
            panel = null;
            panelParams = null;
        }
        Rect bounds = availableBounds();
        List<AppEntry> entries = AppRepository.loadSelectedActivities(this, prefs);
        if (entries.isEmpty()) {
            hidePanel();
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
                this
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
            nextSystemStatusRefresh = 0;
            AppLog.info("Overlay panel attached t+"
                    + (SystemClock.elapsedRealtime() - createdAt)
                    + " ms after service creation");
            return true;
        } catch (SecurityException | WindowManager.BadTokenException error) {
            panel = null;
            panelParams = null;
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

    private void hidePanel() {
        dismissFuelDetails();
        if (panel == null || windowManager == null) {
            panel = null;
            panelParams = null;
            return;
        }
        try {
            windowManager.removeViewImmediate(panel);
        } catch (IllegalArgumentException ignored) {
            // The system may already have removed the overlay after permission revocation.
        }
        if (systemMetricsSampler != null) {
            try {
                systemStatusExecutor.execute(systemMetricsSampler::resetCpuBaseline);
            } catch (RejectedExecutionException ignored) {
                // Service teardown already stopped the sampler.
            }
        }
        nextSystemStatusRefresh = 0;
        panel = null;
        panelParams = null;
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
        suppressPanelUntil = SystemClock.elapsedRealtime() + 1_500L;
        hidePanel();
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
        } catch (SecurityException | WindowManager.BadTokenException error) {
            AppLog.warn("Cannot show fuel details overlay", error);
        }
    }

    private void dismissFuelDetails() {
        if (fuelDetailsView == null || windowManager == null) {
            fuelDetailsView = null;
            return;
        }
        try {
            windowManager.removeViewImmediate(fuelDetailsView);
        } catch (IllegalArgumentException ignored) {
            // The popup may already be detached while HOME is changing.
        }
        fuelDetailsView = null;
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
