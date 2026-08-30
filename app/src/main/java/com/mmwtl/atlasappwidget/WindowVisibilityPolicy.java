package com.mmwtl.atlasappwidget;

import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Decides whether HOME is actually visible from a snapshot of interactive windows. */
final class WindowVisibilityPolicy {
    enum Decision {
        HOME_VISIBLE,
        HOME_HIDDEN,
        UNKNOWN
    }

    static final int MIN_HIDE_THRESHOLD_PERCENT = 30;
    static final int MAX_HIDE_THRESHOLD_PERCENT = 95;
    static final int DEFAULT_HIDE_THRESHOLD_PERCENT = 85;

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
            String ownPackage,
            int hideThresholdPercent
    ) {
        if (windows == null || windows.isEmpty()
                || displayWidth <= 0 || displayHeight <= 0
                || homePackages == null || homePackages.isEmpty()) {
            return Decision.UNKNOWN;
        }
        int threshold = Math.max(MIN_HIDE_THRESHOLD_PERCENT,
                Math.min(MAX_HIDE_THRESHOLD_PERCENT, hideThresholdPercent));

        String foregroundPackage = value(eventPackage);
        String foregroundClass = value(eventClass);
        boolean foregroundFromCurrentWindow = !foregroundPackage.isEmpty();
        if (foregroundPackage.isEmpty() && foreground != null) {
            foregroundPackage = foreground.packageName;
            foregroundClass = foreground.className;
        }
        boolean foregroundIsKnownHome = isHomeComponent(
                foregroundPackage, foregroundClass, homeComponents);

        // UsageEvents may leave a paused GSplit activity marked visible after moveTaskToBack().
        // Only a package tied to the current accessibility window may bypass window inspection;
        // a UsageStats fallback must not override an unambiguously active HOME window below.
        if (foregroundFromCurrentWindow
                && HeadUnitWindowRules.forceHide(foregroundPackage, foregroundClass)) {
            return Decision.HOME_HIDDEN;
        }

        for (WindowObservation window : windows) {
            if (window.launcherAppListVisible
                    && homePackages.contains(window.packageName)
                    && (window.active
                    || window.focused
                    || window.packageName.equals(foregroundPackage))) {
                return Decision.HOME_HIDDEN;
            }
        }

        boolean launcherPresent = false;
        int highestLauncherLayer = Integer.MIN_VALUE;
        boolean nonHomeApplicationPresent = false;
        List<CoveredRect> visibleApplicationRects = new ArrayList<>();
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
                    window.width(), displayWidth, threshold)
                    && coversPercent(window.height(), displayHeight, threshold);
            boolean aboveLauncher = highestLauncherLayer == Integer.MIN_VALUE
                    || window.layer >= highestLauncherLayer;
            if (applicationWindow && aboveLauncher && !window.packageName.isEmpty()) {
                CoveredRect clipped = CoveredRect.clipped(window, displayWidth, displayHeight);
                if (clipped != null) {
                    visibleApplicationRects.add(clipped);
                }
            }
            boolean foregroundWindow = window.active || window.focused
                    || (!foregroundPackage.isEmpty()
                    && foregroundPackage.equals(window.packageName));
            // GSplit restores its singleTask activity after moveTaskToBack() without reliably
            // emitting a fresh window-state event on this head unit. Trust the current focused
            // application window even when the stale event still points at HOME and the restored
            // task uses freeform bounds.
            if (applicationWindow && (window.active || window.focused)
                    && HeadUnitWindowRules.forceHide(window.packageName, window.className)) {
                return Decision.HOME_HIDDEN;
            }
            if (fullScreen && aboveLauncher && foregroundWindow
                    && (!window.packageName.isEmpty() || applicationWindow)) {
                return Decision.HOME_HIDDEN;
            }
            if (fullScreen && foregroundWindow
                    && HeadUnitWindowRules.forceHide(window.packageName, window.className)) {
                return Decision.HOME_HIDDEN;
            }
        }

        if (launcherPresent && coversDisplayPercent(
                visibleApplicationRects, displayWidth, displayHeight, threshold)) {
            return Decision.HOME_HIDDEN;
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

    /** Returns true when the union of the rectangles covers the requested display percentage. */
    private static boolean coversDisplayPercent(
            List<CoveredRect> rectangles,
            int displayWidth,
            int displayHeight,
            int percent
    ) {
        if (rectangles.isEmpty()) {
            return false;
        }
        ArrayList<Integer> xEdges = new ArrayList<>(rectangles.size() * 2);
        for (CoveredRect rectangle : rectangles) {
            xEdges.add(rectangle.left);
            xEdges.add(rectangle.right);
        }
        xEdges.sort(Integer::compareTo);

        long coveredArea = 0L;
        for (int edge = 0; edge + 1 < xEdges.size(); edge++) {
            int left = xEdges.get(edge);
            int right = xEdges.get(edge + 1);
            if (right <= left) {
                continue;
            }
            ArrayList<CoveredRect> intervals = new ArrayList<>();
            for (CoveredRect rectangle : rectangles) {
                if (rectangle.left < right && rectangle.right > left) {
                    intervals.add(rectangle);
                }
            }
            intervals.sort(Comparator.comparingInt(rectangle -> rectangle.top));
            int coveredHeight = 0;
            int intervalTop = -1;
            int intervalBottom = -1;
            for (CoveredRect interval : intervals) {
                if (intervalTop < 0) {
                    intervalTop = interval.top;
                    intervalBottom = interval.bottom;
                } else if (interval.top > intervalBottom) {
                    coveredHeight += intervalBottom - intervalTop;
                    intervalTop = interval.top;
                    intervalBottom = interval.bottom;
                } else {
                    intervalBottom = Math.max(intervalBottom, interval.bottom);
                }
            }
            if (intervalTop >= 0) {
                coveredHeight += intervalBottom - intervalTop;
            }
            coveredArea += (long) (right - left) * coveredHeight;
        }
        return coveredArea * 100L
                >= (long) displayWidth * displayHeight * percent;
    }

    private static final class CoveredRect {
        final int left;
        final int top;
        final int right;
        final int bottom;

        CoveredRect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        static CoveredRect clipped(
                WindowObservation window,
                int displayWidth,
                int displayHeight
        ) {
            int left = Math.max(0, Math.min(displayWidth, window.left));
            int top = Math.max(0, Math.min(displayHeight, window.top));
            int right = Math.max(0, Math.min(displayWidth, window.right));
            int bottom = Math.max(0, Math.min(displayHeight, window.bottom));
            return right > left && bottom > top
                    ? new CoveredRect(left, top, right, bottom)
                    : null;
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
