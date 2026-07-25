package com.mmwtl.atlasappwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
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
    static final String ACTION_START_AFTER_BOOT =
            "com.mmwtl.atlasappwidget.action.START_AFTER_BOOT";
    static final String ACTION_STOP = "com.mmwtl.atlasappwidget.action.STOP";

    private static final String EXTRA_BOOT_DELAY_MS = "boot_delay_ms";
    private static final String CHANNEL_ID = "atlas_app_widget_service";
    private static final int NOTIFICATION_ID = 2107;
    private static final int POLL_VISIBLE_MS = 700;
    private static final int POLL_HIDDEN_MS = 1_000;
    private static final int POLL_ERROR_MS = 2_000;
    private static final int SYSTEM_STATUS_REFRESH_MS = 2_000;
    private static final int NOTIFICATION_VISIBLE = 1;
    private static final int NOTIFICATION_HIDDEN = 2;
    private static final int NOTIFICATION_PERMISSION_ERROR = 3;
    private static final int NOTIFICATION_BOOT_DELAY = 4;
    private static final int NOTIFICATION_NO_APPS = 5;
    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService systemStatusExecutor = Executors.newSingleThreadExecutor();
    private Prefs prefs;
    private WindowManager windowManager;
    private ForegroundAppDetector foregroundDetector;
    private FuelLevelProvider fuelLevelProvider;
    private SystemMetricsSampler systemMetricsSampler;
    private PanelView panel;
    private WindowManager.LayoutParams panelParams;
    private int currentNotificationState;
    private long suppressPanelUntil;
    private long nextSystemStatusRefresh;

    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartWindowX;
    private int dragStartWindowY;

    private final Runnable foregroundPoll = new Runnable() {
        @Override
        public void run() {
            prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
            if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                stopSelf();
                return;
            }
            int nextPollDelay;
            if (!Settings.canDrawOverlays(OverlayService.this)
                    || !ForegroundAppDetector.hasUsageAccess(OverlayService.this)) {
                hidePanel();
                updateNotification(NOTIFICATION_PERMISSION_ERROR);
                nextPollDelay = POLL_ERROR_MS;
            } else if (SystemClock.elapsedRealtime() < suppressPanelUntil) {
                hidePanel();
                updateNotification(NOTIFICATION_HIDDEN);
                nextPollDelay = POLL_VISIBLE_MS;
            } else if (foregroundDetector().isHomeForeground()) {
                boolean hasApps = showPanel();
                if (hasApps) {
                    refreshSystemStatusIfNeeded();
                }
                updateNotification(hasApps ? NOTIFICATION_VISIBLE : NOTIFICATION_NO_APPS);
                nextPollDelay = hasApps ? POLL_VISIBLE_MS : POLL_ERROR_MS;
            } else {
                hidePanel();
                updateNotification(NOTIFICATION_HIDDEN);
                nextPollDelay = POLL_HIDDEN_MS;
            }
            handler.postDelayed(this, nextPollDelay);
        }
    };

    static void start(android.content.Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    static void startAfterBoot(android.content.Context context, int delaySeconds) {
        long delayMs = Math.max(0,
                Math.min(Prefs.MAX_AUTO_START_DELAY_SECONDS, delaySeconds)) * 1_000L;
        Intent intent = new Intent(context, OverlayService.class)
                .setAction(ACTION_START_AFTER_BOOT)
                .putExtra(EXTRA_BOOT_DELAY_MS, delayMs);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        fuelLevelProvider = new FuelLevelProvider(this);
        if (prefs.getBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false)) {
            fuelLevelProvider.start();
        }
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
        AppLog.info("Overlay service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
            stopSelf();
            return START_NOT_STICKY;
        }
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        handler.removeCallbacks(foregroundPoll);
        long delayMs = 0;
        if (ACTION_START_AFTER_BOOT.equals(action)) {
            delayMs = Math.max(0, intent.getLongExtra(EXTRA_BOOT_DELAY_MS, 0));
            if (delayMs > 0) {
                prefs.putLong(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS,
                        System.currentTimeMillis() + delayMs);
            }
        } else if (intent == null) {
            delayMs = Math.max(0,
                    prefs.getLong(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS, 0)
                            - System.currentTimeMillis());
        } else {
            prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
        }
        if (delayMs > 0) {
            hidePanel();
            updateNotification(NOTIFICATION_BOOT_DELAY);
            handler.postDelayed(foregroundPoll, delayMs);
        } else {
            prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
            handler.post(foregroundPoll);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hidePanel();
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean isRunning() {
        return running;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Prefs.KEY_SHOW_SYSTEM_STATUS.equals(key) && fuelLevelProvider != null) {
            if (sharedPreferences.getBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false)) {
                fuelLevelProvider.start();
            } else {
                fuelLevelProvider.stop();
            }
        }
        if (Prefs.KEY_POSITION_X.equals(key) || Prefs.KEY_POSITION_Y.equals(key)
                || Prefs.KEY_SERVICE_ENABLED.equals(key)
                || Prefs.KEY_AUTO_START.equals(key)
                || Prefs.KEY_AUTO_START_DELAY_SECONDS.equals(key)
                || Prefs.KEY_AUTO_START_PENDING_UNTIL_MS.equals(key)) {
            return;
        }
        handler.post(() -> {
            if (panel != null) {
                hidePanel();
                showPanel();
            }
        });
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
        if (panel == null || !panel.hasSystemStatus()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < nextSystemStatusRefresh) {
            return;
        }
        nextSystemStatusRefresh = now + SYSTEM_STATUS_REFRESH_MS;
        PanelView target = panel;
        try {
            systemStatusExecutor.execute(() -> {
                SystemStatusSnapshot snapshot = systemMetricsSampler.sample();
                handler.post(() -> {
                    if (running && panel == target && target.isAttachedToWindow()) {
                        target.updateSystemStatus(snapshot);
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
        } else if (state == NOTIFICATION_BOOT_DELAY) {
            description = getString(R.string.notification_boot_delay);
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
