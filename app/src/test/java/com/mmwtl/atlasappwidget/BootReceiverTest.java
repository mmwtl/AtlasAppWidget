package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BootReceiverTest {
    @Test
    public void bootDelayAlwaysLeavesTimeForSystemWidgets() {
        assertEquals(5, BootReceiver.effectiveAutoStartDelaySeconds(0));
        assertEquals(5, BootReceiver.effectiveAutoStartDelaySeconds(5));
        assertEquals(90, BootReceiver.effectiveAutoStartDelaySeconds(90));
        assertEquals(300, BootReceiver.effectiveAutoStartDelaySeconds(900));
    }
}
