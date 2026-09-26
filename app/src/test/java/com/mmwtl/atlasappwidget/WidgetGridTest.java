package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WidgetGridTest {
    @Test
    public void resizingChangesCapacityInBothDirections() {
        WidgetGrid small = WidgetGrid.forSize(180, 90, true);
        WidgetGrid wide = WidgetGrid.forSize(400, 90, true);
        WidgetGrid tall = WidgetGrid.forSize(180, 300, true);

        assertEquals(2, small.columns);
        assertEquals(1, small.rows);
        assertEquals(5, wide.columns);
        assertEquals(1, wide.rows);
        assertEquals(2, tall.columns);
        assertEquals(3, tall.rows);
    }

    @Test
    public void tinyAndLargeHostsRemainBounded() {
        assertEquals(1, WidgetGrid.forSize(1, 1, false).capacity());
        assertEquals(48, WidgetGrid.forSize(4000, 4000, false).capacity());
    }
}
