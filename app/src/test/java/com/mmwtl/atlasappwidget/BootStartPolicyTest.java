package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BootStartPolicyTest {
    @Test
    public void acceptsColdDirectQuickBootAndUnlockSignals() {
        assertTrue(BootStartPolicy.isStartupAction("android.intent.action.BOOT_COMPLETED"));
        assertTrue(BootStartPolicy.isStartupAction(
                "android.intent.action.LOCKED_BOOT_COMPLETED"));
        assertTrue(BootStartPolicy.isStartupAction(
                "android.intent.action.QUICKBOOT_POWERON"));
        assertTrue(BootStartPolicy.isStartupAction("android.intent.action.USER_UNLOCKED"));
    }

    @Test
    public void rejectsPackageReplacementAndUnrelatedSignals() {
        assertFalse(BootStartPolicy.isStartupAction(null));
        assertFalse(BootStartPolicy.isStartupAction("android.intent.action.MY_PACKAGE_REPLACED"));
        assertFalse(BootStartPolicy.isStartupAction("android.intent.action.SCREEN_ON"));
    }
}
