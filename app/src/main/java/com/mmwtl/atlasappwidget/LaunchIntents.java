package com.mmwtl.atlasappwidget;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Builds the intents used to launch selected activities and shortcuts. */
final class LaunchIntents {
    private LaunchIntents() {
    }

    static Intent forEntry(Context context, AppEntry entry) {
        if (entry == null) {
            return null;
        }
        return entry.isShortcut()
                ? targetIntent(context, entry.intentUri)
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

    static Intent targetIntent(Context context, String uri) {
        try {
            Intent parsed = ShortcutSpec.sanitize(Intent.parseUri(
                    uri, Intent.URI_INTENT_SCHEME));
            if (ShortcutSpec.isDirectCall(parsed)
                    && context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                ShortcutSpec.withoutCallPermission(parsed);
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
