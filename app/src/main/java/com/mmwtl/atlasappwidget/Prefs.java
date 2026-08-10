package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class Prefs {
    private static final String NAME = "atlas_app_widget_settings";
    private static final Object MIGRATION_LOCK = new Object();
    private static volatile boolean credentialMigrationAttempted;
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_APP_UI_SCALE_TENTHS = "app_ui_scale_tenths";
    static final String KEY_SHOW_DRAG_HANDLE = "show_drag_handle";
    static final String KEY_DRAG_HANDLE_POSITION = "drag_handle_position";
    static final String KEY_SHOW_APP_LABELS = "show_app_labels";
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
    private static final String KEY_CUSTOM_ICONS = "custom_icons_json";
    private static final String KEY_PORTABLE_SETTINGS_REVISION = "portable_settings_revision";

    static final int POSITION_UNSET = Integer.MIN_VALUE;

    private final SharedPreferences values;

    Prefs(Context context) {
        Context app = context.getApplicationContext();
        Context storage = app.createDeviceProtectedStorageContext();
        migrateCredentialPreferencesWhenAvailable(app, storage);
        values = storage.getSharedPreferences(NAME, Context.MODE_PRIVATE);
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
    }

    private void writeSelected(List<String> selected) {
        values.edit().putString(KEY_SELECTED_COMPONENTS, selectedJson(selected)).apply();
    }

    boolean replacePortableSettings(SettingsBackup.Data data) {
        SharedPreferences.Editor editor = values.edit()
                .putBoolean(KEY_AUTO_START, data.autoStart)
                .putInt(KEY_APP_UI_SCALE_TENTHS, data.appUiScaleTenths)
                .putBoolean(KEY_SHOW_DRAG_HANDLE, data.movement.showDragHandle)
                .putInt(KEY_DRAG_HANDLE_POSITION, data.movement.dragHandlePosition)
                .putBoolean(KEY_SHOW_APP_LABELS, data.content.showAppLabels)
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
                .putInt(KEY_PORTABLE_SETTINGS_REVISION,
                        values.getInt(KEY_PORTABLE_SETTINGS_REVISION, 0) + 1);
        if (data.positionX == null) {
            editor.remove(KEY_POSITION_X).remove(KEY_POSITION_Y);
        } else {
            editor.putInt(KEY_POSITION_X, data.positionX)
                    .putInt(KEY_POSITION_Y, data.positionY);
        }
        return editor.commit();
    }

    private static String selectedJson(List<String> selected) {
        JSONArray array = new JSONArray();
        for (String component : selected) {
            array.put(component);
        }
        return array.toString();
    }

    synchronized String customIcon(String component) {
        JSONObject object = readCustomIcons();
        String value = object.optString(component, null);
        return value == null || value.isEmpty() ? null : value;
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
