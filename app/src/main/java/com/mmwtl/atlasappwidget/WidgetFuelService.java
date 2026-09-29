package com.mmwtl.atlasappwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps HOME widgets live: the GInputBridge fuel receiver for fuel tiles and the status strip,
 * and CPU/RAM sampling for the status strip.
 */
public final class WidgetFuelService extends Service {
    private static final String CHANNEL_ID = "atlas_widget_fuel";
    private static final int NOTIFICATION_ID = 2108;
    private static final long FUEL_REFRESH_MS = 30_000L;
    // Matches the overlay status cadence.
    private static final long METRICS_REFRESH_MS = 2_000L;
    private static volatile WidgetFuelService instance;
    private static volatile SystemStatusSnapshot statusSnapshot =
            SystemStatusSnapshot.unavailable();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService sampling = Executors.newSingleThreadExecutor();
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!needed(WidgetFuelService.this)) {
                stopSelf();
                return;
            }
            publishIfChanged();
            sampleMetrics();
            handler.postDelayed(this, sampleCpu || sampleRam
                    ? METRICS_REFRESH_MS : FUEL_REFRESH_MS);
        }
    };
    private FuelLevelProvider fuel;
    private SystemMetricsSampler sampler;
    private PowerManager power;
    private String lastDisplayedValue;
    private boolean sampleCpu;
    private boolean sampleRam;

    static void sync(Context context) {
        Context app = context.getApplicationContext();
        Intent service = new Intent(app, WidgetFuelService.class);
        if (needed(app)) {
            WidgetFuelService running = instance;
            if (running != null) {
                running.handler.post(running::configure);
                return;
            }
            try {
                app.startForegroundService(service);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot start live widget updates", error);
            }
        } else {
            app.stopService(service);
        }
    }

    static FuelLevelProvider.Reading reading() {
        WidgetFuelService service = instance;
        return service == null ? FuelLevelProvider.Reading.unavailable() : service.fuel.reading();
    }

    static SystemStatusSnapshot statusSnapshot() {
        return instance == null ? SystemStatusSnapshot.unavailable() : statusSnapshot;
    }

    private static boolean needed(Context context) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isWidgetMode()) {
            return false;
        }
        if (!prefs.selectedComponents().contains(AppEntry.FUEL_COMPONENT_KEY)
                && !prefs.panelConfig().showSystemStatus) {
            return false;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        return manager.getAppWidgetIds(
                new ComponentName(context, AtlasAppWidgetProvider.class)).length > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.widget_fuel_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.widget_fuel_notification_title))
                .setContentText(getString(R.string.widget_fuel_notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        Prefs prefs = new Prefs(this);
        fuel = new FuelLevelProvider(this, prefs, this::publishIfChanged);
        sampler = new SystemMetricsSampler(this, fuel);
        power = getSystemService(PowerManager.class);
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!needed(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        configure();
        return START_STICKY;
    }

    /** Applies the current preferences and restarts the refresh cycle immediately. */
    private void configure() {
        if (instance != this) {
            return;
        }
        Prefs prefs = new Prefs(this);
        PanelConfig config = prefs.panelConfig();
        sampleCpu = config.showSystemStatus && config.showCpuStatus;
        sampleRam = config.showSystemStatus && config.showRamStatus;
        if (prefs.selectedComponents().contains(AppEntry.FUEL_COMPONENT_KEY)
                || config.showSystemStatus && config.showFuelStatus) {
            fuel.start();
        } else {
            fuel.stop();
        }
        lastDisplayedValue = null;
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    private void publishIfChanged() {
        FuelLevelProvider.Reading reading = fuel.reading();
        String displayed = reading.filledDisplayValue() + "|" + reading.freeDisplayValue();
        if (!displayed.equals(lastDisplayedValue)) {
            lastDisplayedValue = displayed;
            AtlasAppWidgetProvider.refreshFuel(this);
        }
    }

    private void sampleMetrics() {
        boolean cpu = sampleCpu;
        boolean ram = sampleRam;
        if (!cpu && !ram) {
            return;
        }
        // Launchers are not drawn with the screen off; a stale CPU baseline would average it in.
        if (power != null && !power.isInteractive()) {
            sampling.execute(sampler::resetCpuBaseline);
            return;
        }
        sampling.execute(() -> {
            SystemStatusSnapshot next = sampler.sample(cpu, ram, false);
            if (!next.equals(statusSnapshot)) {
                statusSnapshot = next;
                AtlasAppWidgetProvider.refreshStatus(this);
            }
        });
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refresh);
        sampling.shutdownNow();
        if (fuel != null) {
            fuel.stop();
        }
        if (instance == this) {
            instance = null;
        }
        statusSnapshot = SystemStatusSnapshot.unavailable();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
