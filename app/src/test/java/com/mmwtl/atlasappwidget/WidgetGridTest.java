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
    public void expandingHostScalesIconsLabelsPaddingAndGapsTogether() {
        WidgetGrid small = grid(400, 240, 3, 2, 80, 10, 8, 20, 4);
        WidgetGrid large = grid(800, 480, 3, 2, 80, 10, 8, 20, 4);
        assertEquals(small.layout.iconSize * 2, large.layout.iconSize);
        assertEquals(small.layout.padding * 2, large.layout.padding);
        assertEquals(small.layout.horizontalGap * 2, large.layout.horizontalGap);
        assertEquals(small.layout.verticalGap * 2, large.layout.verticalGap);
        assertEquals(small.labelScale * 2, large.labelScale, 0.001f);
        assertTrue(large.layout.iconSize > 80);
        assertFits(small, 400, 240);
        assertFits(large, 800, 480);
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
        assertEquals(labels.layout.iconSize + (int) (32 * labels.labelScale)
                + (int) (16 * labels.labelScale), labels.layout.cellHeight);
    }

    @Test
    public void statusStripTakesSpaceFromTheGridOnItsSide() {
        WidgetGrid plain = grid(900, 300, 5, 2, 104, 10, 10, 0, 0);
        WidgetGrid bottom = WidgetGrid.forSize(900, 300, 5, 2, 104, 10, 10, 0, 0,
                false, 60, 16);
        WidgetGrid side = WidgetGrid.forSize(900, 300, 5, 2, 104, 10, 10, 0, 0,
                true, 40, 16);
        assertTrue(bottom.layout.gridWidth <= 900);
        assertTrue(bottom.layout.gridHeight + 60 + 16 + bottom.layout.padding * 2 <= 300);
        assertTrue(side.layout.gridWidth + 56 + side.layout.padding * 2 <= 900);
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
