package com.atlas.overlaywidget;

import android.content.ComponentName;

import java.util.Locale;

final class AppEntry {
    final String componentKey;
    final ComponentName componentName;
    final String label;
    final String activityLabel;
    final String searchText;

    AppEntry(ComponentName componentName, String label, String activityLabel) {
        this.componentName = componentName;
        this.componentKey = componentName.flattenToString();
        this.label = label;
        this.activityLabel = activityLabel;
        this.searchText = (label + " " + activityLabel + " "
                + componentName.getPackageName() + " " + componentName.getClassName())
                .toLowerCase(Locale.getDefault());
    }
}
