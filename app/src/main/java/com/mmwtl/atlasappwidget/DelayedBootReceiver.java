package com.mmwtl.atlasappwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class DelayedBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !BootReceiver.ACTION_DELAYED_BOOT_START.equals(intent.getAction())) {
            return;
        }
        Prefs prefs = new Prefs(context);
        prefs.remove(Prefs.KEY_AUTO_START_PENDING_UNTIL_MS);
        boolean shouldStart = prefs.getBoolean(Prefs.KEY_AUTO_START, false)
                && prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        if (!shouldStart
                || !Settings.canDrawOverlays(context)
                || !ForegroundAppDetector.hasUsageAccess(context)) {
            return;
        }
        try {
            OverlayService.start(context);
        } catch (RuntimeException error) {
            AppLog.warn("Delayed boot start failed", error);
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        }
    }
}
