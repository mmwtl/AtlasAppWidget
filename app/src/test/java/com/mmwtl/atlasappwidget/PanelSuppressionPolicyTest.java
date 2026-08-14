package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PanelSuppressionPolicyTest {
    @Test
    public void homeDoesNotCancelSuppressionBeforeNonHomeIsObserved() {
        PanelSuppressionPolicy policy = new PanelSuppressionPolicy();
        policy.suppress(100L, 1_500L);

        policy.onVisibility(true, 200L);

        assertFalse(policy.isPanelAllowed(200L));
        assertEquals(PanelSuppressionPolicy.State.WAITING_FOR_NON_HOME, policy.state());
    }

    @Test
    public void confirmedHomeReturnClearsSuppression() {
        PanelSuppressionPolicy policy = new PanelSuppressionPolicy();
        policy.suppress(100L, 1_500L);

        policy.onVisibility(false, 300L);
        policy.onVisibility(true, 500L);

        assertTrue(policy.isPanelAllowed(500L));
        assertEquals(PanelSuppressionPolicy.State.IDLE, policy.state());
    }

    @Test
    public void timeoutReleasesSuppressionWhenNonHomeWasNotObserved() {
        PanelSuppressionPolicy policy = new PanelSuppressionPolicy();
        policy.suppress(100L, 1_500L);

        assertTrue(policy.isPanelAllowed(1_600L));
        assertEquals(PanelSuppressionPolicy.State.IDLE, policy.state());
    }
}
