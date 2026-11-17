package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class AppAvailabilityTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void unavailableAppsDisappearAndReturnWithoutLosingSelection() {
        ApplicationInfo app = new ApplicationInfo();
        app.packageName = "com.example.player";
        app.enabled = true;
        ActivityInfo activity = new ActivityInfo();
        activity.packageName = app.packageName;
        activity.name = app.packageName + ".Main";
        activity.applicationInfo = app;
        activity.enabled = true;
        activity.exported = true;
        ResolveInfo resolved = new ResolveInfo();
        resolved.activityInfo = activity;
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        var pm = Shadows.shadowOf(context.getPackageManager());
        pm.addResolveInfoForIntent(launcher, resolved);
        Prefs widget = new Prefs(context).forWidget(4);
        String key = new ComponentName(activity.packageName, activity.name).flattenToString();
        widget.setComponentSelected(key, true);
        assertEquals(1, AppRepository.loadSelectedActivities(context, widget).size());

        app.flags |= ApplicationInfo.FLAG_SUSPENDED;
        assertTrue(AppRepository.loadSelectedActivities(context, widget).isEmpty());
        app.flags &= ~ApplicationInfo.FLAG_SUSPENDED;
        assertEquals(1, AppRepository.loadSelectedActivities(context, widget).size());
        app.enabled = false;
        assertTrue(AppRepository.loadSelectedActivities(context, widget).isEmpty());
        app.enabled = true;
        activity.enabled = false;
        assertTrue(AppRepository.loadSelectedActivities(context, widget).isEmpty());
        activity.enabled = true;
        pm.removeResolveInfosForIntent(launcher, app.packageName);
        assertTrue(AppRepository.loadSelectedActivities(context, widget).isEmpty());
        assertEquals(List.of(key), widget.selectedComponents());
    }

    @Test
    public void ordinaryWidgetKeepsPackageMonitoringAliveWithoutFuelOrStatus() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        prefs.putBoolean(Prefs.KEY_SHOW_SYSTEM_STATUS, false);
        var manager = Shadows.shadowOf(AppWidgetManager.getInstance(context));
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, AtlasAppWidgetProvider.class);
        manager.addBoundWidget(8, info);
        WidgetFuelService.sync(context);
        assertEquals(WidgetFuelService.class.getName(),
                Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService()
                        .getComponent().getClassName());
        prefs.setWidgetMode(false);
        WidgetFuelService.sync(context);
        assertFalse(prefs.isWidgetMode());
    }
}
