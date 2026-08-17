package com.mmwtl.atlasappwidget;

/** Identifies the launcher app-list state exposed through Accessibility nodes. */
final class LauncherAllAppsViewDetector {
    private static final String LAUNCHER_PACKAGE = "com.android.launcher3";
    private static final String ONE_OS_ALL_APPS_CONTAINER_ID =
            ":id/ll_all_apps_container";
    private static final String STANDARD_ALL_APPS_ID = ":id/all_apps";
    private static final String ONE_OS_APP_PAGE_ID = ":id/page_view_app";
    private static final String STANDARD_ALL_APPS_CLASS =
            "com.android.launcher3.allapps.AllAppsContainerView";
    private static final String ONE_OS_ALL_APPS_TAB_TEXT = "My APPs";

    private LauncherAllAppsViewDetector() {
    }

    static boolean isLauncherPackage(String packageName) {
        return LAUNCHER_PACKAGE.equals(packageName);
    }

    static boolean isAllAppsMarker(
            String viewIdResourceName,
            String className,
            String text
    ) {
        return hasSuffix(viewIdResourceName, ONE_OS_ALL_APPS_CONTAINER_ID)
                || hasSuffix(viewIdResourceName, STANDARD_ALL_APPS_ID)
                || hasSuffix(viewIdResourceName, ONE_OS_APP_PAGE_ID)
                || STANDARD_ALL_APPS_CLASS.equals(className)
                || ONE_OS_ALL_APPS_TAB_TEXT.equals(text);
    }

    private static boolean hasSuffix(String value, String suffix) {
        return value != null && value.endsWith(suffix);
    }
}
