package mmwtl.atlaswidget;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForegroundAppDetector {
    private final Context context;
    private final UsageStatsManager usageStatsManager;
    private final Set<String> homePackages = new HashSet<>();
    private long lastHomeRefreshTime;
    private long lastQueryTime;
    private long lastEventTime;
    private String lastForegroundPackage;

    ForegroundAppDetector(Context context) {
        this.context = context.getApplicationContext();
        usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        refreshHomePackages();
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    boolean isHomeForeground() {
        long now = System.currentTimeMillis();
        if (homePackages.isEmpty() || now - lastHomeRefreshTime > 30_000L) {
            refreshHomePackages();
        }
        String foreground = currentForegroundPackage();
        return foreground != null && homePackages.contains(foreground);
    }

    String currentForegroundPackage() {
        if (usageStatsManager == null || !hasUsageAccess(context)) {
            return null;
        }
        long now = System.currentTimeMillis();
        long begin = lastQueryTime == 0
                ? now - 12L * 60L * 60L * 1000L
                : Math.max(now - 60_000L, lastQueryTime - 2_000L);
        UsageEvents events = usageStatsManager.queryEvents(begin, now);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                    && event.getTimeStamp() >= lastEventTime) {
                lastEventTime = event.getTimeStamp();
                lastForegroundPackage = event.getPackageName();
            }
        }
        lastQueryTime = now;

        if (lastForegroundPackage == null) {
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 24L * 60L * 60L * 1000L,
                    now
            );
            if (stats != null) {
                for (UsageStats item : stats) {
                    if (item.getLastTimeUsed() >= lastEventTime) {
                        lastEventTime = item.getLastTimeUsed();
                        lastForegroundPackage = item.getPackageName();
                    }
                }
            }
        }
        return lastForegroundPackage;
    }

    void refreshHomePackages() {
        homePackages.clear();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = context.getPackageManager();
        // A head unit may expose more than one HOME shell. Treat every eligible
        // HOME package as a launcher so OEM shell switching does not hide the panel.
        List<ResolveInfo> homes = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo home : homes) {
            if (home.activityInfo != null) {
                homePackages.add(home.activityInfo.packageName);
            }
        }
        lastHomeRefreshTime = System.currentTimeMillis();
    }
}
