package com.mmwtl.atlasappwidget;

enum OverlayCorner {
    TOP_START("top_start", "Слева сверху"),
    TOP_END("top_end", "Справа сверху"),
    BOTTOM_START("bottom_start", "Слева снизу"),
    BOTTOM_END("bottom_end", "Справа снизу");

    final String preferenceValue;
    final String label;

    OverlayCorner(String preferenceValue, String label) {
        this.preferenceValue = preferenceValue;
        this.label = label;
    }

    static OverlayCorner fromPreference(String value) {
        if (value == null) return null;
        for (OverlayCorner corner : values()) {
            if (corner.preferenceValue.equals(value)) return corner;
        }
        return null;
    }
}
