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
    static final String EXTRA_TARGET_INTENT_URI =
            "com.mmwtl.atlasappwidget.extra.TARGET_INTENT_URI";

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

    static Intent proxy(Context context, AppEntry entry) {
        if (entry == null) return null;
        if (!entry.isShortcut()) return proxy(context, entry.componentName, entry.label);
        return new Intent(context, LaunchProxyActivity.class)
                .putExtra(EXTRA_TARGET_COMPONENT, entry.componentName.flattenToString())
                .putExtra(EXTRA_TARGET_INTENT_URI, entry.intentUri)
                .putExtra(EXTRA_TARGET_LABEL, entry.label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }

    static Intent targetComponent(String flattenedComponent) {
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

    static Intent targetIntent(String uri) {
        try {
            Intent parsed = ShortcutSpec.sanitize(Intent.parseUri(
                    uri, Intent.URI_INTENT_SCHEME));
            if (parsed.getComponent() == null) return null;
            return parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (Exception invalid) {
            return null;
        }
    }

    private static boolean isValid(ComponentName component) {
        return component != null
                && !component.getPackageName().isEmpty()
                && !component.getClassName().isEmpty();
    }
}
