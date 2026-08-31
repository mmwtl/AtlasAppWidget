package com.mmwtl.atlasappwidget;

import android.content.ComponentName;

import java.util.Locale;

final class AppEntry {
    static final String FUEL_COMPONENT_KEY = "atlas:special:fuel";

    enum Kind {
        APPLICATION,
        SHORTCUT,
        FUEL
    }

    final String componentKey;
    final ComponentName componentName;
    final String label;
    final String activityLabel;
    final String searchText;
    final Kind kind;
    final String intentUri;

    AppEntry(ComponentName componentName, String label, String activityLabel) {
        this(Kind.APPLICATION, componentName.flattenToString(),
                componentName, label, activityLabel,
                label + " " + activityLabel + " "
                        + componentName.getPackageName() + " "
                        + componentName.getClassName(), null);
    }

    AppEntry(ShortcutEntry shortcut, String targetLabel) {
        this(Kind.SHORTCUT, shortcut.spec.key, shortcut.componentName, shortcut.spec.title,
                targetLabel, shortcut.spec.title + " " + targetLabel + " "
                        + shortcut.componentName.flattenToString(), shortcut.spec.intentUri);
    }

    private AppEntry(
            Kind kind,
            String componentKey,
            ComponentName componentName,
            String label,
            String activityLabel,
            String searchText,
            String intentUri
    ) {
        this.kind = kind;
        this.intentUri = intentUri;
        this.componentName = componentName;
        this.componentKey = componentKey;
        this.label = label;
        this.activityLabel = activityLabel;
        this.searchText = searchText.toLowerCase(Locale.getDefault());
    }

    static AppEntry fuel(String label, String activityLabel) {
        return new AppEntry(
                Kind.FUEL,
                FUEL_COMPONENT_KEY,
                null,
                label,
                activityLabel,
                label + " " + activityLabel + " fuel топливо бак бензин",
                null
        );
    }

    boolean isFuel() {
        return kind == Kind.FUEL;
    }

    boolean isShortcut() {
        return kind == Kind.SHORTCUT;
    }
}
