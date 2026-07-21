package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ForegroundEventTrackerTest {
    @Test
    public void resumeThenPauseClearsForegroundPackage() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher");
        tracker.onStopped(110, "launcher");

        assertNull(tracker.foregroundPackage());
        assertTrue(tracker.hasObservedEvent());
    }

    @Test
    public void staleEventCannotReplaceNewerForegroundPackage() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(200, "maps");
        tracker.onResumed(150, "launcher");

        assertEquals("maps", tracker.foregroundPackage());
    }

    @Test
    public void pauseForDifferentPackageDoesNotClearForeground() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher");
        tracker.onStopped(110, "other");

        assertEquals("launcher", tracker.foregroundPackage());
    }

    @Test
    public void fallbackSeedStopsAfterRealEventsArrive() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.seed(100, "launcher");
        assertFalse(tracker.hasObservedEvent());
        tracker.onResumed(200, "maps");
        tracker.seed(300, "launcher");

        assertEquals("maps", tracker.foregroundPackage());
    }
}
