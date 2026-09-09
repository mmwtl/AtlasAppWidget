package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Prefs {
    private static final String NAME = "atlas_app_widget_settings";
    private static final Object MIGRATION_LOCK = new Object();
    private static volatile boolean credentialMigrationAttempted;
    static final String KEY_AUTO_START = "auto_start";
    // Legacy launch flags are read once for migration and are not part of the active contract.
    static final String KEY_USE_LAUNCH_PROXY = "use_launch_proxy";
    static final String KEY_USE_DIAGNOSTIC_LAUNCH_ACTIVITY =
            "use_diagnostic_launch_activity";
    private static final String KEY_LEGACY_CLIMATE_MIGRATION_DONE =
            "climate_transition_legacy_migration_done";
    static final String KEY_CLIMATE_TRANSITION_COMPONENTS =
            "climate_transition_components_json";
    static final String KEY_CLIMATE_TRANSITION_DURATION_MS =
            "climate_transition_duration_ms";
    static final String KEY_SHOW_ONLY_IN_APP_LIST = "show_only_in_app_list";
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_APP_UI_SCALE_TENTHS = "app_ui_scale_tenths";
    static final String KEY_FREEFORM_HIDE_THRESHOLD_PERCENT =
            "freeform_hide_threshold_percent";
    static final String KEY_SHOW_DRAG_HANDLE = "show_drag_handle";
    static final String KEY_DRAG_HANDLE_POSITION = "drag_handle_position";
    static final String KEY_SHOW_APP_LABELS = "show_app_labels";
    static final String KEY_APP_LABEL_TEXT_SIZE_SP = "app_label_text_size_sp";
    static final String KEY_APP_LABEL_GAP_DP = "app_label_gap_dp";
    static final String KEY_APP_LABEL_OUTLINE_ENABLED = "app_label_outline_enabled";
    static final String KEY_SHOW_SYSTEM_STATUS = "show_system_status";
    static final String KEY_SHOW_CPU_STATUS = "show_cpu_status";
    static final String KEY_SHOW_RAM_STATUS = "show_ram_status";
    static final String KEY_SHOW_FUEL_STATUS = "show_fuel_status";
    static final String KEY_SYSTEM_STATUS_POSITION = "system_status_position";
    static final String KEY_SYSTEM_STATUS_LINE_HEIGHT_DP = "system_status_line_height_dp";
    static final String KEY_SYSTEM_STATUS_TEXT_SIZE_SP = "system_status_text_size_sp";
    static final String KEY_SYSTEM_STATUS_TEXT_WEIGHT = "system_status_text_weight";
    static final String KEY_FUEL_MULTIPLIER = "fuel_multiplier";
    static final String KEY_FUEL_OFFSET = "fuel_offset";
    static final String KEY_USE_CUSTOM_FUEL_FORMULA = "use_custom_fuel_formula";
    static final String KEY_WIDTH_PERCENT = "width_percent";
    static final String KEY_COLUMNS = "columns";
    static final String KEY_ROWS = "rows";
    static final String KEY_ICON_SIZE_DP = "icon_size_dp";
    static final String KEY_ICON_CORNER_PERCENT = "icon_corner_percent";
    static final String KEY_PADDING_DP = "padding_dp";
    static final String KEY_GAP_DP = "gap_dp";
    static final String KEY_BACKGROUND_COLOR = "background_color";
    static final String KEY_BACKGROUND_ALPHA = "background_alpha";
    static final String KEY_BACKGROUND_STROKE_ENABLED = "background_stroke_enabled";
    static final String KEY_BACKGROUND_STROKE_WIDTH_DP = "background_stroke_width_dp";
    static final String KEY_BACKGROUND_STROKE_ALPHA = "background_stroke_alpha";
    static final String KEY_BACKGROUND_STROKE_COLOR = "background_stroke_color";
    static final String KEY_PANEL_RADIUS_DP = "panel_radius_dp";
    static final String KEY_POSITION_X = "position_x";
    static final String KEY_POSITION_Y = "position_y";
    static final String KEY_SELECTED_COMPONENTS = "selected_components_json";
    static final String KEY_SHORTCUT_CATALOG = "shortcut_catalog_json";
    private static final String KEY_CUSTOM_ICONS = "custom_icons_json";
    private static final String KEY_PORTABLE_SETTINGS_REVISION = "portable_settings_revision";

    static final int CLIMATE_TRANSITION_DURATION_MIN_MS = 50;
    static final int CLIMATE_TRANSITION_DURATION_MAX_MS = 500;
    static final int CLIMATE_TRANSITION_DURATION_STEP_MS = 50;
    static final int CLIMATE_TRANSITION_DURATION_DEFAULT_MS = 500;

    static final int POSITION_UNSET = Integer.MIN_VALUE;

    private final SharedPreferences values;

    Prefs(Context context) {
        Context app = context.getApplicationContext();
        Context storage = app.createDeviceProtectedStorageContext();
        migrateCredentialPreferencesWhenAvailable(app, storage);
        values = storage.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        migrateLegacyClimateTransitionSettings();
    }

    private static void migrateCredentialPreferencesWhenAvailable(
            Context credentialContext,
            Context deviceContext
    ) {
        if (credentialMigrationAttempted) {
            return;
        }
        UserManager users = credentialContext.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) {
            return;
        }
        synchronized (MIGRATION_LOCK) {
            if (credentialMigrationAttempted) {
                return;
            }
            try {
                deviceContext.moveSharedPreferencesFrom(credentialContext, NAME);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot migrate preferences to Direct Boot storage", error);
            }
            credentialMigrationAttempted = true;
        }
    }

    SharedPreferences raw() {
        return values;
    }

    boolean getBoolean(String key, boolean fallback) {
        return values.getBoolean(key, fallback);
    }

    void putBoolean(String key, boolean value) {
        values.edit().putBoolean(key, value).apply();
    }

    int getInt(String key, int fallback) {
        return values.getInt(key, fallback);
    }

    void putInt(String key, int value) {
        values.edit().putInt(key, value).apply();
    }

    float getFloat(String key, float fallback) {
        return values.getFloat(key, fallback);
    }

    void putFloat(String key, float value) {
        values.edit().putFloat(key, value).apply();
    }

    void applyOneOsPreset() {
        values.edit()
                .putBoolean(KEY_SHOW_APP_LABELS, true)
                .putInt(KEY_APP_LABEL_TEXT_SIZE_SP, PanelConfig.ONEOS_APP_LABEL_TEXT_SIZE_SP)
                .putInt(KEY_APP_LABEL_GAP_DP, PanelConfig.ONEOS_APP_LABEL_GAP_DP)
                .putBoolean(KEY_APP_LABEL_OUTLINE_ENABLED, false)
                .putInt(KEY_ICON_SIZE_DP, PanelConfig.ONEOS_ICON_SIZE_DP)
                .putInt(KEY_ICON_CORNER_PERCENT, PanelConfig.ONEOS_ICON_CORNER_PERCENT)
                .apply();
    }

    void putFuelFormula(float multiplier, float offset) {
        values.edit()
                .putFloat(KEY_FUEL_MULTIPLIER, multiplier)
                .putFloat(KEY_FUEL_OFFSET, offset)
                .apply();
    }

    float fuelMultiplier() {
        if (!getBoolean(KEY_USE_CUSTOM_FUEL_FORMULA, false)) {
            return FuelLevelProvider.DEFAULT_MULTIPLIER;
        }
        return getFloat(KEY_FUEL_MULTIPLIER, FuelLevelProvider.DEFAULT_MULTIPLIER);
    }

    float fuelOffset() {
        if (!getBoolean(KEY_USE_CUSTOM_FUEL_FORMULA, false)) {
            return FuelLevelProvider.DEFAULT_OFFSET;
        }
        return getFloat(KEY_FUEL_OFFSET, FuelLevelProvider.DEFAULT_OFFSET);
    }

    void remove(String key) {
        values.edit().remove(key).apply();
    }

    PanelConfig panelConfig() {
        return new PanelConfig(this);
    }

    int freeformHideThresholdPercent() {
        return Math.max(
                WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                Math.min(
                        WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT,
                        getInt(KEY_FREEFORM_HIDE_THRESHOLD_PERCENT,
                                WindowVisibilityPolicy.DEFAULT_HIDE_THRESHOLD_PERCENT)
                )
        );
    }

    boolean needsFuelData() {
        boolean fuelGraphEnabled = getBoolean(KEY_SHOW_SYSTEM_STATUS, false)
                && getBoolean(KEY_SHOW_FUEL_STATUS, true);
        return fuelGraphEnabled || selectedComponents().contains(AppEntry.FUEL_COMPONENT_KEY);
    }

    synchronized List<String> selectedComponents() {
        ArrayList<String> result = new ArrayList<>();
        String json = values.getString(KEY_SELECTED_COMPONENTS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                String value = array.optString(index, "");
                if (!value.isEmpty() && !result.contains(value)) {
                    result.add(value);
                }
            }
        } catch (JSONException error) {
            // Corrupt preferences should not prevent the settings screen opening.
            AppLog.warnRateLimited(
                    "selected-components-json", "Selected-components JSON is corrupt", error);
        }
        return result;
    }

    synchronized void setComponentSelected(String component, boolean selected) {
        List<String> current = selectedComponents();
        current.remove(component);
        if (selected) {
            current.add(component);
        } else {
            removeClimateTransitionComponent(component);
        }
        writeSelected(current);
    }

    synchronized void moveSelected(String component, int delta) {
        List<String> current = selectedComponents();
        int from = current.indexOf(component);
        if (from < 0) {
            return;
        }
        int to = Math.max(0, Math.min(current.size() - 1, from + delta));
        if (from == to) {
            return;
        }
        current.remove(from);
        current.add(to, component);
        writeSelected(current);
    }

    synchronized void retainSelectedComponents(Set<String> availableComponents) {
        List<String> current = selectedComponents();
        if (current.removeIf(component -> !availableComponents.contains(component))) {
            writeSelected(current);
        }
        Set<String> selected = new HashSet<>(current);
        Set<String> climate = climateTransitionComponents();
        if (climate.removeIf(component -> !selected.contains(component))) {
            writeClimateTransitionComponents(climate);
        }
    }

    synchronized boolean isClimateTransitionEnabled(String component) {
        return component != null && climateTransitionComponents().contains(component);
    }

    synchronized void setClimateTransitionEnabled(String component, boolean enabled) {
        if (component == null || AppEntry.FUEL_COMPONENT_KEY.equals(component)) {
            return;
        }
        Set<String> current = climateTransitionComponents();
        if (enabled) {
            if (!selectedComponents().contains(component)) {
                return;
            }
            current.add(component);
        } else {
            current.remove(component);
        }
        writeClimateTransitionComponents(current);
    }

    synchronized Set<String> climateTransitionComponents() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String json = values.getString(KEY_CLIMATE_TRANSITION_COMPONENTS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                String value = array.optString(index, "");
                if (!value.isEmpty() && !AppEntry.FUEL_COMPONENT_KEY.equals(value)) {
                    result.add(value);
                }
            }
        } catch (JSONException error) {
            AppLog.warnRateLimited("climate-transition-components-json",
                    "Climate-transition components JSON is corrupt", error);
        }
        return result;
    }

    synchronized int climateTransitionDurationMs() {
        return normalizeClimateTransitionDuration(getInt(
                KEY_CLIMATE_TRANSITION_DURATION_MS,
                CLIMATE_TRANSITION_DURATION_DEFAULT_MS));
    }

    void setClimateTransitionDurationMs(int durationMs) {
        putInt(KEY_CLIMATE_TRANSITION_DURATION_MS,
                normalizeClimateTransitionDuration(durationMs));
    }

    static int normalizeClimateTransitionDuration(int durationMs) {
        int clamped = Math.max(CLIMATE_TRANSITION_DURATION_MIN_MS,
                Math.min(CLIMATE_TRANSITION_DURATION_MAX_MS, durationMs));
        return ((clamped + CLIMATE_TRANSITION_DURATION_STEP_MS / 2)
                / CLIMATE_TRANSITION_DURATION_STEP_MS)
                * CLIMATE_TRANSITION_DURATION_STEP_MS;
    }

    private void removeClimateTransitionComponent(String component) {
        Set<String> current = climateTransitionComponents();
        if (current.remove(component)) {
            writeClimateTransitionComponents(current);
        }
    }

    private void writeClimateTransitionComponents(Set<String> components) {
        JSONArray array = new JSONArray();
        for (String component : components) {
            array.put(component);
        }
        values.edit().putString(KEY_CLIMATE_TRANSITION_COMPONENTS, array.toString()).apply();
    }

    private void migrateLegacyClimateTransitionSettings() {
        synchronized (MIGRATION_LOCK) {
            migrateLegacyClimateTransitionSettingsLocked();
        }
    }

    private void migrateLegacyClimateTransitionSettingsLocked() {
        if (values.getBoolean(KEY_LEGACY_CLIMATE_MIGRATION_DONE, false)) {
            return;
        }
        boolean legacyEnabled = values.getBoolean(KEY_USE_LAUNCH_PROXY, false)
                || values.getBoolean(KEY_USE_DIAGNOSTIC_LAUNCH_ACTIVITY, false);
        LinkedHashSet<String> climate = new LinkedHashSet<>(climateTransitionComponents());
        if (legacyEnabled) {
            for (String component : selectedComponents()) {
                if (!AppEntry.FUEL_COMPONENT_KEY.equals(component)) {
                    climate.add(component);
                }
            }
        } else {
            climate.clear();
        }
        JSONArray array = new JSONArray();
        for (String component : climate) array.put(component);
        values.edit()
                .putString(KEY_CLIMATE_TRANSITION_COMPONENTS, array.toString())
                .putInt(KEY_CLIMATE_TRANSITION_DURATION_MS,
                        climateTransitionDurationMs())
                .putBoolean(KEY_LEGACY_CLIMATE_MIGRATION_DONE, true)
                .remove(KEY_USE_LAUNCH_PROXY)
                .remove(KEY_USE_DIAGNOSTIC_LAUNCH_ACTIVITY)
                .commit();
    }

    synchronized List<ShortcutSpec> shortcutCatalog() {
        ArrayList<ShortcutSpec> result = new ArrayList<>();
        String json = values.getString(KEY_SHORTCUT_CATALOG, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                try {
                    result.add(new ShortcutSpec(
                            item.getString("key"), item.getString("title"),
                            item.getString("intentUri"), item.getString("targetComponent")));
                } catch (Exception invalid) {
                    AppLog.warnRateLimited("shortcut-catalog-entry-" + index,
                            "Ignoring invalid shortcut catalog entry", invalid);
                }
            }
        } catch (JSONException error) {
            AppLog.warnRateLimited("shortcut-catalog-json",
                    "Shortcut catalog JSON is corrupt", error);
        }
        return result;
    }

    synchronized void saveShortcut(ShortcutSpec shortcut) {
        ArrayList<ShortcutSpec> current = new ArrayList<>(shortcutCatalog());
        boolean replacing = current.removeIf(item -> item.key.equals(shortcut.key));
        if (!replacing && current.size() >= ShortcutSpec.MAX_SHORTCUTS) {
            throw new IllegalArgumentException("Too many shortcuts");
        }
        current.add(shortcut);
        values.edit().putString(KEY_SHORTCUT_CATALOG, shortcutJson(current)).apply();
    }

    synchronized void removeShortcut(String key) {
        ArrayList<ShortcutSpec> current = new ArrayList<>(shortcutCatalog());
        if (current.removeIf(item -> item.key.equals(key))) {
            values.edit().putString(KEY_SHORTCUT_CATALOG, shortcutJson(current)).apply();
        }
        setComponentSelected(key, false);
    }

    private static String shortcutJson(List<ShortcutSpec> shortcuts) {
        JSONArray array = new JSONArray();
        for (ShortcutSpec shortcut : shortcuts) {
            try {
                array.put(new JSONObject()
                        .put("key", shortcut.key)
                        .put("title", shortcut.title)
                        .put("intentUri", shortcut.intentUri)
                        .put("targetComponent", shortcut.targetComponent));
            } catch (JSONException impossible) {
                throw new AssertionError(impossible);
            }
        }
        return array.toString();
    }

    private void writeSelected(List<String> selected) {
        values.edit().putString(KEY_SELECTED_COMPONENTS, selectedJson(selected)).apply();
    }

    boolean replacePortableSettings(Context context, SettingsBackup.Data data) {
        Map<String, String> previousIcons = customIcons();
        Set<String> previousShortcutKeys = new java.util.HashSet<>();
        for (ShortcutSpec shortcut : shortcutCatalog()) previousShortcutKeys.add(shortcut.key);
        Map<String, String> importedIcons = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, byte[]> item : data.customIcons.entrySet()) {
                importedIcons.put(item.getKey(), CustomIconStore.importIcon(
                        context, item.getValue(), item.getKey()));
            }
        } catch (IOException error) {
            AppLog.warn("Cannot restore custom icons", error);
            for (String stored : importedIcons.values()) {
                if (!previousIcons.containsValue(stored)) {
                    CustomIconStore.delete(context, stored);
                }
            }
            return false;
        }
        Map<String, String> mergedIcons = new LinkedHashMap<>();
        Set<String> importedShortcutKeys = new java.util.HashSet<>();
        for (ShortcutSpec shortcut : data.shortcuts) importedShortcutKeys.add(shortcut.key);
        for (Map.Entry<String, String> item : previousIcons.entrySet()) {
            if (!previousShortcutKeys.contains(item.getKey())
                    && !importedShortcutKeys.contains(item.getKey())) {
                mergedIcons.put(item.getKey(), item.getValue());
            }
        }
        mergedIcons.putAll(importedIcons);
        SharedPreferences.Editor editor = values.edit()
                .putBoolean(KEY_AUTO_START, data.autoStart)
                .putBoolean(KEY_SHOW_ONLY_IN_APP_LIST, data.showOnlyInAppList)
                .putInt(KEY_APP_UI_SCALE_TENTHS, data.appUiScaleTenths)
                .putInt(KEY_FREEFORM_HIDE_THRESHOLD_PERCENT,
                        data.freeformHideThresholdPercent)
                .putBoolean(KEY_SHOW_DRAG_HANDLE, data.movement.showDragHandle)
                .putInt(KEY_DRAG_HANDLE_POSITION, data.movement.dragHandlePosition)
                .putBoolean(KEY_SHOW_APP_LABELS, data.content.showAppLabels)
                .putInt(KEY_APP_LABEL_TEXT_SIZE_SP, data.content.appLabelTextSizeSp)
                .putInt(KEY_APP_LABEL_GAP_DP, data.content.appLabelGapDp)
                .putBoolean(KEY_APP_LABEL_OUTLINE_ENABLED,
                        data.content.appLabelOutlineEnabled)
                .putBoolean(KEY_SHOW_SYSTEM_STATUS, data.systemStatus.enabled)
                .putBoolean(KEY_SHOW_CPU_STATUS, data.systemStatus.showCpu)
                .putBoolean(KEY_SHOW_RAM_STATUS, data.systemStatus.showRam)
                .putBoolean(KEY_SHOW_FUEL_STATUS, data.systemStatus.showFuel)
                .putInt(KEY_SYSTEM_STATUS_POSITION, data.systemStatus.position)
                .putInt(KEY_SYSTEM_STATUS_LINE_HEIGHT_DP, data.systemStatus.lineHeightDp)
                .putInt(KEY_SYSTEM_STATUS_TEXT_SIZE_SP, data.systemStatus.textSizeSp)
                .putInt(KEY_SYSTEM_STATUS_TEXT_WEIGHT, data.systemStatus.textWeight)
                .putBoolean(KEY_USE_CUSTOM_FUEL_FORMULA, data.fuel.useCustomFormula)
                .putFloat(KEY_FUEL_MULTIPLIER, data.fuel.multiplier)
                .putFloat(KEY_FUEL_OFFSET, data.fuel.offset)
                .putInt(KEY_WIDTH_PERCENT, data.geometry.widthPercent)
                .putInt(KEY_COLUMNS, data.geometry.columns)
                .putInt(KEY_ROWS, data.geometry.rows)
                .putInt(KEY_ICON_SIZE_DP, data.geometry.iconSizeDp)
                .putInt(KEY_ICON_CORNER_PERCENT, data.geometry.iconCornerPercent)
                .putInt(KEY_PADDING_DP, data.geometry.paddingDp)
                .putInt(KEY_GAP_DP, data.geometry.gapDp)
                .putInt(KEY_BACKGROUND_COLOR, data.appearance.backgroundColor)
                .putInt(KEY_BACKGROUND_ALPHA, data.appearance.backgroundAlpha)
                .putBoolean(KEY_BACKGROUND_STROKE_ENABLED,
                        data.appearance.backgroundStrokeEnabled)
                .putInt(KEY_BACKGROUND_STROKE_WIDTH_DP,
                        data.appearance.backgroundStrokeWidthDp)
                .putInt(KEY_BACKGROUND_STROKE_ALPHA,
                        data.appearance.backgroundStrokeAlpha)
                .putInt(KEY_BACKGROUND_STROKE_COLOR,
                        data.appearance.backgroundStrokeColor)
                .putInt(KEY_PANEL_RADIUS_DP, data.appearance.panelRadiusDp)
                .putString(KEY_SELECTED_COMPONENTS, selectedJson(data.selectedComponents))
                .putString(KEY_SHORTCUT_CATALOG, shortcutJson(data.shortcuts))
                .putString(KEY_CLIMATE_TRANSITION_COMPONENTS,
                        climateTransitionJson(data.climateTransitionComponents))
                .putInt(KEY_CLIMATE_TRANSITION_DURATION_MS, data.climateTransitionDurationMs)
                .putBoolean(KEY_LEGACY_CLIMATE_MIGRATION_DONE, true)
                .remove(KEY_USE_LAUNCH_PROXY)
                .remove(KEY_USE_DIAGNOSTIC_LAUNCH_ACTIVITY)
                .putString(KEY_CUSTOM_ICONS, customIconJson(mergedIcons))
                .putInt(KEY_PORTABLE_SETTINGS_REVISION,
                        values.getInt(KEY_PORTABLE_SETTINGS_REVISION, 0) + 1);
        if (data.positionX == null) {
            editor.remove(KEY_POSITION_X).remove(KEY_POSITION_Y);
        } else {
            editor.putInt(KEY_POSITION_X, data.positionX)
                    .putInt(KEY_POSITION_Y, data.positionY);
        }
        boolean saved = editor.commit();
        if (!saved) {
            for (String stored : importedIcons.values()) {
                if (!previousIcons.containsValue(stored)) {
                    CustomIconStore.delete(context, stored);
                }
            }
            return false;
        }
        for (Map.Entry<String, String> item : previousIcons.entrySet()) {
            String replacement = mergedIcons.get(item.getKey());
            if (!item.getValue().equals(replacement)) {
                CustomIconStore.delete(context, item.getValue());
                IconLoader.clearComponent(item.getKey());
            }
        }
        for (String component : importedIcons.keySet()) {
            IconLoader.clearComponent(component);
        }
        return true;
    }

    private static String selectedJson(List<String> selected) {
        JSONArray array = new JSONArray();
        for (String component : selected) {
            array.put(component);
        }
        return array.toString();
    }

    private static String climateTransitionJson(List<String> components) {
        JSONArray array = new JSONArray();
        for (String component : components) {
            array.put(component);
        }
        return array.toString();
    }

    private static String customIconJson(Map<String, String> icons) {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, String> item : icons.entrySet()) {
            try {
                object.put(item.getKey(), item.getValue());
            } catch (JSONException impossible) {
                throw new AssertionError(impossible);
            }
        }
        return object.toString();
    }

    synchronized String customIcon(String component) {
        JSONObject object = readCustomIcons();
        String value = object.optString(component, null);
        return value == null || value.isEmpty() ? null : value;
    }

    synchronized Map<String, String> customIcons() {
        JSONObject object = readCustomIcons();
        Map<String, String> result = new LinkedHashMap<>();
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = object.optString(key, null);
            if (value != null && !value.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    synchronized void setCustomIcon(String component, String uri) {
        JSONObject object = readCustomIcons();
        try {
            if (uri == null || uri.isEmpty()) {
                object.remove(component);
            } else {
                object.put(component, uri);
            }
            values.edit().putString(KEY_CUSTOM_ICONS, object.toString()).apply();
        } catch (JSONException error) {
            // A component name is always a valid JSONObject key.
            AppLog.warnRateLimited("custom-icons-write", "Cannot update custom-icon JSON", error);
        }
    }

    private JSONObject readCustomIcons() {
        try {
            return new JSONObject(values.getString(KEY_CUSTOM_ICONS, "{}"));
        } catch (JSONException error) {
            AppLog.warnRateLimited("custom-icons-json", "Custom-icon JSON is corrupt", error);
            return new JSONObject();
        }
    }
}
