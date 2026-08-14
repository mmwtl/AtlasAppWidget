package com.mmwtl.atlasappwidget;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForegroundAppDetector {
    private static final long FAST_ACCESSIBILITY_SNAPSHOT_MAX_AGE_MS = 2_000L;

    private final Context context;
    private final UsageStatsManager usageStatsManager;
    private final PowerManager powerManager;
    private final KeyguardManager keyguardManager;
    private final UserManager userManager;
    private final Set<String> homePackages = new HashSet<>();
    private final Set<String> homeComponents = new HashSet<>();
    private final ForegroundEventTracker eventTracker = new ForegroundEventTracker();
    private long lastHomeRefreshTime;
    private long lastQueryTime;

    ForegroundAppDetector(Context context) {
        this.context = context.getApplicationContext();
        usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    boolean isHomeVisible() {
        if (!isDeviceReady()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (homePackages.isEmpty() || now - lastHomeRefreshTime > 30_000L) {
            refreshHomePackages();
        }
        boolean usageStateAvailable = refreshActivityVisibility();
        AccessibilityWindowState.Snapshot windowState = AccessibilityWindowState.current();
        if (windowState.available) {
            WindowVisibilityPolicy.Decision decision = evaluateAccessibilitySnapshot(windowState);
            if (decision != WindowVisibilityPolicy.Decision.UNKNOWN) {
                return decision == WindowVisibilityPolicy.Decision.HOME_VISIBLE;
            }
        }
        return usageStateAvailable && eventTracker.isAnyPackageVisible(homePackages);
    }

    /**
     * Resolves HOME from the most recent accessibility snapshot without touching UsageStats.
     * A null result means that the snapshot is unavailable, stale, or ambiguous and the caller
     * must use the normal UsageStats-backed path.
     */
    Boolean isHomeVisibleFromAccessibility() {
        if (!isDeviceReady()) {
            return Boolean.FALSE;
        }
        AccessibilityWindowState.Snapshot windowState = AccessibilityWindowState.current();
        long snapshotAge = SystemClock.elapsedRealtime() - windowState.updatedAtElapsedRealtime;
        if (!windowState.available
                || windowState.updatedAtElapsedRealtime <= 0L
                || snapshotAge > FAST_ACCESSIBILITY_SNAPSHOT_MAX_AGE_MS) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (homePackages.isEmpty() || now - lastHomeRefreshTime > 30_000L) {
            refreshHomePackages();
        }
        WindowVisibilityPolicy.Decision decision = evaluateAccessibilitySnapshot(windowState);
        if (decision == WindowVisibilityPolicy.Decision.UNKNOWN) {
            return null;
        }
        return decision == WindowVisibilityPolicy.Decision.HOME_VISIBLE;
    }

    boolean isDeviceReady() {
        return powerManager != null
                && powerManager.isInteractive()
                && (keyguardManager == null || !keyguardManager.isKeyguardLocked())
                && (userManager == null || userManager.isUserUnlocked());
    }

    private WindowVisibilityPolicy.Decision evaluateAccessibilitySnapshot(
            AccessibilityWindowState.Snapshot windowState
    ) {
        return WindowVisibilityPolicy.evaluate(
                windowState.windows,
                windowState.displayWidth,
                windowState.displayHeight,
                homePackages,
                homeComponents,
                eventTracker.mostRecentVisibleActivity(),
                windowState.eventPackage,
                windowState.eventClass,
                context.getPackageName()
        );
    }

    private boolean refreshActivityVisibility() {
        if (usageStatsManager == null || !hasUsageAccess(context)) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean initialQuery = lastQueryTime == 0;
        long queryStarted = SystemClock.elapsedRealtime();
        long begin = ForegroundPollPolicy.queryBegin(now, lastQueryTime);
        UsageEvents events;
        try {
            events = usageStatsManager.queryEvents(begin, now);
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("usage-events", "Usage-events query failed", error);
            return false;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                eventTracker.onResumed(
                        event.getTimeStamp(), event.getPackageName(), event.getClassName());
            } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                eventTracker.onPaused(
                        event.getTimeStamp(), event.getPackageName(), event.getClassName());
            } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                eventTracker.onStopped(
                        event.getTimeStamp(), event.getPackageName(), event.getClassName());
            } else if (event.getEventType() == UsageEvents.Event.DEVICE_SHUTDOWN
                    || event.getEventType() == UsageEvents.Event.DEVICE_STARTUP) {
                eventTracker.onReset(event.getTimeStamp());
            }
        }
        lastQueryTime = now;

        if (!eventTracker.hasObservedEvent()) {
            List<UsageStats> stats;
            try {
                stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        now - 24L * 60L * 60L * 1000L,
                        now
                );
            } catch (RuntimeException error) {
                AppLog.warnRateLimited(
                        "usage-stats", "Usage-stats fallback query failed", error);
                return false;
            }
            if (stats != null) {
                for (UsageStats item : stats) {
                    eventTracker.seed(item.getLastTimeUsed(), item.getPackageName());
                }
            }
        }
        long queryElapsed = SystemClock.elapsedRealtime() - queryStarted;
        if (initialQuery || queryElapsed >= 100L) {
            AppLog.info("Foreground usage query completed in " + queryElapsed + " ms");
        }
        return true;
    }

    private void refreshHomePackages() {
        homePackages.clear();
        homeComponents.clear();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = context.getPackageManager();
        // A head unit may expose more than one HOME shell. Treat every eligible
        // HOME package as a launcher so OEM shell switching does not hide the panel.
        List<ResolveInfo> homes;
        try {
            homes = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("home-query", "HOME package query failed", error);
            lastHomeRefreshTime = System.currentTimeMillis();
            return;
        }
        for (ResolveInfo home : homes) {
            if (home.activityInfo != null) {
                homePackages.add(home.activityInfo.packageName);
                homeComponents.add(WindowVisibilityPolicy.componentKey(
                        home.activityInfo.packageName,
                        home.activityInfo.name
                ));
            }
        }
        lastHomeRefreshTime = System.currentTimeMillis();
    }
}
