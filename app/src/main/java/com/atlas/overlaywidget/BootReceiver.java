package com.atlas.overlaywidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
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
                OverlayService.start(context);
            } catch (RuntimeException ignored) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            }
        }
    }
}
