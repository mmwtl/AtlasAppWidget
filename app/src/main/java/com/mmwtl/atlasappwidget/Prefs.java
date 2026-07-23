package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class Prefs {
    static final int MAX_AUTO_START_DELAY_SECONDS = 300;
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_AUTO_START_DELAY_SECONDS = "auto_start_delay_seconds";
    static final String KEY_AUTO_START_PENDING_UNTIL_MS = "auto_start_pending_until_ms";
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_APP_UI_SCALE_TENTHS = "app_ui_scale_tenths";
    static final String KEY_SHOW_DRAG_HANDLE = "show_drag_handle";
    static final String KEY_DRAG_HANDLE_POSITION = "drag_handle_position";
    static final String KEY_SHOW_APP_LABELS = "show_app_labels";
    static final String KEY_SHOW_SYSTEM_STATUS = "show_system_status";
    static final String KEY_SYSTEM_STATUS_POSITION = "system_status_position";
    static final String KEY_SYSTEM_STATUS_LINE_HEIGHT_DP = "system_status_line_height_dp";
    static final String KEY_SYSTEM_STATUS_TEXT_SIZE_SP = "system_status_text_size_sp";
    static final String KEY_SYSTEM_STATUS_TEXT_WEIGHT = "system_status_text_weight";
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
    private static final String KEY_SELECTED_COMPONENTS = "selected_components_json";
    private static final String KEY_CUSTOM_ICONS = "custom_icons_json";

    static final int POSITION_UNSET = Integer.MIN_VALUE;

    private final SharedPreferences values;

    Prefs(Context context) {
        values = context.getApplicationContext()
                .getSharedPreferences("atlas_app_widget_settings", Context.MODE_PRIVATE);
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

    long getLong(String key, long fallback) {
        return values.getLong(key, fallback);
    }

    void putLong(String key, long value) {
        values.edit().putLong(key, value).apply();
    }

    void remove(String key) {
        values.edit().remove(key).apply();
    }

    PanelConfig panelConfig() {
        return new PanelConfig(this);
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
        JSONArray array = new JSONArray();
        for (String component : selected) {
            array.put(component);
        }
        values.edit().putString(KEY_SELECTED_COMPONENTS, array.toString()).apply();
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
