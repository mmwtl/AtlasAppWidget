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
        int gridHeight = showStatus && !sideStatus
                ? height - statusSize - Math.max(0, statusGap) : height;
        // Very short hosts must fit the labels too, without dropping configured rows.
        float labelScale = labelHeight + labelGap == 0 ? 1f : Math.min(1f,
                Math.max(0, gridHeight / rows - 1) / (float) (labelHeight + labelGap));
        PanelLayout layout = PanelLayout.calculate(width, height, width, 1, 1,
                iconSize, (int) (labelHeight * labelScale), (int) (labelGap * labelScale),
                rows, columns, padding, gap, false, false, 0, 0,
                showStatus, sideStatus, statusSize, statusGap, 0, height);
        return new WidgetGrid(columns, rows, layout, labelScale);
    }

    int capacity() {
        return columns * rows;
    }
}
