package com.mmwtl.atlasappwidget;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/** Receives the fuel level exposed by GInputBridge and converts it to tank liters. */
final class FuelLevelProvider {
    static final String BRIDGE_PACKAGE = "com.salat.gbinder";
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
    static final int SENSOR_RANGE_LITERS = 50;
    static final int RESERVE_OFFSET_LITERS =
            TANK_CAPACITY_LITERS - SENSOR_RANGE_LITERS;

    private final Context context;
    private volatile Reading current = Reading.unavailable();
    private boolean registered;

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
            Reading parsed = parseResponse(
                    intent.getStringExtra(EXTRA_ID),
                    intent.getStringExtra(EXTRA_VALUE)
            );
            if (parsed != null) {
                boolean changed = current.liters != parsed.liters;
                current = parsed;
                if (changed) {
                    AppLog.info("Fuel level received: " + parsed.liters + " L");
                }
            }
        }
    };

    FuelLevelProvider(Context context) {
        this.context = context.getApplicationContext();
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
    }

    void stop() {
        if (!registered) {
            return;
        }
        registered = false;
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Android may already have detached the receiver during process teardown.
        }
    }

    Reading reading() {
        return current;
    }

    private void sendSensorCommand(String action) {
        Intent request = new Intent(action)
                .setPackage(BRIDGE_PACKAGE)
                .putExtra(EXTRA_ID, SENSOR_TYPE_FUEL_LEVEL);
        try {
            context.sendBroadcast(request);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot request GInputBridge fuel sensor", error);
        }
    }

    static Reading parseResponse(String rawId, String rawValue) {
        if (rawId == null || rawValue == null) {
            return null;
        }
        try {
            if (Integer.parseInt(rawId.trim()) != SENSOR_TYPE_FUEL_LEVEL) {
                return null;
            }
            return fromSensorValue(Float.parseFloat(rawValue.trim()));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static Reading fromSensorValue(float sensorLiters) {
        if (!Float.isFinite(sensorLiters) || sensorLiters < 0f) {
            return null;
        }
        float boundedSensorLiters = Math.min(SENSOR_RANGE_LITERS, sensorLiters);
        int liters = Math.round(boundedSensorLiters + RESERVE_OFFSET_LITERS);
        int percent = Math.round(liters * 100f / TANK_CAPACITY_LITERS);
        return new Reading(
                Math.max(RESERVE_OFFSET_LITERS,
                        Math.min(TANK_CAPACITY_LITERS, liters)),
                Math.max(0, Math.min(100, percent))
        );
    }

    static final class Reading {
        final int liters;
        final int percent;

        Reading(int liters, int percent) {
            this.liters = liters;
            this.percent = percent;
        }

        static Reading unavailable() {
            return new Reading(
                    SystemStatusSnapshot.UNAVAILABLE,
                    SystemStatusSnapshot.UNAVAILABLE
            );
        }
    }
}
