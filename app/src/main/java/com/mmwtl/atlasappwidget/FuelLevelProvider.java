package com.mmwtl.atlasappwidget;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Receives the fuel level exposed by GInputBridge and converts it to tank liters. */
final class FuelLevelProvider {
    static final String BRIDGE_PACKAGE = "com.salat.gbinder";
    static final String BRIDGE_RECEIVER_CLASS =
            "com.salat.gbinder.BackgroundTaskReceiver";
    static final String ACTION_GET_FLOAT_SENSOR =
            "com.salat.gbinder.GET_FLOAT_SENSOR";
    static final String ACTION_LISTEN_SENSOR_CHANGES =
            "com.salat.gbinder.LISTEN_SENSOR_CHANGES";
    static final String ACTION_SENSOR_FLOAT_RESULT =
            "com.salat.gbinder.SENSOR_FLOAT_RESULT";
    static final String ACTION_SENSOR_FLOAT_CHANGED =
            "com.salat.gbinder.SENSOR_FLOAT_CHANGED";
    static final String EXTRA_ID = "id";
    static final String EXTRA_VALUE = "value";
    static final int SENSOR_TYPE_FUEL_LEVEL = 1_050_112;
    static final int TANK_CAPACITY_LITERS = 54;
    static final float DEFAULT_MULTIPLIER = 1f;
    static final float DEFAULT_OFFSET = 4f;
    static final long SENSOR_REFRESH_INTERVAL_MS = 30_000L;
    static final long SENSOR_LISTEN_REFRESH_INTERVAL_MS = 120_000L;
    static final long READING_STALE_AFTER_MS = 120_000L;
    private static final long[] SENSOR_STARTUP_RETRY_INTERVALS_MS = {
            2_000L,
            5_000L,
            10_000L
    };

    private final Context context;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile float currentSensorValue = Float.NaN;
    private volatile long currentReceivedAtMillis;
    private volatile long currentReceivedAtElapsedRealtime;
    private int lastLoggedLiters = SystemStatusSnapshot.UNAVAILABLE;
    private long lastListenRequestElapsedRealtime;
    private int startupRetryIndex;
    private volatile boolean registered;

    private final Runnable startupRetry = new Runnable() {
        @Override
        public void run() {
            if (!registered
                    || isReadingFresh(
                    currentReceivedAtElapsedRealtime,
                    SystemClock.elapsedRealtime())) {
                return;
            }
            sendSensorCommand(ACTION_GET_FLOAT_SENSOR);
            sendSensorCommand(ACTION_LISTEN_SENSOR_CHANGES);
            startupRetryIndex++;
            if (startupRetryIndex < SENSOR_STARTUP_RETRY_INTERVALS_MS.length) {
                handler.postDelayed(
                        this,
                        SENSOR_STARTUP_RETRY_INTERVALS_MS[startupRetryIndex]
                );
            }
        }
    };

    private final Runnable sensorRefresh = new Runnable() {
        @Override
        public void run() {
            if (!registered) {
                return;
            }
            sendSensorCommand(ACTION_GET_FLOAT_SENSOR);
            long now = SystemClock.elapsedRealtime();
            if (lastListenRequestElapsedRealtime <= 0L
                    || now - lastListenRequestElapsedRealtime
                    >= SENSOR_LISTEN_REFRESH_INTERVAL_MS) {
                sendSensorCommand(ACTION_LISTEN_SENSOR_CHANGES);
                lastListenRequestElapsedRealtime = now;
            }
            handler.postDelayed(this, SENSOR_REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (!ACTION_SENSOR_FLOAT_RESULT.equals(action)
                    && !ACTION_SENSOR_FLOAT_CHANGED.equals(action)) {
                return;
            }
            Float parsed = parseSensorValue(
                    extraValue(intent, EXTRA_ID),
                    extraValue(intent, EXTRA_VALUE)
            );
            if (parsed != null) {
                handler.removeCallbacks(startupRetry);
                currentReceivedAtMillis = System.currentTimeMillis();
                currentReceivedAtElapsedRealtime = SystemClock.elapsedRealtime();
                currentSensorValue = parsed;
                Reading reading = reading();
                if (reading != null && reading.liters != lastLoggedLiters) {
                    lastLoggedLiters = reading.liters;
                    AppLog.info("Fuel level received: " + reading.liters + " L");
                }
            }
        }
    };

    FuelLevelProvider(Context context, Prefs prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @SuppressWarnings("deprecation")
    void start() {
        if (registered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SENSOR_FLOAT_RESULT);
        filter.addAction(ACTION_SENSOR_FLOAT_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            registered = true;
            AppLog.info("Fuel sensor listener registered");
        } catch (RuntimeException error) {
            AppLog.warn("Cannot register GInputBridge fuel listener", error);
            return;
        }
        sendSensorCommand(ACTION_GET_FLOAT_SENSOR);
        sendSensorCommand(ACTION_LISTEN_SENSOR_CHANGES);
        lastListenRequestElapsedRealtime = SystemClock.elapsedRealtime();
        handler.removeCallbacks(sensorRefresh);
        handler.removeCallbacks(startupRetry);
        startupRetryIndex = 0;
        handler.postDelayed(
                startupRetry,
                SENSOR_STARTUP_RETRY_INTERVALS_MS[startupRetryIndex]
        );
        handler.postDelayed(sensorRefresh, SENSOR_REFRESH_INTERVAL_MS);
        AppLog.info("Fuel sensor commands sent to explicit GInputBridge receiver");
    }

    void stop() {
        boolean wasRegistered = registered;
        registered = false;
        handler.removeCallbacks(sensorRefresh);
        handler.removeCallbacks(startupRetry);
        lastListenRequestElapsedRealtime = 0L;
        startupRetryIndex = 0;
        clearReading();
        if (!wasRegistered) {
            return;
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Android may already have detached the receiver during process teardown.
        }
    }

    Reading reading() {
        return readingAt(SystemClock.elapsedRealtime());
    }

    private Reading readingAt(long nowElapsedRealtime) {
        float sensorValue = currentSensorValue;
        long receivedAtElapsedRealtime = currentReceivedAtElapsedRealtime;
        if (!Float.isFinite(sensorValue)
                || !isReadingFresh(receivedAtElapsedRealtime, nowElapsedRealtime)) {
            return Reading.unavailable();
        }
        return fromSensorValue(
                sensorValue,
                prefs.getFloat(Prefs.KEY_FUEL_MULTIPLIER, DEFAULT_MULTIPLIER),
                prefs.getFloat(Prefs.KEY_FUEL_OFFSET, DEFAULT_OFFSET),
                currentReceivedAtMillis
        );
    }

    private void clearReading() {
        currentSensorValue = Float.NaN;
        currentReceivedAtMillis = 0L;
        currentReceivedAtElapsedRealtime = 0L;
        lastLoggedLiters = SystemStatusSnapshot.UNAVAILABLE;
    }

    private void sendSensorCommand(String action) {
        Intent request = new Intent(action)
                .setComponent(new ComponentName(BRIDGE_PACKAGE, BRIDGE_RECEIVER_CLASS))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(EXTRA_ID, SENSOR_TYPE_FUEL_LEVEL);
        try {
            context.sendBroadcast(request);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot request GInputBridge fuel sensor", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static Object extraValue(Intent intent, String name) {
        try {
            Bundle extras = intent.getExtras();
            // GInputBridge documents strings, but older builds may use primitive extras.
            return extras == null ? null : extras.get(name);
        } catch (RuntimeException error) {
            return null;
        }
    }

    static boolean isReadingFresh(long receivedAtElapsedRealtime, long nowElapsedRealtime) {
        return receivedAtElapsedRealtime > 0L
                && nowElapsedRealtime >= receivedAtElapsedRealtime
                && nowElapsedRealtime - receivedAtElapsedRealtime <= READING_STALE_AFTER_MS;
    }

    static Float parseSensorValue(Object rawId, Object rawValue) {
        if (rawId == null || rawValue == null) {
            return null;
        }
        try {
            if (Integer.parseInt(rawId.toString().trim()) != SENSOR_TYPE_FUEL_LEVEL) {
                return null;
            }
            float value = Float.parseFloat(rawValue.toString().trim());
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static Reading fromSensorValue(float sensorValue) {
        return fromSensorValue(sensorValue, DEFAULT_MULTIPLIER, DEFAULT_OFFSET, 0L);
    }

    static Reading fromSensorValue(float sensorValue, float multiplier, float offset) {
        return fromSensorValue(sensorValue, multiplier, offset, 0L);
    }

    private static Reading fromSensorValue(
            float sensorValue,
            float multiplier,
            float offset,
            long receivedAtMillis
    ) {
        if (!Float.isFinite(sensorValue)
                || !Float.isFinite(multiplier)
                || !Float.isFinite(offset)) {
            return null;
        }
        float calculatedLiters = sensorValue * multiplier + offset;
        if (!Float.isFinite(calculatedLiters)) {
            return null;
        }
        float boundedLiters = Math.max(0f,
                Math.min(TANK_CAPACITY_LITERS, calculatedLiters));
        int liters = Math.round(boundedLiters);
        int percent = Math.round(boundedLiters * 100f / TANK_CAPACITY_LITERS);
        return new Reading(
                sensorValue,
                multiplier,
                offset,
                calculatedLiters,
                Math.max(0, Math.min(TANK_CAPACITY_LITERS, liters)),
                Math.max(0, Math.min(100, percent)),
                receivedAtMillis
        );
    }

    static final class Reading {
        final float sensorValue;
        final float multiplier;
        final float offset;
        final float calculatedLiters;
        final int liters;
        final int freeLiters;
        final int percent;
        final long receivedAtMillis;

        Reading(
                float sensorValue,
                float multiplier,
                float offset,
                float calculatedLiters,
                int liters,
                int percent,
                long receivedAtMillis
        ) {
            this.sensorValue = sensorValue;
            this.multiplier = multiplier;
            this.offset = offset;
            this.calculatedLiters = calculatedLiters;
            this.liters = liters;
            this.freeLiters = liters == SystemStatusSnapshot.UNAVAILABLE
                    ? SystemStatusSnapshot.UNAVAILABLE
                    : TANK_CAPACITY_LITERS - liters;
            this.percent = percent;
            this.receivedAtMillis = receivedAtMillis;
        }

        boolean isAvailable() {
            return liters != SystemStatusSnapshot.UNAVAILABLE;
        }

        static Reading unavailable() {
            return new Reading(
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    SystemStatusSnapshot.UNAVAILABLE,
                    SystemStatusSnapshot.UNAVAILABLE,
                    0L
            );
        }
    }
}
