package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OpacityStepsTest {
    @Test public void everyStepSurvivesTheAlphaRoundTrip() {
        for (int step = 0; step <= 20; step++) {
            assertEquals(step, MainActivity.opacityStep(MainActivity.opacityAlpha(step)));
        }
        assertEquals(0, MainActivity.opacityAlpha(0));
        assertEquals(128, MainActivity.opacityAlpha(10));
        assertEquals(255, MainActivity.opacityAlpha(20));
    }

    @Test public void storedValuesLandOnTheNearestStep() {
        assertEquals(18, MainActivity.opacityStep(235));
        assertEquals(16, MainActivity.opacityStep(200));
    }
}
