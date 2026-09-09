package com.mmwtl.atlasappwidget;

import android.content.ComponentName;
import android.content.Intent;

/** Builds the explicit intents used to launch selected activities and shortcuts. */
final class LaunchIntents {
    private LaunchIntents() {
    }

    static Intent forEntry(AppEntry entry) {
        if (entry == null) {
            return null;
        }
        return entry.isShortcut()
                ? targetIntent(entry.intentUri)
                : forComponent(entry.componentName);
    }

    static Intent forComponent(ComponentName component) {
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
            if (parsed.getComponent() == null) {
                return null;
            }
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
