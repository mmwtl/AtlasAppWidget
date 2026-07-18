package mmwtl.atlasappwidget;

final class PanelConfig {
    static final int PANEL_RADIUS_FULLY_ROUNDED = 80;
    static final int HANDLE_LEFT = 0;
    static final int HANDLE_RIGHT = 1;
    static final int HANDLE_TOP = 2;
    static final int HANDLE_BOTTOM = 3;

    final boolean showDragHandle;
    final int dragHandlePosition;
    final boolean showAppLabels;
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
}
