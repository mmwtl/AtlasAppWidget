package mmwtl.atlasappwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
    static final String ACTION_DELAYED_BOOT_START =
            "mmwtl.atlasappwidget.action.DELAYED_BOOT_START";
    private static final int DELAYED_START_REQUEST_CODE = 2108;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        Prefs prefs = new Prefs(context);
        String action = intent.getAction();
        boolean shouldStart;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            shouldStart = prefs.getBoolean(Prefs.KEY_AUTO_START, false);
            if (!shouldStart) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
            }
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            shouldStart = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)
                    || prefs.getBoolean(Prefs.KEY_AUTO_START, false);
        } else {
            return;
        }

        if (shouldStart
                && Settings.canDrawOverlays(context)
                && ForegroundAppDetector.hasUsageAccess(context)) {
            try {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
                if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    int delaySeconds = prefs.getInt(Prefs.KEY_AUTO_START_DELAY_SECONDS, 0);
                    if (delaySeconds > 0 && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                        scheduleDelayedStart(context, prefs, delaySeconds);
                    } else {
                        OverlayService.startAfterBoot(context, delaySeconds);
                    }
                } else {
                    cancelDelayedStart(context);
                    prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
                    OverlayService.start(context);
                }
            } catch (RuntimeException ignored) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
            }
        }
    }

    private void scheduleDelayedStart(Context context, Prefs prefs, int delaySeconds) {
        int boundedDelaySeconds = Math.max(0,
                Math.min(Prefs.MAX_AUTO_START_DELAY_SECONDS, delaySeconds));
        long delayMs = boundedDelaySeconds * 1_000L;
        PendingIntent pendingStart = delayedStartPendingIntent(
                context,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            throw new IllegalStateException("AlarmManager is unavailable");
        }
        prefs.putLong(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS,
                System.currentTimeMillis() + delayMs);
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                pendingStart
        );
    }

    static void cancelDelayedStart(Context context) {
        PendingIntent pendingStart = delayedStartPendingIntent(
                context,
                PendingIntent.FLAG_NO_CREATE
        );
        if (pendingStart == null) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingStart);
        }
        pendingStart.cancel();
    }

    private static PendingIntent delayedStartPendingIntent(Context context, int flags) {
        Intent delayedStart = new Intent(context, DelayedBootReceiver.class)
                .setAction(ACTION_DELAYED_BOOT_START);
        return PendingIntent.getBroadcast(
                context,
                DELAYED_START_REQUEST_CODE,
                delayedStart,
                flags | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
