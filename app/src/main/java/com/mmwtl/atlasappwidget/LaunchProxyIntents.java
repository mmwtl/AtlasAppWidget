package com.mmwtl.atlasappwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Builds the intents used by the optional full-screen launch bridge. */
final class LaunchProxyIntents {
    static final String EXTRA_TARGET_COMPONENT =
            "com.mmwtl.atlasappwidget.extra.TARGET_COMPONENT";
    static final String EXTRA_TARGET_LABEL =
            "com.mmwtl.atlasappwidget.extra.TARGET_LABEL";

    private LaunchProxyIntents() {
    }

    static Intent proxy(Context context, ComponentName component, String label) {
        if (!isValid(component)) {
            return null;
        }
        return new Intent(context, LaunchProxyActivity.class)
                .putExtra(EXTRA_TARGET_COMPONENT, component.flattenToString())
                .putExtra(EXTRA_TARGET_LABEL, label == null ? "" : label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }

    static Intent target(String flattenedComponent) {
        ComponentName component = ComponentName.unflattenFromString(flattenedComponent);
        if (!isValid(component)) {
            return null;
        }
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    private static boolean isValid(ComponentName component) {
        return component != null
                && !component.getPackageName().isEmpty()
                && !component.getClassName().isEmpty();
    }
}
