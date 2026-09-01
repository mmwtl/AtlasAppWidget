package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertTrue;

import android.view.WindowManager;

import org.junit.Test;

public final class LaunchTransitionOverlayTest {
    @Test
    public void transitionWindowPreservesNonInteractiveFullscreenFlags() {
        int flags = LaunchTransitionOverlay.WINDOW_FLAGS;

        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0);
    }
}
