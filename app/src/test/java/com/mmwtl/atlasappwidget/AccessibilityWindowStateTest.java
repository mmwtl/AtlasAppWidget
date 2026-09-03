package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public final class AccessibilityWindowStateTest {
    private final Object token = new Object();

    @After public void resetState() {
        AccessibilityWindowState.setStateListener(null);
        AccessibilityWindowState.markConnected(token);
        AccessibilityWindowState.markUnavailable(token);
    }

    @Test public void connectedServiceDoesNotNeedManagerLookup() {
        AccessibilityWindowState.markConnected(token);
        assertTrue(AccessibilityWindowState.isEnabled(null));
    }

    @Test public void lifecycleNotifiesListenerAndIgnoresOldServiceDestruction() {
        int[] changes = {0};
        AccessibilityWindowState.setStateListener(() -> changes[0]++);
        Object oldToken = new Object();
        AccessibilityWindowState.markConnected(oldToken);
        AccessibilityWindowState.markConnected(token);
        AccessibilityWindowState.markUnavailable(oldToken);
        assertEquals(2, changes[0]);
        assertTrue(AccessibilityWindowState.isEnabled(null));

        AccessibilityWindowState.markUnavailable(token);
        assertEquals(3, changes[0]);
        assertFalse(AccessibilityWindowState.current().available);
    }

    @Test public void clearingOldListenerDoesNotRemoveNewListener() {
        int[] changes = {0};
        Runnable oldListener = () -> {};
        Runnable listener = () -> changes[0]++;
        AccessibilityWindowState.setStateListener(oldListener);
        AccessibilityWindowState.setStateListener(listener);
        AccessibilityWindowState.clearStateListener(oldListener);
        AccessibilityWindowState.markConnected(token);
        assertEquals(1, changes[0]);

        AccessibilityWindowState.clearStateListener(listener);
        AccessibilityWindowState.markUnavailable(token);
        assertEquals(1, changes[0]);
    }
}
