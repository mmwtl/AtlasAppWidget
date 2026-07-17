package mmwtl.atlaswidget;

final class PanelConfig {
    final boolean showDragHandle;
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
    final int panelShape;
    final int panelRadiusDp;

    PanelConfig(Prefs prefs) {
        showDragHandle = prefs.getBoolean(Prefs.KEY_SHOW_DRAG_HANDLE, true);
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
        panelShape = prefs.getInt(Prefs.KEY_PANEL_SHAPE, 1);
        panelRadiusDp = prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, 8);
    }
}
