package com.mmwtl.atlasappwidget;

/** Grid capacity follows the size supplied by the widget host, in dp. */
final class WidgetGrid {
    final int columns;
    final int rows;

    private WidgetGrid(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    static WidgetGrid forSize(int widthDp, int heightDp, boolean showLabels) {
        int columns = Math.max(1, Math.min(8, (widthDp - 16) / 72));
        int rows = Math.max(1, Math.min(6,
                (heightDp - 16) / (showLabels ? 88 : 72)));
        return new WidgetGrid(columns, rows);
    }

    int capacity() {
        return columns * rows;
    }
}
