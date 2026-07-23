package com.mmwtl.atlasappwidget;

final class PanelConfig {
    static final int PANEL_RADIUS_FULLY_ROUNDED = 80;
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

    final boolean showDragHandle;
    final int dragHandlePosition;
    final boolean showAppLabels;
    final boolean showSystemStatus;
    final int systemStatusPosition;
    final int systemStatusLineHeightDp;
    final int systemStatusTextSizeSp;
    final int systemStatusTextWeight;
    final int widthPercent;
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

    PanelConfig(Prefs prefs) {
        showDragHandle = prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true);
        dragHandlePosition = Math.max(HANDLE_LEFT, Math.min(HANDLE_BOTTOM,
                prefs.getInt(Prefs.KEY_DRAG_HANDLE_POSITION, HANDLE_LEFT)));
        showAppLabels = prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false);
        showSystemStatus = prefs.getBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false);
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
        widthPercent = prefs.getInt(Prefs.KEY_WIDTH_PERCENT, 72);
        columns = prefs.getInt(Prefs.KEY_COLUMNS, 5);
        rows = prefs.getInt(Prefs.KEY_ROWS, 1);
        iconSizeDp = prefs.getInt(Prefs.KEY_ICON_SIZE_DP, 72);
        iconCornerPercent = prefs.getInt(Prefs.KEY_ICON_CORNER_PERCENT, 12);
        paddingDp = prefs.getInt(Prefs.KEY_PADDING_DP, 14);
        gapDp = prefs.getInt(Prefs.KEY_GAP_DP, 12);
        backgroundColor = prefs.getInt(Prefs.KEY_BACKGROUND_COLOR, 0xFF262626);
        backgroundAlpha = prefs.getInt(Prefs.KEY_BACKGROUND_ALPHA, 235);
        backgroundStrokeEnabled = prefs.getBoolean(Prefs.KEY_BACKGROUND_STROKE_ENABLED, false);
        backgroundStrokeWidthDp = prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_WIDTH_DP, 2);
        backgroundStrokeAlpha = prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_ALPHA, 200);
        backgroundStrokeColor = prefs.getInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, 0xFF7893A0);
        panelRadiusDp = prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, 8);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
