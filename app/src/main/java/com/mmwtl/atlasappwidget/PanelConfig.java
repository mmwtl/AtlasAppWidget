package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.graphics.Paint;
import android.util.TypedValue;

final class PanelConfig {
    static final int PANEL_RADIUS_FULLY_ROUNDED = 80;
    /** Screen dp; matches the corner radius of the stock OneOS cards. */
    static final int PANEL_RADIUS_DEFAULT_DP = 24;
    /** Default before 2.0.1; kept for installs that never moved the slider. */
    static final int PANEL_RADIUS_LEGACY_DEFAULT_DP = 8;
    /** A color left to the theme, stored instead of a picked color, which is always opaque. */
    static final int THEME_COLOR = 0;
    /** Overlay panel color while it is left to the theme; earlier versions stored it as is. */
    static final int GRAPHITE = 0xFF262626;
    /** The stroke color left to the theme; earlier versions stored it as is. */
    static final int STROKE_ACCENT = 0xFF7893A0;
    /** Launcher surfaces, the same as widget_surface in values and values-night. */
    static final int DAY_SURFACE = 0xFFEDEEF0;
    static final int NIGHT_SURFACE = 0xFF232528;
    /** Launcher text, the same as widget_text in values and values-night. */
    static final int DAY_TEXT = 0xFF202124;
    static final int NIGHT_TEXT = 0xFFF1F2F4;
    /** The widget's theme: the head unit's day or night mode, or one of them fixed. */
    static final int THEME_AUTO = 0;
    static final int THEME_DAY = 1;
    static final int THEME_NIGHT = 2;
    static final int HANDLE_LEFT = 0;
    static final int HANDLE_RIGHT = 1;
    static final int HANDLE_TOP = 2;
    static final int HANDLE_BOTTOM = 3;
    static final int STATUS_TOP = 0;
    static final int STATUS_BOTTOM = 1;
    static final int STATUS_LEFT = 2;
    static final int STATUS_RIGHT = 3;
    static final int STATUS_LINE_HEIGHT_MIN_DP = 2;
    static final int STATUS_LINE_HEIGHT_MAX_DP = 20;
    static final int STATUS_LINE_HEIGHT_DEFAULT_DP = 6;
    static final int STATUS_TEXT_SIZE_MIN_SP = 8;
    static final int STATUS_TEXT_SIZE_MAX_SP = 24;
    static final int STATUS_TEXT_SIZE_DEFAULT_SP = 12;
    static final int STATUS_TEXT_WEIGHT_MIN = 100;
    static final int STATUS_TEXT_WEIGHT_MAX = 900;
    static final int STATUS_TEXT_WEIGHT_DEFAULT = 700;
    static final int APP_LABEL_TEXT_SIZE_MIN_SP = 8;
    static final int APP_LABEL_TEXT_SIZE_MAX_SP = 24;
    static final int APP_LABEL_TEXT_SIZE_DEFAULT_SP = 12;
    static final int APP_LABEL_GAP_MIN_DP = 0;
    static final int APP_LABEL_GAP_MAX_DP = 40;
    static final int APP_LABEL_GAP_DEFAULT_DP = 4;
    static final int ONEOS_ICON_SIZE_DP = 104;
    static final int ONEOS_ICON_CORNER_PERCENT = 23;
    static final int ONEOS_APP_LABEL_TEXT_SIZE_SP = 24;
    static final int ONEOS_APP_LABEL_GAP_DP = 16;
    static final int WIDTH_REFERENCE_PIXELS = 1440;
    static final int WIDTH_MIN_PIXELS = 1;
    static final int WIDTH_MAX_PIXELS = WIDTH_REFERENCE_PIXELS;
    static final int WIDTH_DEFAULT_PIXELS = Math.round(
            WIDTH_REFERENCE_PIXELS * 72 / 100f);
    static final int HEIGHT_REFERENCE_PIXELS = 1920;
    static final int HEIGHT_MIN_PIXELS = 1;
    static final int HEIGHT_MAX_PIXELS = HEIGHT_REFERENCE_PIXELS;

    final boolean showDragHandle;
    final int dragHandlePosition;
    final boolean showAppLabels;
    final int appLabelTextSizeSp;
    final int appLabelGapDp;
    final boolean appLabelOutlineEnabled;
    final boolean showSystemStatus;
    final boolean showCpuStatus;
    final boolean showRamStatus;
    final boolean showFuelStatus;
    final int systemStatusPosition;
    final int systemStatusLineHeightDp;
    final int systemStatusTextSizeSp;
    final int systemStatusTextWeight;
    final int widthPixels;
    final boolean manualHeightEnabled;
    final int heightPixels;
    final int columns;
    final int rows;
    final int iconSizeDp;
    final int iconCornerPercent;
    final int paddingDp;
    final int gapDp;
    final int backgroundColor;
    final int backgroundAlpha;
    final boolean backgroundStrokeEnabled;
    final int backgroundStrokeWidthDp;
    final int backgroundStrokeAlpha;
    final int backgroundStrokeColor;
    final int panelRadiusDp;
    /** The widget's day colors; the two above are the night ones and the overlay's. */
    final int dayBackgroundColor;
    final int dayBackgroundStrokeColor;
    /** Opacity of icons, labels and the status strip, 0..255. */
    final int contentAlpha;
    /** {@link #THEME_AUTO}, {@link #THEME_DAY} or {@link #THEME_NIGHT}; the widget's only. */
    final int themeMode;

    PanelConfig(Prefs prefs) {
        showDragHandle = prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true);
        dragHandlePosition = Math.max(HANDLE_LEFT, Math.min(HANDLE_BOTTOM,
                prefs.getInt(Prefs.KEY_DRAG_HANDLE_POSITION, HANDLE_LEFT)));
        showAppLabels = prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false);
        appLabelTextSizeSp = clamp(
                prefs.getInt(Prefs.KEY_APP_LABEL_TEXT_SIZE_SP, APP_LABEL_TEXT_SIZE_DEFAULT_SP),
                APP_LABEL_TEXT_SIZE_MIN_SP,
                APP_LABEL_TEXT_SIZE_MAX_SP
        );
        appLabelGapDp = clamp(
                prefs.getInt(Prefs.KEY_APP_LABEL_GAP_DP, APP_LABEL_GAP_DEFAULT_DP),
                APP_LABEL_GAP_MIN_DP,
                APP_LABEL_GAP_MAX_DP
        );
        appLabelOutlineEnabled = prefs.getBoolean(
                Prefs.KEY_APP_LABEL_OUTLINE_ENABLED, true);
        showCpuStatus = prefs.getBoolean(Prefs.KEY_SHOW_CPU_STATUS, true);
        showRamStatus = prefs.getBoolean(Prefs.KEY_SHOW_RAM_STATUS, true);
        showFuelStatus = prefs.getBoolean(Prefs.KEY_SHOW_FUEL_STATUS, true);
        showSystemStatus = prefs.getBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false)
                && systemStatusMetricCount() > 0;
        systemStatusPosition = Math.max(STATUS_TOP, Math.min(STATUS_RIGHT,
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_POSITION, STATUS_BOTTOM)));
        systemStatusLineHeightDp = clamp(
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_LINE_HEIGHT_DP,
                        STATUS_LINE_HEIGHT_DEFAULT_DP),
                STATUS_LINE_HEIGHT_MIN_DP,
                STATUS_LINE_HEIGHT_MAX_DP
        );
        systemStatusTextSizeSp = clamp(
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_TEXT_SIZE_SP,
                        STATUS_TEXT_SIZE_DEFAULT_SP),
                STATUS_TEXT_SIZE_MIN_SP,
                STATUS_TEXT_SIZE_MAX_SP
        );
        systemStatusTextWeight = clamp(
                prefs.getInt(Prefs.KEY_SYSTEM_STATUS_TEXT_WEIGHT,
                        STATUS_TEXT_WEIGHT_DEFAULT),
                STATUS_TEXT_WEIGHT_MIN,
                STATUS_TEXT_WEIGHT_MAX
        );
        widthPixels = clamp(
                prefs.getInt(Prefs.KEY_WIDTH_PIXELS, WIDTH_DEFAULT_PIXELS),
                WIDTH_MIN_PIXELS,
                WIDTH_MAX_PIXELS
        );
        manualHeightEnabled = prefs.getBoolean(Prefs.KEY_MANUAL_HEIGHT_ENABLED, false);
        heightPixels = clamp(
                prefs.getInt(Prefs.KEY_HEIGHT_PIXELS, HEIGHT_REFERENCE_PIXELS),
                HEIGHT_MIN_PIXELS,
                HEIGHT_MAX_PIXELS
        );
        columns = prefs.getInt(Prefs.KEY_COLUMNS, 5);
        rows = prefs.getInt(Prefs.KEY_ROWS, 1);
        iconSizeDp = prefs.getInt(Prefs.KEY_ICON_SIZE_DP, 72);
        iconCornerPercent = prefs.getInt(Prefs.KEY_ICON_CORNER_PERCENT, 12);
        paddingDp = prefs.getInt(Prefs.KEY_PADDING_DP, 14);
        gapDp = prefs.getInt(Prefs.KEY_GAP_DP, 12);
        backgroundColor = prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, THEME_COLOR);
        backgroundAlpha = prefs.getInt(Prefs.KEY_BACKGROUND_ALPHA, 235);
        backgroundStrokeEnabled = prefs.getBoolean(Prefs.KEY_BACKGROUND_STROKE_ENABLED, false);
        backgroundStrokeWidthDp = prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, 2);
        backgroundStrokeAlpha = prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_ALPHA, 200);
        backgroundStrokeColor = prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, THEME_COLOR);
        panelRadiusDp = prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, PANEL_RADIUS_DEFAULT_DP);
        dayBackgroundColor = prefs.getInt(Prefs.KEY_DAY_BACKGROUND_COLOR, THEME_COLOR);
        dayBackgroundStrokeColor = prefs.getInt(Prefs.KEY_DAY_BACKGROUND_STROKE_COLOR,
                THEME_COLOR);
        contentAlpha = Math.max(0, Math.min(255, prefs.getInt(Prefs.KEY_CONTENT_ALPHA, 255)));
        themeMode = Math.max(THEME_AUTO, Math.min(THEME_NIGHT,
                prefs.getInt(Prefs.KEY_THEME_MODE, THEME_AUTO)));
    }

    boolean fixedTheme() {
        return themeMode != THEME_AUTO;
    }

    /** The mode whose colors the widget shows while the head unit is in {@code night} mode. */
    private boolean shownNight(boolean night) {
        return fixedTheme() ? themeMode == THEME_NIGHT : night;
    }

    /**
     * The widget's panel color in a head unit mode. A fixed theme shows its own colors in both
     * modes, with a color left to the theme resolved: the host would resolve it by its own mode.
     */
    int widgetBackground(boolean night) {
        boolean shown = shownNight(night);
        int color = shown ? backgroundColor : dayBackgroundColor;
        return fixedTheme() ? surface(color, shown) : color;
    }

    int widgetStroke(boolean night) {
        return shownNight(night) ? backgroundStrokeColor : dayBackgroundStrokeColor;
    }

    /** The color an earlier version stored for what is now left to the theme. */
    static int fromLegacyColor(int color, int legacyDefault) {
        return (color & 0x00FFFFFF) == (legacyDefault & 0x00FFFFFF) ? THEME_COLOR : color;
    }

    /** The panel color of a mode, with a color left to the theme resolved. */
    static int surface(int color, boolean night) {
        return color != THEME_COLOR ? color : night ? NIGHT_SURFACE : DAY_SURFACE;
    }

    static int stroke(int color) {
        return color != THEME_COLOR ? color : STROKE_ACCENT;
    }

    int overlayBackgroundColor() {
        return backgroundColor != THEME_COLOR ? backgroundColor : GRAPHITE;
    }

    /** Whether light text contrasts better than dark text with this color. */
    static boolean isDark(int color) {
        // Black and white text have equal WCAG contrast at this luminance.
        return luminance(color) < 0.179;
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 0xFF) + 0.7152 * linear((color >> 8) & 0xFF)
                + 0.0722 * linear(color & 0xFF);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** How the widget's labels and status strip read on its day and night panels. */
    enum TextMode {
        /** Dark by day and light at night: the host tints a mask with widget_text. */
        THEME,
        /** Light by day and dark at night: the host tints a mask with widget_text_inverse. */
        INVERSE,
        /** Light in both modes, drawn into the bitmaps. */
        LIGHT,
        /** Dark in both modes, drawn into the bitmaps. */
        DARK;

        boolean masked() {
            return this == THEME || this == INVERSE;
        }
    }

    TextMode textMode() {
        boolean lightByDay = isDark(surface(widgetBackground(false), false));
        boolean lightAtNight = isDark(surface(widgetBackground(true), true));
        return lightByDay == lightAtNight
                ? lightByDay ? TextMode.LIGHT : TextMode.DARK
                : lightAtNight ? TextMode.THEME : TextMode.INVERSE;
    }

    int systemStatusMetricCount() {
        int count = 0;
        if (showCpuStatus) {
            count++;
        }
        if (showRamStatus) {
            count++;
        }
        if (showFuelStatus) {
            count++;
        }
        return count;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static int widthPixelsFromLegacyPercent(int widthPercent, int availableWidthPixels) {
        int safeWidth = Math.max(1, availableWidthPixels);
        return clamp(
                Math.round(safeWidth * widthPercent / 100f),
                WIDTH_MIN_PIXELS,
                WIDTH_MAX_PIXELS
        );
    }

    static int appLabelHeightPixels(Context context, int textSizeSp) {
        float textSizePixels = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.getResources().getDisplayMetrics()
        );
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSizePixels);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        int fontHeight = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent);
        int shadowRadius = Ui.dp(context, 2);
        int shadowOffset = Ui.dp(context, 1);
        int measuredHeight = fontHeight + shadowRadius * 2 + shadowOffset;
        return Math.max(Ui.dp(context, 20), measuredHeight);
    }
}
