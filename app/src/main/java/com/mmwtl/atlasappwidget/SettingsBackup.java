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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SettingsBackup {
    static final String FILE_NAME = "AtlasAppWidget-settings.json";
    private static final String FORMAT = "atlas-app-widget-settings";
    private static final int SCHEMA_VERSION = 9;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_BACKUP_ICON_BYTES = 128 * 1024;
    private static final int MAX_SELECTED_COMPONENTS = 200;
    private static final int MAX_COMPONENT_LENGTH = 2_048;

    static final class Data {
        final boolean autoStart;
        final boolean showOnlyInAppList;
        final int appUiScaleTenths;
        final int freeformHideThresholdPercent;
        final Integer positionX;
        final Integer positionY;
        final List<String> selectedComponents;
        final List<ShortcutSpec> shortcuts;
        final List<String> climateTransitionComponents;
        final int climateTransitionDurationMs;
        final Map<String, byte[]> customIcons;
        final ContentData content;
        final MovementData movement;
        final SystemStatusData systemStatus;
        final FuelData fuel;
        final GeometryData geometry;
        final AppearanceData appearance;

        Data(boolean autoStart, boolean showOnlyInAppList, int appUiScaleTenths,
                int freeformHideThresholdPercent, Integer positionX, Integer positionY,
                List<String> selectedComponents, List<ShortcutSpec> shortcuts,
                List<String> climateTransitionComponents, int climateTransitionDurationMs,
                Map<String, byte[]> customIcons, ContentData content, MovementData movement,
                SystemStatusData systemStatus, FuelData fuel, GeometryData geometry,
                AppearanceData appearance) throws IOException {
            this.autoStart = autoStart;
            this.showOnlyInAppList = showOnlyInAppList;
            this.appUiScaleTenths = requireRange("settings.uiScaleTenths", appUiScaleTenths,
                    ScaledActivity.MIN_SCALE_TENTHS, ScaledActivity.MAX_SCALE_TENTHS);
            this.freeformHideThresholdPercent = requireRange(
                    "settings.freeformHideThresholdPercent",
                    freeformHideThresholdPercent,
                    WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                    WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT);
            if ((positionX == null) != (positionY == null)) {
                throw invalid("Положение overlay должно содержать обе координаты");
            }
            if (positionX != null && (positionX == Prefs.POSITION_UNSET
                    || positionY == Prefs.POSITION_UNSET)) {
                throw invalid("Недопустимое положение overlay");
            }
            this.positionX = positionX;
            this.positionY = positionY;
            this.shortcuts = validateShortcuts(shortcuts);
            this.selectedComponents = validateSelectedComponents(selectedComponents, this.shortcuts);
            this.climateTransitionComponents = validateClimateTransitionComponents(
                    climateTransitionComponents, this.selectedComponents);
            this.climateTransitionDurationMs = requireRange(
                    "settings.climateTransitionDurationMs", climateTransitionDurationMs,
                    Prefs.CLIMATE_TRANSITION_DURATION_MIN_MS,
                    Prefs.CLIMATE_TRANSITION_DURATION_MAX_MS);
            if (climateTransitionDurationMs % Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS != 0) {
                throw invalid("settings.climateTransitionDurationMs должен быть кратен 50 мс");
            }
            this.customIcons = validateCustomIcons(customIcons, this.shortcuts);
            if (content == null || movement == null || systemStatus == null || fuel == null
                    || geometry == null || appearance == null) {
                throw invalid("В JSON отсутствует раздел настроек");
            }
            this.content = content.validated();
            this.movement = movement.validated();
            this.systemStatus = systemStatus.validated();
            this.fuel = fuel.validated();
            this.geometry = geometry.validated();
            this.appearance = appearance.validated();
        }
    }

    static final class ContentData {
        final boolean showAppLabels;
        final int appLabelTextSizeSp;
        final int appLabelGapDp;
        final boolean appLabelOutlineEnabled;

        ContentData(boolean showAppLabels) {
            this(showAppLabels, PanelConfig.APP_LABEL_TEXT_SIZE_DEFAULT_SP,
                    PanelConfig.APP_LABEL_GAP_DEFAULT_DP, true);
        }

        ContentData(boolean showAppLabels, int appLabelTextSizeSp) {
            this(showAppLabels, appLabelTextSizeSp, PanelConfig.APP_LABEL_GAP_DEFAULT_DP, true);
        }

        ContentData(boolean showAppLabels, int appLabelTextSizeSp, int appLabelGapDp) {
            this(showAppLabels, appLabelTextSizeSp, appLabelGapDp, true);
        }

        ContentData(boolean showAppLabels, int appLabelTextSizeSp, int appLabelGapDp,
                boolean appLabelOutlineEnabled) {
            this.showAppLabels = showAppLabels;
            this.appLabelTextSizeSp = appLabelTextSizeSp;
            this.appLabelGapDp = appLabelGapDp;
            this.appLabelOutlineEnabled = appLabelOutlineEnabled;
        }

        private ContentData validated() throws IOException {
            requireRange("settings.content.appLabelTextSizeSp", appLabelTextSizeSp,
                    PanelConfig.APP_LABEL_TEXT_SIZE_MIN_SP,
                    PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP);
            requireRange("settings.content.appLabelGapDp", appLabelGapDp,
                    PanelConfig.APP_LABEL_GAP_MIN_DP, PanelConfig.APP_LABEL_GAP_MAX_DP);
            return this;
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
        final int widthPixels;
        final int columns;
        final int rows;
        final int iconSizeDp;
        final int iconCornerPercent;
        final int paddingDp;
        final int gapDp;

        GeometryData(int widthPixels, int columns, int rows, int iconSizeDp,
                int iconCornerPercent, int paddingDp, int gapDp) {
            this.widthPixels = widthPixels;
            this.columns = columns;
            this.rows = rows;
            this.iconSizeDp = iconSizeDp;
            this.iconCornerPercent = iconCornerPercent;
            this.paddingDp = paddingDp;
            this.gapDp = gapDp;
        }

        private GeometryData validated() throws IOException {
            requireRange("settings.geometry.widthPixels", widthPixels,
                    PanelConfig.WIDTH_MIN_PIXELS, PanelConfig.WIDTH_MAX_PIXELS);
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
        return capture(null, prefs);
    }

    static Data capture(Context context, Prefs prefs) throws IOException {
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
        List<String> climateComponents = new ArrayList<>();
        List<String> selectedValues = prefs.selectedComponents();
        Set<String> selected = new HashSet<>(selectedValues);
        Set<String> shortcutKeys = new HashSet<>();
        for (ShortcutSpec shortcut : prefs.shortcutCatalog()) shortcutKeys.add(shortcut.key);
        for (String component : prefs.climateTransitionComponents()) {
            boolean validSelected = selected.contains(component)
                    && (!component.startsWith(ShortcutSpec.KEY_PREFIX)
                    || shortcutKeys.contains(component));
            if (validSelected && !AppEntry.FUEL_COMPONENT_KEY.equals(component)) {
                climateComponents.add(component);
            }
        }
        return new Data(
                prefs.getBoolean(Prefs.KEY_AUTO_START, false),
                prefs.getBoolean(Prefs.KEY_SHOW_ONLY_IN_APP_LIST, false),
                clamp(prefs.getInt(Prefs.KEY_APP_UI_SCALE_TENTHS,
                                ScaledActivity.DEFAULT_SCALE_TENTHS),
                        ScaledActivity.MIN_SCALE_TENTHS, ScaledActivity.MAX_SCALE_TENTHS),
                prefs.freeformHideThresholdPercent(),
                positionX,
                positionY,
                selectedValues,
                prefs.shortcutCatalog(),
                climateComponents,
                prefs.climateTransitionDurationMs(),
                captureCustomIcons(context, prefs),
                new ContentData(
                        prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false),
                        clamp(prefs.getInt(Prefs.KEY_APP_LABEL_TEXT_SIZE_SP,
                                        PanelConfig.APP_LABEL_TEXT_SIZE_DEFAULT_SP),
                                PanelConfig.APP_LABEL_TEXT_SIZE_MIN_SP,
                                PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP),
                        clamp(prefs.getInt(Prefs.KEY_APP_LABEL_GAP_DP,
                                        PanelConfig.APP_LABEL_GAP_DEFAULT_DP),
                                PanelConfig.APP_LABEL_GAP_MIN_DP,
                                PanelConfig.APP_LABEL_GAP_MAX_DP),
                        prefs.getBoolean(Prefs.KEY_APP_LABEL_OUTLINE_ENABLED, true)),
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
                        clamp(prefs.getInt(Prefs.KEY_WIDTH_PIXELS,
                                        PanelConfig.WIDTH_DEFAULT_PIXELS),
                                PanelConfig.WIDTH_MIN_PIXELS,
                                PanelConfig.WIDTH_MAX_PIXELS),
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

    private static Map<String, byte[]> captureCustomIcons(Context context, Prefs prefs)
            throws IOException {
        if (context == null) {
            return Map.of();
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        Set<String> shortcutKeys = new HashSet<>();
        for (ShortcutSpec shortcut : prefs.shortcutCatalog()) shortcutKeys.add(shortcut.key);
        for (Map.Entry<String, String> item : prefs.customIcons().entrySet()) {
            if (!shortcutKeys.contains(item.getKey())) continue;
            result.put(item.getKey(), readIcon(context, item.getValue()));
        }
        return result;
    }

    private static byte[] readIcon(Context context, String storedValue) throws IOException {
        File internal = CustomIconStore.resolve(context, storedValue);
        try (InputStream input = internal == null
                ? context.getContentResolver().openInputStream(Uri.parse(storedValue))
                : new FileInputStream(internal)) {
            if (input == null) throw new IOException("Custom icon data is unavailable");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BACKUP_ICON_BYTES) {
                    throw new IOException("Custom icon is too large for settings backup");
                }
                output.write(buffer, 0, count);
            }
            if (total == 0) throw new IOException("Custom icon is empty");
            return output.toByteArray();
        }
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
        return encode(capture(context, prefs), appVersion(context)).getBytes(StandardCharsets.UTF_8);
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
            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            if (displayWidth <= 0) {
                displayWidth = PanelConfig.WIDTH_REFERENCE_PIXELS;
            }
            return decode(new String(output.toByteArray(), StandardCharsets.UTF_8), displayWidth);
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
                    .put("showOnlyInAppList", data.showOnlyInAppList)
                    .put("uiScaleTenths", data.appUiScaleTenths)
                    .put("freeformHideThresholdPercent",
                            data.freeformHideThresholdPercent)
                    .put("selectedComponents", new JSONArray(data.selectedComponents))
                    .put("shortcuts", shortcutArray(data.shortcuts))
                    .put("climateTransitionComponents",
                            new JSONArray(data.climateTransitionComponents))
                    .put("climateTransitionDurationMs", data.climateTransitionDurationMs)
                    .put("customIcons", customIconObject(data.customIcons))
                    .put("content", new JSONObject()
                            .put("showAppLabels", data.content.showAppLabels)
                            .put("appLabelTextSizeSp", data.content.appLabelTextSizeSp)
                            .put("appLabelGapDp", data.content.appLabelGapDp)
                            .put("appLabelOutlineEnabled",
                                    data.content.appLabelOutlineEnabled))
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
                            .put("widthPixels", data.geometry.widthPixels)
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
            String encoded = root.toString(2) + '\n';
            if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                throw invalid("JSON настроек с иконками больше 256 КБ");
            }
            return encoded;
        } catch (JSONException error) {
            throw new IOException("Не удалось сформировать JSON настроек", error);
        }
    }

    static Data decode(String json) throws IOException {
        return decode(json, PanelConfig.WIDTH_REFERENCE_PIXELS);
    }

    private static Data decode(String json, int legacyDisplayWidthPixels) throws IOException {
        try {
            if (json != null && !json.isEmpty() && json.charAt(0) == '\ufeff') {
                json = json.substring(1);
            }
            JSONObject root = new JSONObject(json == null ? "" : json);
            if (!FORMAT.equals(requireString(root, "format", "format"))) {
                throw invalid("Это не файл настроек Atlas App Widget");
            }
            int version = requireInt(root, "schemaVersion", "schemaVersion");
            if (version < 1 || version > SCHEMA_VERSION) {
                throw invalid("Неподдерживаемая версия JSON: " + version);
            }
            JSONObject settings = requireObject(root, "settings", "settings");
            JSONObject content = requireObject(settings, "content", "settings.content");
            JSONObject movement = requireObject(settings, "movement", "settings.movement");
            JSONObject status = requireObject(settings, "systemStatus", "settings.systemStatus");
            JSONObject fuel = requireObject(settings, "fuel", "settings.fuel");
            JSONObject geometry = requireObject(settings, "geometry", "settings.geometry");
            JSONObject appearance = requireObject(settings, "appearance", "settings.appearance");
            if (version >= 5 && !content.has("appLabelTextSizeSp")) {
                throw invalid("В JSON отсутствует размер названий приложений");
            }
            if (version >= 6 && !content.has("appLabelGapDp")) {
                throw invalid("В JSON отсутствует интервал до названий приложений");
            }
            if (version >= 7 && !content.has("appLabelOutlineEnabled")) {
                throw invalid("В JSON отсутствует настройка обводки названий приложений");
            }
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
            List<ShortcutSpec> shortcuts = version >= 2 && settings.has("shortcuts")
                    ? parseShortcuts(settings) : List.of();
            Map<String, byte[]> customIcons = version >= 3 && settings.has("customIcons")
                    ? parseCustomIcons(settings) : Map.of();
            List<String> selectedComponents = requireStringList(settings, "selectedComponents",
                    "settings.selectedComponents");
            boolean legacyClimateEnabled = (settings.has("useLaunchProxy")
                    && requireBoolean(settings, "useLaunchProxy", "settings.useLaunchProxy"))
                    || (settings.has("useDiagnosticLaunchActivity")
                    && requireBoolean(settings, "useDiagnosticLaunchActivity",
                    "settings.useDiagnosticLaunchActivity"));
            List<String> climateComponents;
            int climateDuration;
            if (version >= 4) {
                if (!settings.has("climateTransitionComponents")
                        || !settings.has("climateTransitionDurationMs")) {
                    throw invalid("В JSON отсутствуют настройки скрытия климат-панели");
                }
                climateComponents = parseClimateTransitionComponents(settings,
                        selectedComponents);
                climateDuration = requireInt(settings, "climateTransitionDurationMs",
                        "settings.climateTransitionDurationMs");
                if (version < 8) {
                    requireRange("settings.climateTransitionDurationMs", climateDuration,
                            10, 1_000);
                    climateDuration = Prefs.normalizeClimateTransitionDuration(climateDuration);
                }
            } else {
                climateComponents = legacyClimateEnabled
                        ? selectedNonFuelComponents(selectedComponents) : List.of();
                climateDuration = Prefs.CLIMATE_TRANSITION_DURATION_DEFAULT_MS;
            }
            return new Data(
                    requireBoolean(settings, "autoStart", "settings.autoStart"),
                    settings.has("showOnlyInAppList")
                            && requireBoolean(settings, "showOnlyInAppList",
                                    "settings.showOnlyInAppList"),
                    requireInt(settings, "uiScaleTenths", "settings.uiScaleTenths"),
                    settings.has("freeformHideThresholdPercent")
                            ? requireInt(settings, "freeformHideThresholdPercent",
                                    "settings.freeformHideThresholdPercent")
                            : WindowVisibilityPolicy.DEFAULT_HIDE_THRESHOLD_PERCENT,
                    x,
                    y,
                    selectedComponents,
                    shortcuts,
                    climateComponents,
                    climateDuration,
                    customIcons,
                    new ContentData(requireBoolean(content, "showAppLabels",
                            "settings.content.showAppLabels"),
                            version >= 5
                                    ? requireInt(content, "appLabelTextSizeSp",
                                    "settings.content.appLabelTextSizeSp")
                                    : PanelConfig.APP_LABEL_TEXT_SIZE_DEFAULT_SP,
                            version >= 6
                                    ? requireInt(content, "appLabelGapDp",
                                    "settings.content.appLabelGapDp")
                                    : PanelConfig.APP_LABEL_GAP_DEFAULT_DP,
                            version >= 7
                                    ? requireBoolean(content, "appLabelOutlineEnabled",
                                    "settings.content.appLabelOutlineEnabled")
                                    : true),
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
                            decodeWidthPixels(geometry, version, legacyDisplayWidthPixels),
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

    private static int decodeWidthPixels(JSONObject geometry, int version,
            int legacyDisplayWidthPixels) throws IOException {
        if (version >= 9 || !geometry.has("widthPercent")) {
            return requireInt(geometry, "widthPixels", "settings.geometry.widthPixels");
        }
        int widthPercent = requireInt(geometry, "widthPercent",
                "settings.geometry.widthPercent");
        requireRange("settings.geometry.widthPercent", widthPercent, 25, 100);
        return PanelConfig.widthPixelsFromLegacyPercent(
                widthPercent, legacyDisplayWidthPixels);
    }

    private static JSONArray shortcutArray(List<ShortcutSpec> shortcuts) throws JSONException {
        JSONArray result = new JSONArray();
        for (ShortcutSpec shortcut : shortcuts) {
            result.put(new JSONObject()
                    .put("key", shortcut.key)
                    .put("title", shortcut.title)
                    .put("intentUri", shortcut.intentUri)
                    .put("targetComponent", shortcut.targetComponent));
        }
        return result;
    }

    private static JSONObject customIconObject(Map<String, byte[]> icons) throws JSONException {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, byte[]> item : icons.entrySet()) {
            result.put(item.getKey(), Base64.getEncoder().encodeToString(item.getValue()));
        }
        return result;
    }

    private static List<ShortcutSpec> parseShortcuts(JSONObject settings) throws IOException {
        Object value = requireValue(settings, "shortcuts", "settings.shortcuts");
        if (!(value instanceof JSONArray array)) {
            throw invalid("Поле settings.shortcuts должно быть массивом");
        }
        if (array.length() > ShortcutSpec.MAX_SHORTCUTS) {
            throw invalid("Слишком много ярлыков");
        }
        ArrayList<ShortcutSpec> result = new ArrayList<>(array.length());
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < array.length(); index++) {
            try {
                JSONObject item = array.getJSONObject(index);
                ShortcutSpec shortcut = new ShortcutSpec(
                        item.getString("key"), item.getString("title"),
                        item.getString("intentUri"), item.getString("targetComponent"));
                if (!keys.add(shortcut.key)) {
                    throw invalid("Повторяющийся ярлык в settings.shortcuts");
                }
                result.add(shortcut);
            } catch (JSONException | IllegalArgumentException error) {
                throw invalid("Некорректный ярлык в settings.shortcuts[" + index + "]", error);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> parseClimateTransitionComponents(JSONObject settings,
            List<String> selectedComponents) throws IOException {
        Object value = requireValue(settings, "climateTransitionComponents",
                "settings.climateTransitionComponents");
        if (!(value instanceof JSONArray array)) {
            throw invalid("Поле settings.climateTransitionComponents должно быть массивом");
        }
        ArrayList<String> result = new ArrayList<>(array.length());
        Set<String> selected = new HashSet<>(selectedComponents);
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < array.length(); index++) {
            Object item;
            try {
                item = array.get(index);
            } catch (JSONException error) {
                throw invalid("Не удалось прочитать settings.climateTransitionComponents["
                        + index + "]", error);
            }
            if (!(item instanceof String key) || key.isEmpty()
                    || key.length() > MAX_COMPONENT_LENGTH
                    || AppEntry.FUEL_COMPONENT_KEY.equals(key)
                    || !selected.contains(key) || !unique.add(key)) {
                throw invalid("Некорректный элемент в settings.climateTransitionComponents["
                        + index + "]");
            }
            result.add(key);
        }
        if (result.size() > MAX_SELECTED_COMPONENTS) {
            throw invalid("Слишком много элементов скрытия климат-панели");
        }
        return List.copyOf(result);
    }

    private static List<String> selectedNonFuelComponents(List<String> selectedComponents) {
        ArrayList<String> result = new ArrayList<>();
        for (String component : selectedComponents) {
            if (!AppEntry.FUEL_COMPONENT_KEY.equals(component)) {
                result.add(component);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, byte[]> parseCustomIcons(JSONObject settings) throws IOException {
        Object value = requireValue(settings, "customIcons", "settings.customIcons");
        if (!(value instanceof JSONObject object)) {
            throw invalid("Поле settings.customIcons должно быть объектом");
        }
        if (object.length() > MAX_SELECTED_COMPONENTS) {
            throw invalid("Слишком много пользовательских иконок");
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || key.isEmpty() || key.length() > MAX_COMPONENT_LENGTH) {
                throw invalid("Некорректный ключ в settings.customIcons");
            }
            Object encoded;
            try {
                encoded = object.get(key);
            } catch (JSONException error) {
                throw invalid("Не удалось прочитать settings.customIcons." + key, error);
            }
            if (!(encoded instanceof String text) || text.isEmpty()) {
                throw invalid("Иконка settings.customIcons." + key
                        + " должна быть base64-строкой");
            }
            final byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(text);
            } catch (IllegalArgumentException error) {
                throw invalid("Некорректная base64-иконка settings.customIcons." + key, error);
            }
            if (bytes.length == 0 || bytes.length > MAX_BACKUP_ICON_BYTES) {
                throw invalid("Иконка settings.customIcons." + key + " слишком большая");
            }
            result.put(key, bytes);
        }
        return Map.copyOf(result);
    }

    private static Map<String, byte[]> validateCustomIcons(Map<String, byte[]> customIcons,
            List<ShortcutSpec> shortcuts) throws IOException {
        if (customIcons == null || customIcons.size() > MAX_SELECTED_COMPONENTS) {
            throw invalid("Некорректный каталог пользовательских иконок");
        }
        Set<String> shortcutKeys = new HashSet<>();
        for (ShortcutSpec shortcut : shortcuts) shortcutKeys.add(shortcut.key);
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> item : customIcons.entrySet()) {
            if (!shortcutKeys.contains(item.getKey())) {
                throw invalid("Пользовательская иконка не относится к ярлыку");
            }
            if (item.getValue() == null || item.getValue().length == 0
                    || item.getValue().length > MAX_BACKUP_ICON_BYTES) {
                throw invalid("Некорректные данные пользовательской иконки");
            }
            result.put(item.getKey(), item.getValue().clone());
        }
        return Map.copyOf(result);
    }

    private static List<ShortcutSpec> validateShortcuts(List<ShortcutSpec> shortcuts)
            throws IOException {
        if (shortcuts == null || shortcuts.size() > ShortcutSpec.MAX_SHORTCUTS) {
            throw invalid("Некорректный каталог ярлыков");
        }
        ArrayList<ShortcutSpec> result = new ArrayList<>(shortcuts.size());
        Set<String> keys = new HashSet<>();
        for (ShortcutSpec shortcut : shortcuts) {
            if (shortcut == null || !keys.add(shortcut.key)) {
                throw invalid("Повторяющийся ярлык");
            }
            result.add(shortcut);
        }
        return List.copyOf(result);
    }

    private static List<String> validateClimateTransitionComponents(List<String> components,
            List<String> selectedComponents) throws IOException {
        if (components == null || components.size() > MAX_SELECTED_COMPONENTS) {
            throw invalid("Некорректный список элементов скрытия климат-панели");
        }
        Set<String> selected = new HashSet<>(selectedComponents);
        Set<String> unique = new HashSet<>();
        ArrayList<String> result = new ArrayList<>(components.size());
        for (String component : components) {
            if (component == null || component.isEmpty()
                    || component.length() > MAX_COMPONENT_LENGTH
                    || AppEntry.FUEL_COMPONENT_KEY.equals(component)
                    || !selected.contains(component) || !unique.add(component)) {
                throw invalid("Элемент скрытия климат-панели должен быть выбранным non-fuel элементом");
            }
            result.add(component);
        }
        return List.copyOf(result);
    }

    private static List<String> validateSelectedComponents(List<String> components,
            List<ShortcutSpec> shortcuts)
            throws IOException {
        if (components == null) throw invalid("Не указан список выбранных элементов");
        if (components.size() > MAX_SELECTED_COMPONENTS) {
            throw invalid("Слишком много выбранных элементов");
        }
        Set<String> unique = new HashSet<>();
        Set<String> shortcutKeys = new HashSet<>();
        for (ShortcutSpec shortcut : shortcuts) shortcutKeys.add(shortcut.key);
        ArrayList<String> result = new ArrayList<>(components.size());
        for (String component : components) {
            if (component == null || component.isEmpty()
                    || component.length() > MAX_COMPONENT_LENGTH) {
                throw invalid("Некорректный элемент в settings.selectedComponents");
            }
            if (!unique.add(component)) {
                throw invalid("Повторяющийся элемент в settings.selectedComponents");
            }
            if (component.startsWith(ShortcutSpec.KEY_PREFIX)
                    && !shortcutKeys.contains(component)) {
                continue;
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
