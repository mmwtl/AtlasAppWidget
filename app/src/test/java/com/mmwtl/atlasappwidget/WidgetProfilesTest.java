package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class WidgetProfilesTest {
    private static final String APP = "com.example/.App";
    private static final String OTHER = "com.example/.Other";

    private final Context context = RuntimeEnvironment.getApplication();

    private void place(int... ids) {
        var manager = Shadows.shadowOf(AppWidgetManager.getInstance(context));
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, AtlasAppWidgetProvider.class);
        for (int id : ids) {
            manager.addBoundWidget(id, info);
        }
    }

    @Test
    public void widgetFollowsSharedSettingsUntilItsFirstEdit() {
        Prefs shared = new Prefs(context);
        Prefs widget = shared.forWidget(7);
        shared.putInt(Prefs.KEY_ROWS, 2);
        shared.setComponentSelected(APP, true);
        assertFalse(widget.hasProfile());
        assertEquals(2, widget.panelConfig().rows);
        assertEquals(List.of(APP), widget.selectedComponents());

        widget.putInt(Prefs.KEY_COLUMNS, 3);
        assertTrue(widget.hasProfile());
        // The first edit keeps what the widget showed and detaches it from the template.
        shared.putInt(Prefs.KEY_ROWS, 4);
        shared.setComponentSelected(OTHER, true);
        assertEquals(2, widget.panelConfig().rows);
        assertEquals(3, widget.panelConfig().columns);
        assertEquals(List.of(APP), widget.selectedComponents());
    }

    @Test
    public void widgetsAndOverlayKeepSeparateShortcutsAndLook() {
        Prefs shared = new Prefs(context);
        Prefs first = shared.forWidget(1);
        Prefs second = shared.forWidget(12);
        first.setComponentSelected(APP, true);
        first.applyOneOsPreset();
        second.setComponentSelected(OTHER, true);

        assertEquals(List.of(APP), first.selectedComponents());
        assertEquals(List.of(OTHER), second.selectedComponents());
        assertTrue(shared.selectedComponents().isEmpty());
        assertTrue(first.panelConfig().showAppLabels);
        assertFalse(second.panelConfig().showAppLabels);
        assertFalse(shared.panelConfig().showAppLabels);
    }

    @Test
    public void sharedSettingsPassThroughAWidgetWithoutCreatingAProfile() {
        Prefs shared = new Prefs(context);
        Prefs widget = shared.forWidget(3);
        widget.putBoolean(Prefs.KEY_AUTO_START, true);
        widget.putFuelFormula(2f, 3f);
        widget.setCustomIcon(APP, "icon");

        assertFalse(widget.hasProfile());
        assertTrue(shared.getBoolean(Prefs.KEY_AUTO_START, false));
        assertEquals(2f, shared.getFloat(Prefs.KEY_FUEL_MULTIPLIER, 0f), 0f);
        assertEquals("icon", shared.customIcon(APP));
    }

    @Test
    public void climateTransitionStaysWhileAnyWidgetShowsTheShortcut() {
        Prefs shared = new Prefs(context);
        Prefs first = shared.forWidget(1);
        Prefs second = shared.forWidget(2);
        first.setComponentSelected(APP, true);
        second.setComponentSelected(APP, true);
        first.setClimateTransitionEnabled(APP, true);

        first.setComponentSelected(APP, false);
        first.retainSelectedComponents(Set.of(OTHER));
        assertTrue(second.isClimateTransitionEnabled(APP));

        second.setComponentSelected(APP, false);
        assertFalse(second.isClimateTransitionEnabled(APP));
    }

    @Test
    public void removingAShortcutClearsItFromEveryWidget() {
        Prefs shared = new Prefs(context);
        Prefs first = shared.forWidget(1);
        Prefs second = shared.forWidget(2);
        shared.setComponentSelected(APP, true);
        first.setComponentSelected(OTHER, true);
        second.setComponentSelected(APP, true);
        second.setClimateTransitionEnabled(APP, true);

        first.removeShortcut(APP);

        assertTrue(shared.selectedComponents().isEmpty());
        assertEquals(List.of(OTHER), first.selectedComponents());
        assertTrue(second.selectedComponents().isEmpty());
        assertFalse(second.isClimateTransitionEnabled(APP));
    }

    @Test
    public void copyProfileReplacesOtherWidgetsAndLeavesTemplate() {
        Prefs shared = new Prefs(context);
        Prefs source = shared.forWidget(1);
        Prefs target = shared.forWidget(2);
        target.putInt(Prefs.KEY_ROWS, 4);
        source.setComponentSelected(APP, true);

        source.copyProfileTo(new int[]{1, 2});

        assertEquals(List.of(APP), target.selectedComponents());
        assertFalse(target.contains(Prefs.KEY_ROWS));
        assertTrue(shared.selectedComponents().isEmpty());
    }

    @Test
    public void deletingAndRestoringFollowTheWidgetId() {
        place(5);
        Prefs shared = new Prefs(context);
        shared.forWidget(5).putInt(Prefs.KEY_COLUMNS, 3);

        AtlasAppWidgetProvider provider = new AtlasAppWidgetProvider();
        provider.onRestored(context, new int[]{5}, new int[]{6});
        assertFalse(shared.forWidget(5).hasProfile());
        assertEquals(3, shared.forWidget(6).getInt(Prefs.KEY_COLUMNS, 0));

        provider.onDeleted(context, new int[]{6});
        assertFalse(shared.forWidget(6).hasProfile());
    }

    @Test
    public void backupRestoresProfilesByPositionWithTheirClimateTransitions() throws Exception {
        place(3, 9);
        Prefs shared = new Prefs(context);
        Prefs widget = shared.forWidget(9);
        widget.setComponentSelected(APP, true);
        widget.setClimateTransitionEnabled(APP, true);
        // Captured through a widget's view: the backup still holds the shared settings.
        SettingsBackup.Data data = SettingsBackup.decode(SettingsBackup.encode(
                SettingsBackup.capture(context, widget), "test"));
        assertNull(data.widgetProfiles.get(0));
        assertTrue(data.selectedComponents.isEmpty());

        widget.removeProfile();
        shared.forWidget(3).ensureProfile();
        shared.raw().edit().remove(Prefs.KEY_CLIMATE_TRANSITION_COMPONENTS).commit();
        assertTrue(shared.replacePortableSettings(context, data));

        assertFalse(shared.forWidget(3).hasProfile());
        assertEquals(List.of(APP), widget.selectedComponents());
        assertTrue(widget.isClimateTransitionEnabled(APP));
    }

    @Test
    public void importingFileWithoutProfilesPutsWidgetsBackOnSharedSettings() throws Exception {
        place(4);
        Prefs shared = new Prefs(context);
        SettingsBackup.Data data = SettingsBackup.capture(context, shared);
        data.widgetProfiles = List.of();
        shared.forWidget(4).ensureProfile();

        assertTrue(shared.replacePortableSettings(context,
                SettingsBackup.decode(SettingsBackup.encode(data, "test"))));

        assertFalse(shared.forWidget(4).hasProfile());
    }

    @Test
    public void unknownProfileKeysAreDroppedOnImport() throws Exception {
        place(4);
        SettingsBackup.Data data = SettingsBackup.capture(context, new Prefs(context));
        data.widgetProfiles = List.of(Map.of(
                Prefs.KEY_COLUMNS, 3,
                Prefs.KEY_DISPLAY_MODE, "overlay",
                Prefs.KEY_AUTO_START, true,
                Prefs.KEY_SELECTED_COMPONENTS, "[]"));

        Map<String, Object> restored = SettingsBackup.decode(
                SettingsBackup.encode(data, "test")).widgetProfiles.get(0);

        assertEquals(Map.of(Prefs.KEY_COLUMNS, 3, Prefs.KEY_SELECTED_COMPONENTS, "[]"), restored);
    }

    @Test
    public void settingsEditTheRequestedWidgetOnly() {
        place(31, 32);
        Prefs shared = new Prefs(context);
        shared.setWidgetMode(true);
        var controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(context, MainActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 32)).create();
        try {
            View root = controller.get().findViewById(android.R.id.content);
            Spinner chooser = findChooser(root);
            assertNotNull(chooser);
            assertEquals(1, chooser.getSelectedItemPosition());
            assertEquals(3, chooser.getCount());
            assertEquals("Шаблон для новых виджетов", chooser.getItemAtPosition(2));
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void overlayModeEditsSharedSettingsEvenWhenWidgetsArePlaced() {
        place(41);
        var controller = Robolectric.buildActivity(MainActivity.class).create();
        try {
            assertFalse(new Prefs(context).forWidget(41).hasProfile());
        } finally {
            controller.destroy();
        }
    }

    private static Spinner findChooser(View view) {
        if (view instanceof Spinner spinner
                && "Какой виджет настраивать".contentEquals(spinner.getContentDescription() == null
                        ? "" : spinner.getContentDescription())) {
            return spinner;
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                Spinner found = findChooser(group.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
