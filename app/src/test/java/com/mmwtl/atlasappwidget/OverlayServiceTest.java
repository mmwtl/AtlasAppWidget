package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OverlayServiceTest {
    @Test
    public void fuelDetailsAutoHideDelayIsTenSeconds() {
        assertEquals(10_000L, OverlayService.FUEL_DETAILS_AUTO_HIDE_DELAY_MS);
    }
}
