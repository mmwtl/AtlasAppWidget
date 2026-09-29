package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WidgetGridTest {
    @Test
    public void resizingPreservesConfiguredCapacity() {
        WidgetGrid small = grid(140, 72, 5, 2, 104, 14, 12, 32, 16);
        WidgetGrid wide = grid(900, 400, 5, 2, 104, 14, 12, 32, 16);
        assertEquals(5, small.columns);
        assertEquals(2, small.rows);
        assertEquals(10, small.capacity());
        assertEquals(small.capacity(), wide.capacity());
        assertTrue(small.layout.iconSize < wide.layout.iconSize);
        assertFits(small, 140, 72);
    }

    @Test
    public void roomyHostPreservesLargeIconsPaddingAndGaps() {
        WidgetGrid grid = grid(1200, 800, 3, 2, 240, 40, 36, 40, 24);
        assertEquals(240, grid.layout.iconSize);
        assertEquals(40, grid.layout.padding);
        assertEquals(36, grid.layout.horizontalGap);
        assertEquals(36, grid.layout.verticalGap);
        assertEquals(240 + 40 + 24, grid.layout.cellHeight);
        assertEquals(1f, grid.labelScale, 0f);
        assertFits(grid, 1200, 800);
    }

    @Test
    public void shortHostFitsLargeLabelsWithoutDroppingRows() {
        WidgetGrid grid = grid(200, 60, 10, 4, 104, 40, 40, 40, 40);
        assertEquals(40, grid.capacity());
        assertTrue(grid.labelScale < 1f);
        assertFits(grid, 200, 60);
    }

    @Test
    public void labelsAndTheirGapReduceAvailableIconHeight() {
        WidgetGrid plain = grid(500, 120, 5, 1, 104, 10, 10, 0, 0);
        WidgetGrid labels = grid(500, 120, 5, 1, 104, 10, 10, 32, 16);
        assertTrue(labels.layout.iconSize < plain.layout.iconSize);
        assertEquals(labels.layout.iconSize + 48, labels.layout.cellHeight);
    }

    @Test
    public void statusStripTakesSpaceFromTheGridOnItsSide() {
        WidgetGrid plain = grid(900, 300, 5, 2, 104, 10, 10, 0, 0);
        WidgetGrid bottom = WidgetGrid.forSize(900, 300, 5, 2, 104, 10, 10, 0, 0,
                false, 60, 16);
        WidgetGrid side = WidgetGrid.forSize(900, 300, 5, 2, 104, 10, 10, 0, 0,
                true, 40, 16);
        assertEquals(plain.layout.gridWidth, bottom.layout.gridWidth);
        assertTrue(bottom.layout.gridHeight + 60 + 16 + bottom.layout.padding * 2 <= 300);
        assertEquals(plain.layout.gridWidth - 56, side.layout.gridWidth);
        assertTrue(bottom.layout.iconSize < plain.layout.iconSize);
    }

    private static WidgetGrid grid(int width, int height, int columns, int rows,
            int icon, int padding, int gap, int labelHeight, int labelGap) {
        return WidgetGrid.forSize(width, height, columns, rows,
                icon, padding, gap, labelHeight, labelGap);
    }

    private static void assertFits(WidgetGrid grid, int width, int height) {
        PanelLayout layout = grid.layout;
        assertTrue(layout.iconSize > 0);
        assertTrue(layout.iconSize <= layout.cellWidth);
        assertTrue(layout.padding * 2 + grid.columns * layout.cellWidth
                + (grid.columns - 1) * layout.horizontalGap <= width);
        assertTrue(layout.padding * 2 + grid.rows * layout.cellHeight
                + (grid.rows - 1) * layout.verticalGap <= height);
    }
}
