package com.mmwtl.atlasappwidget;

/** Immutable subset of AccessibilityWindowInfo used by the visibility policy. */
final class WindowObservation {
    final String packageName;
    final String className;
    final int type;
    final boolean active;
    final boolean focused;
    final int layer;
    final int left;
    final int top;
    final int right;
    final int bottom;

    WindowObservation(
            String packageName,
            String className,
            int type,
            boolean active,
            boolean focused,
            int layer,
            int left,
            int top,
            int right,
            int bottom
    ) {
        this.packageName = value(packageName);
        this.className = value(className);
        this.type = type;
        this.active = active;
        this.focused = focused;
        this.layer = layer;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    int width() {
        return Math.max(0, right - left);
    }

    int height() {
        return Math.max(0, bottom - top);
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
