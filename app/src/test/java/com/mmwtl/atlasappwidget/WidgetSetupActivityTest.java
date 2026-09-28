package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class WidgetSetupActivityTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void rejectsMissingUnknownAndForeignIds() {
        AppWidgetProviderInfo foreign = new AppWidgetProviderInfo();
        foreign.provider = new ComponentName("other", "other.Provider");
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(92, foreign);
        for (int id : new int[]{AppWidgetManager.INVALID_APPWIDGET_ID, 91, 92}) {
            ActivityController<WidgetSetupActivity> controller =
                    Robolectric.buildActivity(WidgetSetupActivity.class, configure(id)).create();
            Activity activity = controller.get();
            assertTrue(activity.isFinishing());
            assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
            assertEquals(id, Shadows.shadowOf(activity).getResultIntent()
                    .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
            controller.destroy();
        }
    }

    @Test
    public void cancelKeepsIdAndRestoresGridAndLabels() {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_COLUMNS, 5);
        prefs.putBoolean(Prefs.KEY_SHOW_APP_LABELS, false);
        assertFalse(prefs.raw().contains(Prefs.KEY_ROWS));
        bind(61);
        ActivityController<WidgetSetupActivity> controller = open(61);
        WidgetSetupActivity activity = controller.get();
        View root = activity.findViewById(android.R.id.content);
        assertFalse("setup must stay open", activity.isFinishing());
        assertNotNull(MainActivityTest.find(root, "Виджет на рабочий стол"));
        described(root, "Увеличить: Столбцы").performClick();
        described(root, "Увеличить: Ряды").performClick();
        MainActivityTest.find(root, "Подписи под иконками").performClick();
        assertEquals("written immediately for the preview", 6, prefs.getInt(Prefs.KEY_COLUMNS, 0));
        assertEquals(2, prefs.getInt(Prefs.KEY_ROWS, 0));
        assertTrue(prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, false));

        MainActivityTest.find(root, "✕").performClick();
        assertTrue(activity.isFinishing());
        controller.pause().stop().destroy();
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        assertEquals(61, Shadows.shadowOf(activity).getResultIntent()
                .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
        assertEquals(5, prefs.getInt(Prefs.KEY_COLUMNS, 0));
        assertFalse("unset key stays unset", prefs.raw().contains(Prefs.KEY_ROWS));
        assertFalse(prefs.getBoolean(Prefs.KEY_SHOW_APP_LABELS, true));
        assertFalse(prefs.isWidgetConfigured(61));
    }

    @Test
    public void addKeepsChoicesAndSwitchesModeOnlyWhenChecked() {
        for (boolean switchMode : new boolean[]{false, true}) {
            int id = switchMode ? 63 : 62;
            Prefs prefs = new Prefs(context);
            prefs.setWidgetMode(false);
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
            prefs.putInt(Prefs.KEY_COLUMNS, 4);
            bind(id);
            ActivityController<WidgetSetupActivity> controller = open(id);
            WidgetSetupActivity activity = controller.get();
            View root = activity.findViewById(android.R.id.content);
            described(root, "Уменьшить: Столбцы").performClick();
            View modeSwitch = MainActivityTest.find(root, "Переключить в режим Android-виджета");
            assertNotNull(modeSwitch);
            if (!switchMode) modeSwitch.performClick();
            MainActivityTest.find(root, "Добавить").performClick();
            assertTrue(activity.isFinishing());
            controller.pause().stop().destroy();
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            assertEquals(id, Shadows.shadowOf(activity).getResultIntent()
                    .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
            assertEquals(3, prefs.getInt(Prefs.KEY_COLUMNS, 0));
            assertEquals(switchMode, prefs.isWidgetMode());
            assertEquals("overlay stops only on switch", !switchMode,
                    prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false));
            assertEquals(switchMode, MainActivityTest.stoppedServices().contains(
                    new ComponentName(context, OverlayService.class)));
        }
    }

    @Test
    public void confirmedIdReopensAsReconfigurationUntilDeleted() {
        new Prefs(context).setWidgetMode(true);
        bind(64);
        ActivityController<WidgetSetupActivity> first = open(64);
        View firstRoot = first.get().findViewById(android.R.id.content);
        assertNull("widget mode hides the switch",
                MainActivityTest.find(firstRoot, "Переключить в режим Android-виджета"));
        MainActivityTest.find(firstRoot, "Добавить").performClick();
        first.pause().stop().destroy();
        assertTrue(new Prefs(context).isWidgetConfigured(64));

        ActivityController<WidgetSetupActivity> again = open(64);
        View root = again.get().findViewById(android.R.id.content);
        assertNotNull(MainActivityTest.find(root, "Настройка виджета"));
        assertNull(MainActivityTest.find(root, "Добавить"));
        assertNotNull(MainActivityTest.find(root, "Открыть все настройки"));
        MainActivityTest.find(root, "Готово").performClick();
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(again.get()).getResultCode());
        again.pause().stop().destroy();

        new AtlasAppWidgetProvider().onDeleted(context, new int[]{64});
        assertFalse(new Prefs(context).isWidgetConfigured(64));
        ActivityController<WidgetSetupActivity> fresh = open(64);
        assertNotNull(MainActivityTest.find(fresh.get().findViewById(android.R.id.content),
                "Добавить"));
        fresh.destroy();
    }

    @Test
    public void addAndOpenStartsSettingsInNewTask() {
        new Prefs(context).setWidgetMode(true);
        bind(65);
        ActivityController<WidgetSetupActivity> controller = open(65);
        WidgetSetupActivity activity = controller.get();
        MainActivityTest.find(activity.findViewById(android.R.id.content),
                "Добавить и открыть все настройки").performClick();
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        Intent settings = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(settings);
        assertEquals(new ComponentName(context, MainActivity.class), settings.getComponent());
        assertTrue((settings.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        controller.pause().stop().destroy();
    }

    private ActivityController<WidgetSetupActivity> open(int id) {
        return Robolectric.buildActivity(WidgetSetupActivity.class, configure(id))
                .create().start().resume().visible();
    }

    private void bind(int id) {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, AtlasAppWidgetProvider.class);
        info.initialLayout = R.layout.widget_empty;
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id, info);
    }

    private static Intent configure(int id) {
        return new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
    }

    private static View described(View view, String description) {
        if (description.contentEquals(String.valueOf(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = described(group.getChildAt(index), description);
                if (match != null) return match;
            }
        }
        return null;
    }
}
