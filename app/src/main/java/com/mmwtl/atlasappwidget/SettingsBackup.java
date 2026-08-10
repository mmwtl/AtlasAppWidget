package com.mmwtl.atlasappwidget;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SettingsBackup {
    static final String FILE_NAME = "AtlasAppWidget-settings.json";
    private static final String FORMAT = "atlas-app-widget-settings";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_SELECTED_COMPONENTS = 200;
    private static final int MAX_COMPONENT_LENGTH = 2_048;

    static final class Data {
        final boolean autoStart;
        final int appUiScaleTenths;
        final Integer positionX;
        final Integer positionY;
        final List<String> selectedComponents;
        final ContentData content;
        final MovementData movement;
        final SystemStatusData systemStatus;
        final FuelData fuel;
        final GeometryData geometry;
        final AppearanceData appearance;

        Data(boolean autoStart, int appUiScaleTenths, Integer positionX, Integer positionY,
                List<String> selectedComponents, ContentData content, MovementData movement,
                SystemStatusData systemStatus, FuelData fuel, GeometryData geometry,
                AppearanceData appearance) throws IOException {
            this.autoStart = autoStart;
            this.appUiScaleTenths = requireRange("settings.uiScaleTenths", appUiScaleTenths,
                    ScaledActivity.MIN_SCALE_TENTHS, ScaledActivity.MAX_SCALE_TENTHS);
            if ((positionX == null) != (positionY == null)) {
                throw invalid("Положение overlay должно содержать обе координаты");
            }
            if (positionX != null && (positionX == Prefs.POSITION_UNSET
                    || positionY == Prefs.POSITION_UNSET)) {
                throw invalid("Недопустимое положение overlay");
            }
            this.positionX = positionX;
            this.positionY = positionY;
            this.selectedComponents = validateSelectedComponents(selectedComponents);
            if (content == null || movement == null || systemStatus == null || fuel == null
                    || geometry == null || appearance == null) {
                throw invalid("В JSON отсутствует раздел настроек");
            }
            this.content = content;
            this.movement = movement.validated();
            this.systemStatus = systemStatus.validated();
            this.fuel = fuel.validated();
            this.geometry = geometry.validated();
            this.appearance = appearance.validated();
        }
    }

    static final class ContentData {
        final boolean showAppLabels;

        ContentData(boolean showAppLabels) {
            this.showAppLabels = showAppLabels;
        }
    }

    static final class MovementData {
        final boolean showDragHandle;
        final int dragHandlePosition;

        MovementData(boolean showDragHandle, int dragHandlePosition) {
            this.showDragHandle = showDragHandle;
            this.dragHandlePosition = dragHandlePosition;
        }

        private MovementData validated() throws IOException {
            requireRange("settings.movement.dragHandlePosition", dragHandlePosition,
                    PanelConfig.HANDLE_LEFT, PanelConfig.HANDLE_BOTTOM);
            return this;
        }
    }

    static final class SystemStatusData {
        final boolean enabled;
        final boolean showCpu;
        final boolean showRam;
        final boolean showFuel;
        final int position;
        final int lineHeightDp;
        final int textSizeSp;
        final int textWeight;

        SystemStatusData(boolean enabled, boolean showCpu, boolean showRam, boolean showFuel,
                int position, int lineHeightDp, int textSizeSp, int textWeight) {
            this.enabled = enabled;
            this.showCpu = showCpu;
            this.showRam = showRam;
            this.showFuel = showFuel;
            this.position = position;
            this.lineHeightDp = lineHeightDp;
            this.textSizeSp = textSizeSp;
            this.textWeight = textWeight;
        }

        private SystemStatusData validated() throws IOException {
            if (!showCpu && !showRam && !showFuel) {
                throw invalid("Должен быть включён хотя бы один системный индикатор");
            }
            requireRange("settings.systemStatus.position", position,
                    PanelConfig.STATUS_TOP, PanelConfig.STATUS_RIGHT);
            requireRange("settings.systemStatus.lineHeightDp", lineHeightDp,
                    PanelConfig.STATUS_LINE_HEIGHT_MIN_DP,
                    PanelConfig.STATUS_LINE_HEIGHT_MAX_DP);
            requireRange("settings.systemStatus.textSizeSp", textSizeSp,
                    PanelConfig.STATUS_TEXT_SIZE_MIN_SP, PanelConfig.STATUS_TEXT_SIZE_MAX_SP);
            requireRange("settings.systemStatus.textWeight", textWeight,
                    PanelConfig.STATUS_TEXT_WEIGHT_MIN, PanelConfig.STATUS_TEXT_WEIGHT_MAX);
            if (textWeight % 100 != 0) {
                throw invalid("Поле settings.systemStatus.textWeight должно быть кратно 100");
            }
            return this;
        }
    }

    static final class FuelData {
        final boolean useCustomFormula;
        final float multiplier;
        final float offset;

        FuelData(boolean useCustomFormula, float multiplier, float offset) {
            this.useCustomFormula = useCustomFormula;
            this.multiplier = multiplier;
            this.offset = offset;
        }

        private FuelData validated() throws IOException {
            requireFloatRange("settings.fuel.multiplier", multiplier, -100f, 100f);
            requireFloatRange("settings.fuel.offset", offset, -1_000f, 1_000f);
            return this;
        }
    }

    static final class GeometryData {
        final int widthPercent;
        final int columns;
        final int rows;
        final int iconSizeDp;
        final int iconCornerPercent;
        final int paddingDp;
        final int gapDp;

        GeometryData(int widthPercent, int columns, int rows, int iconSizeDp,
                int iconCornerPercent, int paddingDp, int gapDp) {
            this.widthPercent = widthPercent;
            this.columns = columns;
            this.rows = rows;
            this.iconSizeDp = iconSizeDp;
            this.iconCornerPercent = iconCornerPercent;
            this.paddingDp = paddingDp;
            this.gapDp = gapDp;
        }

        private GeometryData validated() throws IOException {
            requireRange("settings.geometry.widthPercent", widthPercent, 25, 100);
            requireRange("settings.geometry.columns", columns, 1, 10);
            requireRange("settings.geometry.rows", rows, 1, 4);
            requireRange("settings.geometry.iconSizeDp", iconSizeDp, 40, 240);
            requireRange("settings.geometry.iconCornerPercent", iconCornerPercent, 0, 50);
            requireRange("settings.geometry.paddingDp", paddingDp, 4, 40);
            requireRange("settings.geometry.gapDp", gapDp, 0, 40);
            return this;
        }
    }

    static final class AppearanceData {
        final int backgroundColor;
        final int backgroundAlpha;
        final boolean backgroundStrokeEnabled;
        final int backgroundStrokeWidthDp;
        final int backgroundStrokeAlpha;
        final int backgroundStrokeColor;
        final int panelRadiusDp;

        AppearanceData(int backgroundColor, int backgroundAlpha,
                boolean backgroundStrokeEnabled, int backgroundStrokeWidthDp,
                int backgroundStrokeAlpha, int backgroundStrokeColor, int panelRadiusDp) {
            this.backgroundColor = backgroundColor;
            this.backgroundAlpha = backgroundAlpha;
            this.backgroundStrokeEnabled = backgroundStrokeEnabled;
            this.backgroundStrokeWidthDp = backgroundStrokeWidthDp;
            this.backgroundStrokeAlpha = backgroundStrokeAlpha;
            this.backgroundStrokeColor = backgroundStrokeColor;
            this.panelRadiusDp = panelRadiusDp;
        }

        private AppearanceData validated() throws IOException {
            requireRange("settings.appearance.backgroundAlpha", backgroundAlpha, 0, 255);
            requireRange("settings.appearance.backgroundStrokeWidthDp",
                    backgroundStrokeWidthDp, 1, 20);
            requireRange("settings.appearance.backgroundStrokeAlpha",
                    backgroundStrokeAlpha, 0, 255);
            requireRange("settings.appearance.panelRadiusDp", panelRadiusDp,
                    0, PanelConfig.PANEL_RADIUS_FULLY_ROUNDED);
            return this;
        }
    }

    private SettingsBackup() {
    }

    static Data capture(Prefs prefs) throws IOException {
        int x = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int y = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        Integer positionX = x == Prefs.POSITION_UNSET || y == Prefs.POSITION_UNSET ? null : x;
        Integer positionY = positionX == null ? null : y;
        boolean showCpu = prefs.getBoolean(Prefs.KEY_SHOW_CPU_STATUS, true);
        boolean showRam = prefs.getBoolean(Prefs.KEY_SHOW_RAM_STATUS, true);
        boolean showFuel = prefs.getBoolean(Prefs.KEY_SHOW_FUEL_STATUS, true);
        if (!showCpu && !showRam && !showFuel) {
            showCpu = true;
        }
        return new Data(
                prefs.getBoolean(Prefs.KEY_AUTO_START, false),
                clamp(prefs.getInt(Prefs.KEY_APP_UI_SCALE_TENTHS,
                                ScaledActivity.DEFAULT_SCALE_TENTHS),
                        ScaledActivity.MIN_SCALE_TENTHS, ScaledActivity.MAX_SCALE_TENTHS),
                positionX,
                positionY,
                prefs.selectedComponents(),
                new ContentData(prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false)),
                new MovementData(
                        prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true),
                        clamp(prefs.getInt(Prefs.KEY_DRAG_HANDLE_POSITION,
                                        PanelConfig.HANDLE_LEFT),
                                PanelConfig.HANDLE_LEFT, PanelConfig.HANDLE_BOTTOM)),
                new SystemStatusData(
                        prefs.getBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false),
                        showCpu,
                        showRam,
                        showFuel,
                        clamp(prefs.getInt(Prefs.KEY_SYSTEM_STATUS_POSITION,
                                        PanelConfig.STATUS_BOTTOM),
                                PanelConfig.STATUS_TOP, PanelConfig.STATUS_RIGHT),
                        clamp(prefs.getInt(Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP,
                                        PanelConfig.STATUS_LINE_HEIGHT_DEFAULT_DP),
                                PanelConfig.STATUS_LINE_HEIGHT_MIN_DP,
                                PanelConfig.STATUS_LINE_HEIGHT_MAX_DP),
                        clamp(prefs.getInt(Prefs.KEY_SYSTEM_STATUS_TEXT_SIZE_SP,
                                        PanelConfig.STATUS_TEXT_SIZE_DEFAULT_SP),
                                PanelConfig.STATUS_TEXT_SIZE_MIN_SP,
                                PanelConfig.STATUS_TEXT_SIZE_MAX_SP),
                        clampToHundreds(prefs.getInt(Prefs.KEY_SYSTEM_STATUS_TEXT_WEIGHT,
                                PanelConfig.STATUS_TEXT_WEIGHT_DEFAULT))),
                new FuelData(
                        prefs.getBoolean(Prefs.KEY_USE_CUSTOM_FUEL_FORMULA, false),
                        clamp(prefs.getFloat(Prefs.KEY_FUEL_MULTIPLIER,
                                FuelLevelProvider.DEFAULT_MULTIPLIER), -100f, 100f),
                        clamp(prefs.getFloat(Prefs.KEY_FUEL_OFFSET,
                                FuelLevelProvider.DEFAULT_OFFSET), -1_000f, 1_000f)),
                new GeometryData(
                        clamp(prefs.getInt(Prefs.KEY_WIDTH_PERCENT, 72), 25, 100),
                        clamp(prefs.getInt(Prefs.KEY_COLUMNS, 5), 1, 10),
                        clamp(prefs.getInt(Prefs.KEY_ROWS, 1), 1, 4),
                        clamp(prefs.getInt(Prefs.KEY_ICON_SIZE_DP, 72), 40, 240),
                        clamp(prefs.getInt(Prefs.KEY_ICON_CORNER_PERCENT, 12), 0, 50),
                        clamp(prefs.getInt(Prefs.KEY_PADDING_DP, 14), 4, 40),
                        clamp(prefs.getInt(Prefs.KEY_GAP_DP, 12), 0, 40)),
                new AppearanceData(
                        prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, 0xFF262626),
                        clamp(prefs.getInt(Prefs.KEY_BACKGROUND_ALPHA, 235), 0, 255),
                        prefs.getBoolean(Prefs.KEY_BACKGROUND_STROKE_ENABLED, false),
                        clamp(prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, 2), 1, 20),
                        clamp(prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_ALPHA, 200), 0, 255),
                        prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, Ui.ACCENT),
                        clamp(prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, 8),
                                0, PanelConfig.PANEL_RADIUS_FULLY_ROUNDED)));
    }

    static void write(Context context, Prefs prefs, Uri uri) throws IOException {
        if (uri == null) throw invalid("Файл не выбран");
        writeContents(context.getContentResolver(), uri, encodedContents(context, prefs));
    }

    static String writeToDownloads(Context context, Prefs prefs) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw invalid("Не удалось создать JSON в папке Download");
        try {
            writeContents(resolver, uri, encodedContents(context, prefs));
            ContentValues published = new ContentValues();
            published.put(MediaStore.Downloads.IS_PENDING, 0);
            if (resolver.update(uri, published, null, null) <= 0) {
                throw invalid("Не удалось опубликовать JSON в папке Download");
            }
            return displayName(resolver, uri);
        } catch (IOException | RuntimeException error) {
            try {
                resolver.delete(uri, null, null);
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            if (error instanceof IOException ioError) throw ioError;
            throw invalid("Не удалось сохранить JSON в папку Download", error);
        }
    }

    private static byte[] encodedContents(Context context, Prefs prefs) throws IOException {
        return encode(capture(prefs), appVersion(context)).getBytes(StandardCharsets.UTF_8);
    }

    private static void writeContents(ContentResolver resolver, Uri uri, byte[] contents)
            throws IOException {
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw invalid("Не удалось открыть файл для записи");
            output.write(contents);
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isBlank()) return name;
            }
        } catch (RuntimeException error) {
            AppLog.warn("Cannot read exported settings display name", error);
        }
        return FILE_NAME;
    }

    static Data read(Context context, Uri uri) throws IOException {
        if (uri == null) throw invalid("Файл не выбран");
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw invalid("Не удалось открыть файл");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) {
                    throw invalid("JSON настроек больше 256 КБ");
                }
                output.write(buffer, 0, count);
            }
            return decode(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    static String encode(Data data, String appVersion) throws IOException {
        try {
            JSONObject root = new JSONObject()
                    .put("format", FORMAT)
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("appVersion", appVersion == null ? "" : appVersion);
            JSONObject settings = new JSONObject()
                    .put("autoStart", data.autoStart)
                    .put("uiScaleTenths", data.appUiScaleTenths)
                    .put("selectedComponents", new JSONArray(data.selectedComponents))
                    .put("content", new JSONObject()
                            .put("showAppLabels", data.content.showAppLabels))
                    .put("movement", new JSONObject()
                            .put("showDragHandle", data.movement.showDragHandle)
                            .put("dragHandlePosition", data.movement.dragHandlePosition))
                    .put("systemStatus", new JSONObject()
                            .put("enabled", data.systemStatus.enabled)
                            .put("showCpu", data.systemStatus.showCpu)
                            .put("showRam", data.systemStatus.showRam)
                            .put("showFuel", data.systemStatus.showFuel)
                            .put("position", data.systemStatus.position)
                            .put("lineHeightDp", data.systemStatus.lineHeightDp)
                            .put("textSizeSp", data.systemStatus.textSizeSp)
                            .put("textWeight", data.systemStatus.textWeight))
                    .put("fuel", new JSONObject()
                            .put("useCustomFormula", data.fuel.useCustomFormula)
                            .put("multiplier", readableFloat(data.fuel.multiplier))
                            .put("offset", readableFloat(data.fuel.offset)))
                    .put("geometry", new JSONObject()
                            .put("widthPercent", data.geometry.widthPercent)
                            .put("columns", data.geometry.columns)
                            .put("rows", data.geometry.rows)
                            .put("iconSizeDp", data.geometry.iconSizeDp)
                            .put("iconCornerPercent", data.geometry.iconCornerPercent)
                            .put("paddingDp", data.geometry.paddingDp)
                            .put("gapDp", data.geometry.gapDp))
                    .put("appearance", new JSONObject()
                            .put("backgroundColor", data.appearance.backgroundColor)
                            .put("backgroundAlpha", data.appearance.backgroundAlpha)
                            .put("backgroundStrokeEnabled",
                                    data.appearance.backgroundStrokeEnabled)
                            .put("backgroundStrokeWidthDp",
                                    data.appearance.backgroundStrokeWidthDp)
                            .put("backgroundStrokeAlpha",
                                    data.appearance.backgroundStrokeAlpha)
                            .put("backgroundStrokeColor",
                                    data.appearance.backgroundStrokeColor)
                            .put("panelRadiusDp", data.appearance.panelRadiusDp));
            if (data.positionX == null) {
                settings.put("overlayPosition", JSONObject.NULL);
            } else {
                settings.put("overlayPosition", new JSONObject()
                        .put("x", data.positionX)
                        .put("y", data.positionY));
            }
            root.put("settings", settings);
            return root.toString(2) + '\n';
        } catch (JSONException error) {
            throw new IOException("Не удалось сформировать JSON настроек", error);
        }
    }

    static Data decode(String json) throws IOException {
        try {
            if (json != null && !json.isEmpty() && json.charAt(0) == '\ufeff') {
                json = json.substring(1);
            }
            JSONObject root = new JSONObject(json == null ? "" : json);
            if (!FORMAT.equals(requireString(root, "format", "format"))) {
                throw invalid("Это не файл настроек Atlas App Widget");
            }
            int version = requireInt(root, "schemaVersion", "schemaVersion");
            if (version != SCHEMA_VERSION) {
                throw invalid("Неподдерживаемая версия JSON: " + version);
            }
            JSONObject settings = requireObject(root, "settings", "settings");
            JSONObject content = requireObject(settings, "content", "settings.content");
            JSONObject movement = requireObject(settings, "movement", "settings.movement");
            JSONObject status = requireObject(settings, "systemStatus", "settings.systemStatus");
            JSONObject fuel = requireObject(settings, "fuel", "settings.fuel");
            JSONObject geometry = requireObject(settings, "geometry", "settings.geometry");
            JSONObject appearance = requireObject(settings, "appearance", "settings.appearance");
            Object positionValue = requireValue(settings, "overlayPosition",
                    "settings.overlayPosition");
            Integer x = null;
            Integer y = null;
            if (positionValue != JSONObject.NULL) {
                if (!(positionValue instanceof JSONObject position)) {
                    throw invalid("settings.overlayPosition должен быть объектом или null");
                }
                x = requireInt(position, "x", "settings.overlayPosition.x");
                y = requireInt(position, "y", "settings.overlayPosition.y");
            }
            return new Data(
                    requireBoolean(settings, "autoStart", "settings.autoStart"),
                    requireInt(settings, "uiScaleTenths", "settings.uiScaleTenths"),
                    x,
                    y,
                    requireStringList(settings, "selectedComponents",
                            "settings.selectedComponents"),
                    new ContentData(requireBoolean(content, "showAppLabels",
                            "settings.content.showAppLabels")),
                    new MovementData(
                            requireBoolean(movement, "showDragHandle",
                                    "settings.movement.showDragHandle"),
                            requireInt(movement, "dragHandlePosition",
                                    "settings.movement.dragHandlePosition")),
                    new SystemStatusData(
                            requireBoolean(status, "enabled", "settings.systemStatus.enabled"),
                            requireBoolean(status, "showCpu", "settings.systemStatus.showCpu"),
                            requireBoolean(status, "showRam", "settings.systemStatus.showRam"),
                            requireBoolean(status, "showFuel", "settings.systemStatus.showFuel"),
                            requireInt(status, "position", "settings.systemStatus.position"),
                            requireInt(status, "lineHeightDp",
                                    "settings.systemStatus.lineHeightDp"),
                            requireInt(status, "textSizeSp", "settings.systemStatus.textSizeSp"),
                            requireInt(status, "textWeight", "settings.systemStatus.textWeight")),
                    new FuelData(
                            requireBoolean(fuel, "useCustomFormula",
                                    "settings.fuel.useCustomFormula"),
                            requireFloat(fuel, "multiplier", "settings.fuel.multiplier"),
                            requireFloat(fuel, "offset", "settings.fuel.offset")),
                    new GeometryData(
                            requireInt(geometry, "widthPercent",
                                    "settings.geometry.widthPercent"),
                            requireInt(geometry, "columns", "settings.geometry.columns"),
                            requireInt(geometry, "rows", "settings.geometry.rows"),
                            requireInt(geometry, "iconSizeDp", "settings.geometry.iconSizeDp"),
                            requireInt(geometry, "iconCornerPercent",
                                    "settings.geometry.iconCornerPercent"),
                            requireInt(geometry, "paddingDp", "settings.geometry.paddingDp"),
                            requireInt(geometry, "gapDp", "settings.geometry.gapDp")),
                    new AppearanceData(
                            requireInt(appearance, "backgroundColor",
                                    "settings.appearance.backgroundColor"),
                            requireInt(appearance, "backgroundAlpha",
                                    "settings.appearance.backgroundAlpha"),
                            requireBoolean(appearance, "backgroundStrokeEnabled",
                                    "settings.appearance.backgroundStrokeEnabled"),
                            requireInt(appearance, "backgroundStrokeWidthDp",
                                    "settings.appearance.backgroundStrokeWidthDp"),
                            requireInt(appearance, "backgroundStrokeAlpha",
                                    "settings.appearance.backgroundStrokeAlpha"),
                            requireInt(appearance, "backgroundStrokeColor",
                                    "settings.appearance.backgroundStrokeColor"),
                            requireInt(appearance, "panelRadiusDp",
                                    "settings.appearance.panelRadiusDp")));
        } catch (JSONException error) {
            throw invalid("Повреждённый JSON настроек", error);
        }
    }

    private static List<String> validateSelectedComponents(List<String> components)
            throws IOException {
        if (components == null) throw invalid("Не указан список выбранных элементов");
        if (components.size() > MAX_SELECTED_COMPONENTS) {
            throw invalid("Слишком много выбранных элементов");
        }
        Set<String> unique = new HashSet<>();
        ArrayList<String> result = new ArrayList<>(components.size());
        for (String component : components) {
            if (component == null || component.isEmpty()
                    || component.length() > MAX_COMPONENT_LENGTH) {
                throw invalid("Некорректный элемент в settings.selectedComponents");
            }
            if (!unique.add(component)) {
                throw invalid("Повторяющийся элемент в settings.selectedComponents");
            }
            result.add(component);
        }
        return List.copyOf(result);
    }

    private static List<String> requireStringList(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (!(value instanceof JSONArray array)) {
            throw invalid("Поле " + path + " должно быть массивом");
        }
        ArrayList<String> result = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            Object item;
            try {
                item = array.get(index);
            } catch (JSONException error) {
                throw invalid("Не удалось прочитать " + path + "[" + index + "]", error);
            }
            if (!(item instanceof String text)) {
                throw invalid("Поле " + path + "[" + index + "] должно быть строкой");
            }
            result.add(text);
        }
        return result;
    }

    private static String appVersion(Context context) {
        try {
            String value = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return value == null ? "" : value;
        } catch (PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    private static Object requireValue(JSONObject object, String key, String path)
            throws IOException {
        if (!object.has(key)) throw invalid("Отсутствует поле " + path);
        try {
            return object.get(key);
        } catch (JSONException error) {
            throw invalid("Не удалось прочитать поле " + path, error);
        }
    }

    private static JSONObject requireObject(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (value instanceof JSONObject nested) return nested;
        throw invalid("Поле " + path + " должно быть объектом");
    }

    private static String requireString(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (value instanceof String text) return text;
        throw invalid("Поле " + path + " должно быть строкой");
    }

    private static boolean requireBoolean(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (value instanceof Boolean flag) return flag;
        throw invalid("Поле " + path + " должно быть true или false");
    }

    private static int requireInt(JSONObject object, String key, String path) throws IOException {
        Object value = requireValue(object, key, path);
        if (!(value instanceof Number number)) {
            throw invalid("Поле " + path + " должно быть целым числом");
        }
        double exact = number.doubleValue();
        if (!Double.isFinite(exact) || exact != Math.rint(exact)
                || exact < Integer.MIN_VALUE || exact > Integer.MAX_VALUE) {
            throw invalid("Поле " + path + " должно быть целым числом");
        }
        return (int) exact;
    }

    private static float requireFloat(JSONObject object, String key, String path)
            throws IOException {
        Object value = requireValue(object, key, path);
        if (!(value instanceof Number number)) {
            throw invalid("Поле " + path + " должно быть числом");
        }
        float result = number.floatValue();
        if (!Float.isFinite(result)) throw invalid("Поле " + path + " должно быть числом");
        return result;
    }

    private static int requireRange(String path, int value, int min, int max)
            throws IOException {
        if (value < min || value > max) {
            throw invalid("Поле " + path + " вне диапазона " + min + "…" + max);
        }
        return value;
    }

    private static float requireFloatRange(String path, float value, float min, float max)
            throws IOException {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw invalid("Поле " + path + " вне диапазона " + min + "…" + max);
        }
        return value;
    }

    private static int clampToHundreds(int value) {
        int clamped = clamp(value, PanelConfig.STATUS_TEXT_WEIGHT_MIN,
                PanelConfig.STATUS_TEXT_WEIGHT_MAX);
        return Math.round(clamped / 100f) * 100;
    }

    private static double readableFloat(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static IOException invalid(String message) {
        return new IOException(message);
    }

    private static IOException invalid(String message, Throwable cause) {
        return new IOException(message, cause);
    }
}
