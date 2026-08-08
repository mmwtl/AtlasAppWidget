package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BootReceiverTest {
    @Test
    public void bootDelayStaysWithinConfiguredRange() {
        assertEquals(0, BootReceiver.effectiveAutoStartDelaySeconds(-1));
        assertEquals(0, BootReceiver.effectiveAutoStartDelaySeconds(0));
        assertEquals(5, BootReceiver.effectiveAutoStartDelaySeconds(5));
        assertEquals(15, BootReceiver.effectiveAutoStartDelaySeconds(15));
        assertEquals(15, BootReceiver.effectiveAutoStartDelaySeconds(16));
    }
}
