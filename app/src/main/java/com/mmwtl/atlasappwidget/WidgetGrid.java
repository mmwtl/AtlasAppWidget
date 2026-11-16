package com.mmwtl.atlasappwidget;

/** Widget geometry in physical pixels; the host owns bounds, preferences own the grid. */
final class WidgetGrid {
    final int columns;
    final int rows;
    final PanelLayout layout;
    final float labelScale;

    private WidgetGrid(int columns, int rows, PanelLayout layout, float labelScale) {
        this.columns = columns;
        this.rows = rows;
        this.layout = layout;
        this.labelScale = labelScale;
    }

    static WidgetGrid forSize(int width, int height, int columns, int rows,
            int iconSize, int padding, int gap, int labelHeight, int labelGap) {
        return forSize(width, height, columns, rows, iconSize, padding, gap,
                labelHeight, labelGap, false, 0, 0);
    }

    /** {@code statusSize} of zero means no system status strip beside the grid. */
    static WidgetGrid forSize(int width, int height, int columns, int rows,
            int iconSize, int padding, int gap, int labelHeight, int labelGap,
            boolean sideStatus, int statusSize, int statusGap) {
        columns = Math.max(1, Math.min(10, columns));
        rows = Math.max(1, Math.min(4, rows));
        boolean showStatus = statusSize > 0;
        int statusSpace = showStatus ? statusSize + Math.max(0, statusGap) : 0;
        int gridWidth = Math.max(1, width - (sideStatus ? statusSpace : 0));
        int gridHeight = Math.max(1, height - (sideStatus ? 0 : statusSpace));
        // Preferences describe proportions. The host size determines their actual pixel size,
        // so expanding a widget grows its contents as well as its background.
        int naturalWidth = columns * iconSize + (columns - 1) * gap + padding * 2;
        int naturalHeight = rows * (iconSize + labelHeight + labelGap)
                + (rows - 1) * gap + padding * 2;
        float scale = Math.min(gridWidth / (float) Math.max(1, naturalWidth),
                gridHeight / (float) Math.max(1, naturalHeight));
        float labelScale = scale;
        PanelLayout layout = PanelLayout.calculate(width, height, width, 1, 1,
                Math.max(1, (int) (iconSize * scale)), (int) (labelHeight * scale),
                (int) (labelGap * scale), rows, columns, (int) (padding * scale),
                (int) (gap * scale), false, false, 0, 0,
                showStatus, sideStatus, statusSize, statusGap, 0, height);
        return new WidgetGrid(columns, rows, layout, labelScale);
    }

    int capacity() {
        return columns * rows;
    }
}
