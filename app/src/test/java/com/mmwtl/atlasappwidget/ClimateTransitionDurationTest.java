package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ClimateTransitionDurationTest {
    @Test public void clampsAndRoundsExistingDurations() {
        int[][] cases = {
                {Integer.MIN_VALUE, 50}, {10, 50}, {50, 50}, {74, 50}, {75, 100},
                {110, 100}, {125, 150}, {475, 500}, {500, 500}, {1000, 500},
                {Integer.MAX_VALUE, 500}
        };
        for (int[] pair : cases) {
            assertEquals(pair[1], Prefs.normalizeClimateTransitionDuration(pair[0]));
        }
    }

    @Test public void preservesAllTenSliderSteps() {
        assertEquals(50, Prefs.CLIMATE_TRANSITION_DURATION_MIN_MS);
        assertEquals(500, Prefs.CLIMATE_TRANSITION_DURATION_MAX_MS);
        assertEquals(50, Prefs.CLIMATE_TRANSITION_DURATION_STEP_MS);
        for (int duration = 50; duration <= 500; duration += 50) {
            assertEquals(duration, Prefs.normalizeClimateTransitionDuration(duration));
        }
    }
}
