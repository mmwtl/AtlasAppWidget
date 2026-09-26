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

/** Keeps the GInputBridge receiver active while a fuel tile is placed on HOME. */
public final class WidgetFuelService extends Service {
    private static final String CHANNEL_ID = "atlas_widget_fuel";
    private static final int NOTIFICATION_ID = 2108;
    private static final long STATUS_REFRESH_MS = 30_000L;
    private static volatile WidgetFuelService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!needed(WidgetFuelService.this)) {
                stopSelf();
                return;
            }
            publishIfChanged();
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };
    private FuelLevelProvider fuel;
    private String lastDisplayedValue;

    static void sync(Context context) {
        Context app = context.getApplicationContext();
        Intent service = new Intent(app, WidgetFuelService.class);
        if (needed(app)) {
            if (instance == null) {
                try {
                    app.startForegroundService(service);
                } catch (RuntimeException error) {
                    AppLog.warn("Cannot start live widget fuel updates", error);
                }
            }
        } else {
            app.stopService(service);
        }
    }

    static FuelLevelProvider.Reading reading() {
        WidgetFuelService service = instance;
        return service == null ? FuelLevelProvider.Reading.unavailable() : service.fuel.reading();
    }

    private static boolean needed(Context context) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isWidgetMode()
                || !prefs.selectedComponents().contains(AppEntry.FUEL_COMPONENT_KEY)) {
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
        fuel = new FuelLevelProvider(this, new Prefs(this), this::publishIfChanged);
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!needed(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        fuel.start();
        lastDisplayedValue = null;
        publishIfChanged();
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, STATUS_REFRESH_MS);
        return START_STICKY;
    }

    private void publishIfChanged() {
        FuelLevelProvider.Reading reading = fuel.reading();
        String displayed = reading.filledDisplayValue() + "|" + reading.freeDisplayValue();
        if (!displayed.equals(lastDisplayedValue)) {
            lastDisplayedValue = displayed;
            AtlasAppWidgetProvider.refreshFuel(this);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refresh);
        if (fuel != null) {
            fuel.stop();
        }
        if (instance == this) {
            instance = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
