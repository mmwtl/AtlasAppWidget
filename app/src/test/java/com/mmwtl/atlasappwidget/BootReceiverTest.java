package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BootReceiverTest {
    @Test
    public void bootDelayAlwaysLeavesTimeForSystemWidgets() {
        assertEquals(15, BootReceiver.effectiveAutoStartDelaySeconds(0));
        assertEquals(15, BootReceiver.effectiveAutoStartDelaySeconds(15));
        assertEquals(90, BootReceiver.effectiveAutoStartDelaySeconds(90));
        assertEquals(300, BootReceiver.effectiveAutoStartDelaySeconds(900));
    }
}
