package mmwtl.atlaswidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        List<AppEntry> available = loadLaunchableActivities(context);
        Map<String, AppEntry> byComponent = new LinkedHashMap<>();
        for (AppEntry entry : available) {
            byComponent.put(entry.componentKey, entry);
        }

        ArrayList<AppEntry> selected = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        for (String key : prefs.selectedComponents()) {
            AppEntry entry = byComponent.get(key);
            if (entry == null) {
                entry = loadExact(packageManager, key);
            }
            if (entry != null) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private static AppEntry loadExact(PackageManager packageManager, String key) {
        ComponentName component = ComponentName.unflattenFromString(key);
        if (component == null) {
            return null;
        }
        try {
            ActivityInfo activity = packageManager.getActivityInfo(component, 0);
            if (!activity.exported) {
                return null;
            }
            String appLabel = safeLabel(activity.applicationInfo.loadLabel(packageManager), component.getPackageName());
            String activityLabel = safeLabel(activity.loadLabel(packageManager), component.getClassName());
            return new AppEntry(component, appLabel, activityLabel);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private static String safeLabel(CharSequence value, String fallback) {
        if (value == null || value.toString().trim().isEmpty()) {
            return fallback;
        }
        return value.toString().trim();
    }
}
