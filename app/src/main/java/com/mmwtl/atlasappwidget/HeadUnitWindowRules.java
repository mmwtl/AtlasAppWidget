package com.mmwtl.atlasappwidget;

import java.util.Set;

/** Narrow workarounds for known Atlas/OneOS windows that do not report normal activity state. */
final class HeadUnitWindowRules {
    private static final Set<String> FORCE_HIDE_PACKAGES = Set.of(
            "com.salat.gsplit",
            "com.geely.hvac",
            "com.geely.oneosphone"
    );
    private static final Set<String> FORCE_HIDE_CLASSES = Set.of(
            "com.salat.gbinder.features.launcher.LauncherEntryActivity"
    );

    private HeadUnitWindowRules() {
    }

    static boolean forceHide(String packageName, String className) {
        return FORCE_HIDE_PACKAGES.contains(value(packageName))
                || FORCE_HIDE_CLASSES.contains(value(className));
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
