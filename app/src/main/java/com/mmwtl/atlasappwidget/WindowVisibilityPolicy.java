package com.mmwtl.atlasappwidget;

import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.Set;

/** Decides whether HOME is actually visible from a snapshot of interactive windows. */
final class WindowVisibilityPolicy {
    enum Decision {
        HOME_VISIBLE,
        HOME_HIDDEN,
        UNKNOWN
    }

    private static final int FULLSCREEN_PERCENT = 85;

    private WindowVisibilityPolicy() {
    }

    static Decision evaluate(
            List<WindowObservation> windows,
            int displayWidth,
            int displayHeight,
            Set<String> homePackages,
            Set<String> homeComponents,
            ForegroundEventTracker.VisibleActivity foreground,
            String eventPackage,
            String eventClass,
            String ownPackage
    ) {
        if (windows == null || windows.isEmpty()
                || displayWidth <= 0 || displayHeight <= 0
                || homePackages == null || homePackages.isEmpty()) {
            return Decision.UNKNOWN;
        }

        String foregroundPackage = value(eventPackage);
        String foregroundClass = value(eventClass);
        if (foregroundPackage.isEmpty() && foreground != null) {
            foregroundPackage = foreground.packageName;
            foregroundClass = foreground.className;
        }
        boolean foregroundIsKnownHome = isHomeComponent(
                foregroundPackage, foregroundClass, homeComponents);

        if (HeadUnitWindowRules.forceHide(foregroundPackage, foregroundClass)) {
            return Decision.HOME_HIDDEN;
        }

        boolean launcherPresent = false;
        int highestLauncherLayer = Integer.MIN_VALUE;
        boolean nonHomeApplicationPresent = false;
        for (WindowObservation window : windows) {
            boolean homeWindow = isHomeWindow(
                    window,
                    homePackages,
                    homeComponents,
                    foregroundPackage,
                    foregroundClass
            );
            if (homeWindow) {
                launcherPresent = true;
                highestLauncherLayer = Math.max(highestLauncherLayer, window.layer);
            }
        }
        for (WindowObservation window : windows) {
            boolean homeWindow = isHomeWindow(
                    window,
                    homePackages,
                    homeComponents,
                    foregroundPackage,
                    foregroundClass
            );
            if (homeWindow) {
                continue;
            }

            boolean applicationWindow = window.type == AccessibilityWindowInfo.TYPE_APPLICATION;
            boolean ownPassiveOverlay = ownPackage.equals(window.packageName)
                    && !applicationWindow && !window.active && !window.focused;
            if (ownPassiveOverlay) {
                continue;
            }
            if (applicationWindow && !window.packageName.isEmpty()) {
                nonHomeApplicationPresent = true;
            }

            boolean fullScreen = coversPercent(
                    window.width(), displayWidth, FULLSCREEN_PERCENT)
                    && coversPercent(window.height(), displayHeight, FULLSCREEN_PERCENT);
            boolean aboveLauncher = highestLauncherLayer == Integer.MIN_VALUE
                    || window.layer >= highestLauncherLayer;
            boolean foregroundWindow = window.active || window.focused
                    || (!foregroundPackage.isEmpty()
                    && foregroundPackage.equals(window.packageName));
            if (fullScreen && aboveLauncher && foregroundWindow
                    && (!window.packageName.isEmpty() || applicationWindow)) {
                return Decision.HOME_HIDDEN;
            }
            if (fullScreen && foregroundWindow
                    && HeadUnitWindowRules.forceHide(window.packageName, window.className)) {
                return Decision.HOME_HIDDEN;
            }
        }

        // A focused non-HOME activity from a package that also exposes FallbackHome must not be
        // mistaken for the launcher merely because the package appears in CATEGORY_HOME.
        if (!foregroundPackage.isEmpty()
                && homePackages.contains(foregroundPackage)
                && !foregroundIsKnownHome
                && !foregroundClass.isEmpty()
                && !launcherPresent) {
            return Decision.HOME_HIDDEN;
        }
        if (launcherPresent || foregroundIsKnownHome) {
            return Decision.HOME_VISIBLE;
        }
        if (!foregroundPackage.isEmpty() || nonHomeApplicationPresent) {
            return Decision.HOME_HIDDEN;
        }
        return Decision.UNKNOWN;
    }

    private static boolean isHomeWindow(
            WindowObservation window,
            Set<String> homePackages,
            Set<String> homeComponents,
            String foregroundPackage,
            String foregroundClass
    ) {
        if (isHomeComponent(window.packageName, window.className, homeComponents)) {
            return true;
        }
        if (!homePackages.contains(window.packageName)) {
            return false;
        }
        return !window.packageName.equals(foregroundPackage)
                || foregroundClass.isEmpty()
                || isHomeComponent(foregroundPackage, foregroundClass, homeComponents);
    }

    private static boolean isHomeComponent(
            String packageName,
            String className,
            Set<String> homeComponents
    ) {
        return homeComponents != null
                && homeComponents.contains(componentKey(packageName, className));
    }

    static String componentKey(String packageName, String className) {
        String packageValue = value(packageName);
        String classValue = value(className);
        if (classValue.startsWith(".")) {
            classValue = packageValue + classValue;
        }
        return packageValue + "/" + classValue;
    }

    private static boolean coversPercent(int size, int displaySize, int percent) {
        return (long) size * 100L >= (long) displaySize * percent;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
