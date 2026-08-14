package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public final class ForegroundEventTrackerTest {
    private static final Set<String> HOME_PACKAGES = Set.of("launcher");

    @Test
    public void pausedHomeRemainsVisibleBehindFocusedFreeformActivity() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onPaused(110, "launcher", "HomeActivity");
        tracker.onResumed(120, "maps", "MapActivity");

        assertTrue(tracker.isAnyPackageVisible(HOME_PACKAGES));
    }

    @Test
    public void stoppedHomeIsHiddenByFullscreenActivity() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onPaused(110, "launcher", "HomeActivity");
        tracker.onStopped(120, "launcher", "HomeActivity");
        tracker.onResumed(130, "maps", "MapActivity");

        assertFalse(tracker.isAnyPackageVisible(HOME_PACKAGES));
    }

    @Test
    public void stoppingOneHomeActivityDoesNotHideAnotherVisibleHomeActivity() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onResumed(110, "launcher", "AssistantActivity");
        tracker.onStopped(120, "launcher", "AssistantActivity");

        assertTrue(tracker.isAnyPackageVisible(HOME_PACKAGES));
    }

    @Test
    public void staleOverlappingEventCannotRestoreStoppedActivity() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onStopped(200, "launcher", "HomeActivity");
        tracker.onResumed(150, "launcher", "HomeActivity");

        assertFalse(tracker.isAnyPackageVisible(HOME_PACKAGES));
    }

    @Test
    public void packageStopWithoutClassHidesAllActivitiesFromThatPackage() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onResumed(110, "launcher", "AssistantActivity");
        tracker.onStopped(120, "launcher", null);

        assertFalse(tracker.isAnyPackageVisible(HOME_PACKAGES));
    }

    @Test
    public void startupEventClearsActivitiesLeftOpenBeforeRestart() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onReset(200);
        tracker.onResumed(150, "launcher", "HomeActivity");

        assertFalse(tracker.isAnyPackageVisible(HOME_PACKAGES));
        assertTrue(tracker.hasObservedEvent());
    }

    @Test
    public void fallbackSeedStopsAfterRealEventsArrive() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.seed(100, "launcher");
        assertTrue(tracker.isAnyPackageVisible(HOME_PACKAGES));
        assertFalse(tracker.hasObservedEvent());

        tracker.onResumed(200, "maps", "MapActivity");
        tracker.seed(300, "launcher");

        assertFalse(tracker.isAnyPackageVisible(HOME_PACKAGES));
    }

    @Test
    public void reportsMostRecentlyVisibleActivityForWindowDisambiguation() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "launcher", "HomeActivity");
        tracker.onPaused(110, "launcher", "HomeActivity");
        tracker.onResumed(120, "settings", "SettingsActivity");

        ForegroundEventTracker.VisibleActivity visible = tracker.mostRecentVisibleActivity();

        assertEquals("settings", visible.packageName);
        assertEquals("SettingsActivity", visible.className);
    }
}
