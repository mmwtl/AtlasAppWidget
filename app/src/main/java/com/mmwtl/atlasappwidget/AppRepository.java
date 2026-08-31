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

        for (ShortcutEntry shortcut : loadValidShortcuts(context)) {
            String targetLabel = safeActivityLabel(packageManager, shortcut.componentName);
            unique.put(shortcut.spec.key, new AppEntry(shortcut, targetLabel));
        }

        ArrayList<AppEntry> result = new ArrayList<>(unique.values());
        result.add(AppEntry.fuel(
                context.getString(R.string.fuel_tile_name),
                context.getString(R.string.fuel_tile_picker_description)
        ));
        result.sort(Comparator
                .comparingInt((AppEntry item) -> item.isFuel() ? 0 : 1)
                .thenComparing(item -> item.label.toLowerCase(Locale.getDefault()))
                .thenComparing(item -> item.activityLabel.toLowerCase(Locale.getDefault())));
        return result;
    }

    static List<AppEntry> loadSelectedActivities(Context context, Prefs prefs) {
        List<String> selectedKeys = prefs.selectedComponents();
        if (selectedKeys.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> selectedKeySet = new LinkedHashSet<>(selectedKeys);

        PackageManager packageManager = context.getPackageManager();
        Map<String, AppEntry> byComponent = new LinkedHashMap<>();
        if (selectedKeySet.contains(AppEntry.FUEL_COMPONENT_KEY)) {
            byComponent.put(AppEntry.FUEL_COMPONENT_KEY, AppEntry.fuel(
                    context.getString(R.string.fuel_tile_name),
                    context.getString(R.string.fuel_tile_picker_description)
            ));
        }
        for (ShortcutEntry shortcut : loadValidShortcuts(context)) {
            if (selectedKeySet.contains(shortcut.spec.key)) {
                byComponent.put(shortcut.spec.key, new AppEntry(shortcut,
                        safeActivityLabel(packageManager, shortcut.componentName)));
            }
        }
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
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

        ArrayList<AppEntry> selected = new ArrayList<>();
        for (String key : selectedKeys) {
            AppEntry entry = byComponent.get(key);
            if (entry != null) {
                selected.add(entry);
            }
        }
        return selected;
    }

    static List<AppEntry> placeholderSelectedActivities(Context context, Prefs prefs) {
        List<AppEntry> result = new ArrayList<>();
        Map<String, ShortcutEntry> shortcuts = new LinkedHashMap<>();
        for (ShortcutEntry shortcut : loadValidShortcuts(context)) {
            shortcuts.put(shortcut.spec.key, shortcut);
        }
        for (String key : prefs.selectedComponents()) {
            if (AppEntry.FUEL_COMPONENT_KEY.equals(key)) {
                result.add(AppEntry.fuel(
                        context.getString(R.string.fuel_tile_name),
                        context.getString(R.string.fuel_tile_picker_description)
                ));
                continue;
            }
            ComponentName component = ComponentName.unflattenFromString(key);
            if (component != null) {
                result.add(new AppEntry(component, "", ""));
                continue;
            }
            ShortcutEntry shortcut = shortcuts.get(key);
            if (shortcut != null) {
                result.add(new AppEntry(shortcut, ""));
            }
        }
        return result;
    }

    private static List<ShortcutEntry> loadValidShortcuts(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ArrayList<ShortcutEntry> result = new ArrayList<>();
        for (ShortcutSpec spec : new Prefs(context).shortcutCatalog()) {
            try {
                ShortcutEntry entry = new ShortcutEntry(spec);
                ActivityInfo info = packageManager.getActivityInfo(entry.componentName, 0);
                Intent intent = spec.parseIntent();
                if (intent.getComponent() == null
                        || !entry.componentName.equals(intent.getComponent())
                        || !info.exported || !info.enabled
                        || info.applicationInfo == null || !info.applicationInfo.enabled) {
                    continue;
                }
                result.add(entry);
            } catch (Exception invalid) {
                AppLog.warnRateLimited("shortcut-invalid-" + spec.key,
                        "Ignoring unavailable shortcut", invalid);
            }
        }
        return result;
    }

    private static String safeActivityLabel(PackageManager packageManager,
            ComponentName component) {
        try {
            ActivityInfo info = packageManager.getActivityInfo(component, 0);
            return safeLabel(info.loadLabel(packageManager), component.getClassName());
        } catch (Exception ignored) {
            return component.getClassName();
        }
    }

    private static String safeLabel(CharSequence value, String fallback) {
        if (value == null || value.toString().trim().isEmpty()) {
            return fallback;
        }
        return value.toString().trim();
    }
}
