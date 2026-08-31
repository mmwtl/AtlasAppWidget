package com.mmwtl.atlasappwidget;

import android.content.ComponentName;

/** Runtime view of a stored shortcut and its explicit target activity. */
final class ShortcutEntry {
    final ShortcutSpec spec;
    final ComponentName componentName;

    ShortcutEntry(ShortcutSpec spec) {
        this.spec = spec;
        this.componentName = ComponentName.unflattenFromString(spec.targetComponent);
        if (componentName == null) {
            throw new IllegalArgumentException("Invalid shortcut target");
        }
    }
}
