package mmwtl.atlasappwidget;

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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.List;

public final class OverlayService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, PanelView.Listener {
    static final String ACTION_START = "mmwtl.atlasappwidget.action.START";
    static final String ACTION_STOP = "mmwtl.atlasappwidget.action.STOP";

    private static final String CHANNEL_ID = "atlas_app_widget_service";
    private static final int NOTIFICATION_ID = 2107;
    private static final int POLL_INTERVAL_MS = 450;
    private static final int NOTIFICATION_VISIBLE = 1;
    private static final int NOTIFICATION_HIDDEN = 2;
    private static final int NOTIFICATION_PERMISSION_ERROR = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private WindowManager windowManager;
    private ForegroundAppDetector foregroundDetector;
    private PanelView panel;
    private WindowManager.LayoutParams panelParams;
    private int currentNotificationState;
    private long suppressPanelUntil;

    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartWindowX;
    private int dragStartWindowY;

    private final Runnable foregroundPoll = new Runnable() {
        @Override
        public void run() {
            if (!prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                stopSelf();
                return;
            }
            if (!Settings.canDrawOverlays(OverlayService.this)
                    || !ForegroundAppDetector.hasUsageAccess(OverlayService.this)) {
                hidePanel();
                updateNotification(NOTIFICATION_PERMISSION_ERROR);
            } else if (System.currentTimeMillis() < suppressPanelUntil) {
                hidePanel();
                updateNotification(NOTIFICATION_HIDDEN);
            } else if (foregroundDetector.isHomeForeground()) {
                showPanel();
                updateNotification(NOTIFICATION_VISIBLE);
            } else {
                hidePanel();
                updateNotification(NOTIFICATION_HIDDEN);
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    static void start(android.content.Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        foregroundDetector = new ForegroundAppDetector(this);
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
        handler.removeCallbacks(foregroundPoll);
        handler.post(foregroundPoll);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hidePanel();
        if (prefs != null) {
            prefs.raw().unregisterOnSharedPreferenceChangeListener(this);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Prefs.KEY_POSITION_X.equals(key) || Prefs.KEY_POSITION_Y.equals(key)
                || Prefs.KEY_SERVICE_ENABLED.equals(key)) {
            return;
        }
        handler.post(() -> {
            if (panel != null) {
                hidePanel();
                showPanel();
            }
        });
    }

    private void showPanel() {
        if (windowManager == null) {
            return;
        }
        if (panel != null) {
            if (panel.isAttachedToWindow()) {
                return;
            }
            // Some head-unit shells can detach an overlay while switching tasks
            // without going through our removal path. Do not keep a stale view
            // reference, otherwise the panel never returns when HOME is resumed.
            panel = null;
            panelParams = null;
        }
        Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
        List<AppEntry> entries = AppRepository.loadSelectedActivities(this, prefs);
        PanelView candidate = new PanelView(
                this,
                prefs,
                prefs.panelConfig(),
                entries,
                false,
                bounds.width(),
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
                ? Math.max(0, (bounds.width() - candidate.panelWidth()) / 2)
                : storedX - candidate.outlineInset();
        params.y = storedY == Prefs.POSITION_UNSET
                ? Math.max(0, Math.round((bounds.height() - candidate.panelHeight()) * 0.72f))
                : storedY - candidate.outlineInset();
        clampPosition(params, candidate, bounds);

        try {
            windowManager.addView(candidate, params);
            panel = candidate;
            panelParams = params;
        } catch (SecurityException | WindowManager.BadTokenException ignored) {
            panel = null;
            panelParams = null;
            updateNotification(NOTIFICATION_PERMISSION_ERROR);
        }
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
        panel = null;
        panelParams = null;
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
                Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
                clampPosition(panelParams, panel, bounds);
                try {
                    windowManager.updateViewLayout(panel, panelParams);
                } catch (IllegalArgumentException ignored) {
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
        suppressPanelUntil = System.currentTimeMillis() + 1_500L;
        hidePanel();
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "Не удалось открыть " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void clampPosition(WindowManager.LayoutParams params, PanelView target, Rect bounds) {
        params.x = Math.max(0, Math.min(params.x, Math.max(0, bounds.width() - target.panelWidth())));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, bounds.height() - target.panelHeight())));
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
