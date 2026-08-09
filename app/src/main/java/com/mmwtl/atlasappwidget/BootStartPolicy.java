package com.mmwtl.atlasappwidget;

final class BootStartPolicy {
    static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    private BootStartPolicy() {
    }

    static boolean isStartupAction(String action) {
        return "android.intent.action.BOOT_COMPLETED".equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || ACTION_QUICKBOOT_POWERON.equals(action)
                || "android.intent.action.USER_UNLOCKED".equals(action);
    }
}
