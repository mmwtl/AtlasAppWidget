package com.mmwtl.atlasappwidget;

import android.content.ComponentName;
import android.content.Intent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Immutable, portable description of a legacy ACTION_CREATE_SHORTCUT result. */
final class ShortcutSpec {
    static final String KEY_PREFIX = "atlas:shortcut:";
    static final String GSPLIT_PACKAGE = "com.salat.gsplit";
    static final String GSPLIT_PRESET_ACTIVITY = ".PresetLauncherActivity";
    static final int MAX_SHORTCUTS = 100;
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_INTENT_URI_LENGTH = 16 * 1024;
    static final int MAX_TARGET_COMPONENT_LENGTH = 512;

    final String key;
    final String title;
    final String intentUri;
    final String targetComponent;

    ShortcutSpec(String key, String title, String intentUri, String targetComponent) {
        if (key == null || !key.startsWith(KEY_PREFIX)
                || !key.equals(stableKey(intentUri))) {
            throw new IllegalArgumentException("Invalid shortcut key");
        }
        if (title == null || title.trim().isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Invalid shortcut title");
        }
        if (intentUri == null || intentUri.isEmpty()
                || intentUri.length() > MAX_INTENT_URI_LENGTH) {
            throw new IllegalArgumentException("Invalid shortcut intent URI");
        }
        if (targetComponent == null || targetComponent.isEmpty()
                || targetComponent.length() > MAX_TARGET_COMPONENT_LENGTH) {
            throw new IllegalArgumentException("Invalid shortcut target");
        }
        this.key = key;
        this.title = title.trim();
        this.intentUri = intentUri;
        this.targetComponent = targetComponent;
    }

    static ShortcutSpec create(String title, Intent intent) {
        if (intent == null || intent.getComponent() == null) {
            throw new IllegalArgumentException("Shortcut intent must be explicit");
        }
        String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
        ComponentName component = intent.getComponent();
        return new ShortcutSpec(stableKey(uri), title, uri, component.flattenToString());
    }

    static Intent sanitize(Intent source) {
        if (source == null || source.getComponent() == null) {
            throw new IllegalArgumentException("Shortcut intent must be explicit");
        }
        Intent copy = new Intent(source);
        copy.setSelector(null);
        copy.setClipData(null);
        // A shortcut provider is external input. The target action, data, categories and extras
        // describe the shortcut, but task/grant flags must remain under this launcher's control.
        copy.setFlags(0);
        return copy;
    }

    static String stableKey(String intentUri) {
        if (intentUri == null) {
            return KEY_PREFIX + "invalid";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(intentUri.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(KEY_PREFIX.length() + digest.length * 2);
            result.append(KEY_PREFIX);
            for (byte value : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static boolean isGsplitPreset(ComponentName component) {
        return component != null
                && GSPLIT_PACKAGE.equals(component.getPackageName())
                && (GSPLIT_PACKAGE + GSPLIT_PRESET_ACTIVITY).equals(component.getClassName());
    }

    Intent parseIntent() throws Exception {
        return Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
    }
}
