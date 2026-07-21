package com.mmwtl.atlasappwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AppRepository {
    private AppRepository() {
    }

    static List<AppEntry> loadLaunchableActivities(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL
        );
        Map<String, AppEntry> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null || !activity.exported) {
                continue;
            }
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            String appLabel = safeLabel(activity.applicationInfo.loadLabel(packageManager), activity.packageName);
            String activityLabel = safeLabel(info.loadLabel(packageManager), activity.name);
            AppEntry entry = new AppEntry(component, appLabel, activityLabel);
            unique.put(entry.componentKey, entry);
        }

        ArrayList<AppEntry> result = new ArrayList<>(unique.values());
        result.sort(Comparator
                .comparing((AppEntry item) -> item.label.toLowerCase(Locale.getDefault()))
                .thenComparing(item -> item.activityLabel.toLowerCase(Locale.getDefault())));
        return result;
    }

    static List<AppEntry> loadSelectedActivities(Context context, Prefs prefs) {
        List<String> selectedKeys = prefs.selectedComponents();
        if (selectedKeys.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> selectedKeySet = new LinkedHashSet<>(selectedKeys);

        Set<String> selectedPackages = new LinkedHashSet<>();
        for (String key : selectedKeys) {
            ComponentName component = ComponentName.unflattenFromString(key);
            if (component != null) {
                selectedPackages.add(component.getPackageName());
            }
        }

        PackageManager packageManager = context.getPackageManager();
        Map<String, AppEntry> byComponent = new LinkedHashMap<>();
        for (String packageName : selectedPackages) {
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName);
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.MATCH_ALL
            );
            for (ResolveInfo info : resolved) {
                ActivityInfo activity = info.activityInfo;
                if (activity == null || !activity.exported || !activity.enabled
                        || activity.applicationInfo == null
                        || !activity.applicationInfo.enabled) {
                    continue;
                }
                ComponentName component = new ComponentName(activity.packageName, activity.name);
                String key = component.flattenToString();
                if (!selectedKeySet.contains(key)) {
                    continue;
                }
                String appLabel = safeLabel(
                        activity.applicationInfo.loadLabel(packageManager),
                        activity.packageName
                );
                String activityLabel = safeLabel(info.loadLabel(packageManager), activity.name);
                byComponent.put(key, new AppEntry(component, appLabel, activityLabel));
            }
        }

        ArrayList<AppEntry> selected = new ArrayList<>();
        for (String key : selectedKeys) {
            AppEntry entry = byComponent.get(key);
            if (entry != null) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private static String safeLabel(CharSequence value, String fallback) {
        if (value == null || value.toString().trim().isEmpty()) {
            return fallback;
        }
        return value.toString().trim();
    }
}
