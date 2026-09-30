package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class MainActivityTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void tabsSwitchPagesAndSelectionSurvivesSavedState() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            for (String tab : new String[]{"Панель", "Ярлыки", "Вид", "Система"}) {
                assertTrue(tab, shown(find(root, tab)));
            }
            assertTrue(shown(find(root, "Режим отображения")));
            assertFalse(shown(find(root, "Сетка")));
            assertFalse("preview hidden on Panel", shown(find(root, "Предпросмотр панели")));

            find(root, "Вид").performClick();
            assertTrue(shown(find(root, "Сетка")));
            assertTrue(shown(find(root, "Стиль OneOS")));
            assertFalse(shown(find(root, "Режим отображения")));

            find(root, "Система").performClick();
            assertTrue(shown(find(root, "Импорт и экспорт настроек")));
            assertTrue(shown(find(root, "Интерфейс приложения")));
            assertFalse(shown(find(root, "Сетка")));

            Bundle state = new Bundle();
            controller.saveInstanceState(state);
            ActivityController<MainActivity> restored =
                    Robolectric.buildActivity(MainActivity.class).create(state);
            try {
                View restoredRoot = root(restored);
                assertTrue(shown(find(restoredRoot, "Импорт и экспорт настроек")));
                assertFalse(shown(find(restoredRoot, "Режим отображения")));
            } finally {
                restored.destroy();
            }
        } finally {
            close(controller);
        }
    }

    @Test
    public void collapsibleSectionsStartClosedAndOpenFromHeader() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Вид").performClick();
            assertFalse(shown(find(root, "Внутренний отступ")));
            assertFalse(shown(find(root, "Толщина обводки")));
            header(find(root, "Отступы и зазоры")).performClick();
            assertTrue(shown(find(root, "Внутренний отступ")));
            assertFalse("other sections stay closed", shown(find(root, "Толщина обводки")));
            header(find(root, "Отступы и зазоры")).performClick();
            assertFalse(shown(find(root, "Внутренний отступ")));

            find(root, "Ярлыки").performClick();
            assertFalse(shown(find(root, "Изменить формулу")));
            header(find(root, "Формула расчёта")).performClick();
            assertTrue(shown(find(root, "Использовать свою формулу")));
        } finally {
            close(controller);
        }
    }

    @Test
    public void eachDisplayModeShowsOnlyItsBlocks() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            assertTrue(shown(find(root, "На рабочем столе")));
            assertTrue(shown(find(root, "Добавить виджет на рабочий стол")));
            assertFalse(shown(find(root, "Перемещение")));
            assertFalse(shown(find(root, "Видимость")));
            assertFalse(shown(find(root, "Запустить панель")));
            find(root, "Вид").performClick();
            assertTrue(shown(find(root, "Сетка")));
            assertTrue(shown(find(root, context.getString(R.string.widget_geometry_hint))));
            assertFalse(shown(find(root, "Размер панели")));
            assertTrue(shown(find(root, "Системный статус")));
        } finally {
            close(controller);
        }

        prefs.setWidgetMode(false);
        controller = open();
        try {
            View root = root(controller);
            assertFalse(shown(find(root, "На рабочем столе")));
            assertTrue(shown(find(root, "Запустить панель")));
            assertTrue(shown(find(root, "Перемещение")));
            assertTrue(shown(find(root, "Видимость")));
            find(root, "Вид").performClick();
            assertTrue(shown(find(root, "Размер панели")));
            assertTrue(shown(find(root, "Системный статус")));
            assertFalse(shown(find(root, context.getString(R.string.widget_geometry_hint))));
        } finally {
            close(controller);
        }
    }

    @Test
    public void widgetSegmentSwitchesModeAndStopsOverlay() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(false);
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            TextView widget = (TextView) find(root, "Виджет");
            widget.performClick();
            assertTrue(prefs.isWidgetMode());
            assertTrue(widget.isSelected());
            assertFalse(prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, true));
            assertTrue(stoppedServices().contains(
                    new ComponentName(context, OverlayService.class)));
            assertTrue(shown(find(root, "На рабочем столе")));
            assertFalse(shown(find(root, "Перемещение")));

            find(root, "Оверлей").performClick();
            assertFalse(prefs.isWidgetMode());
            assertTrue(shown(find(root, "Перемещение")));
        } finally {
            close(controller);
        }
    }

    @Test
    public void importButtonPicksFileAndImportsAfterConfirmation() throws Exception {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_COLUMNS, 5);
        File file = File.createTempFile("backup", ".json");
        file.deleteOnExit();
        Files.write(file.toPath(),
                SettingsBackup.encode(context, prefs).getBytes(StandardCharsets.UTF_8));
        prefs.putInt(Prefs.KEY_COLUMNS, 3);
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Система").performClick();
            find(root, "Импортировать настройки из JSON").performClick();
            ShadowActivity shadow = Shadows.shadowOf(controller.get());
            Intent picker = shadow.getNextStartedActivityForResult().intent;
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.getAction());
            assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));
            assertEquals("*/*", picker.getType());
            shadow.receiveResult(picker, Activity.RESULT_OK,
                    new Intent().setData(Uri.fromFile(file)));

            await(() -> ShadowAlertDialog.getLatestDialog() != null);
            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(() -> prefs.getInt(Prefs.KEY_COLUMNS, 0) == 5);
        } finally {
            close(controller);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void exportButtonSharesJsonFile() throws Exception {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Система").performClick();
            find(root, "Экспортировать настройки в JSON").performClick();
            ShadowActivity shadow = Shadows.shadowOf(controller.get());
            Intent[] started = new Intent[1];
            await(() -> (started[0] = shadow.getNextStartedActivity()) != null);
            Intent chooser = started[0];
            assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
            assertTrue((chooser.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals(Intent.ACTION_SEND, send.getAction());
            assertEquals("application/json", send.getType());
            assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            Uri uri = send.getParcelableExtra(Intent.EXTRA_STREAM);
            assertNotNull(uri);
            assertEquals("content", uri.getScheme());
            assertEquals(BackupProvider.authority(context), uri.getAuthority());
            assertTrue(uri.getLastPathSegment().matches(
                    "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_AtlasAppWidget-settings\\.json"));
            assertEquals(uri, send.getClipData().getItemAt(0).getUri());
            File exported = new File(new File(context.getCacheDir(), "export"),
                    uri.getLastPathSegment());
            SettingsBackup.decode(new String(Files.readAllBytes(exported.toPath()),
                    StandardCharsets.UTF_8));
        } finally {
            close(controller);
        }
    }

    /** Settings transfer runs on the activity's I/O thread and posts back to the main looper. */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            ShadowLooper.idleMainLooper();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Condition not reached");
    }

    /** Drains the services stopped so far; WidgetFuelService.sync may stop its own service. */
    static java.util.List<ComponentName> stoppedServices() {
        java.util.List<ComponentName> stopped = new java.util.ArrayList<>();
        for (Intent intent = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getNextStoppedService(); intent != null; intent = Shadows.shadowOf(
                RuntimeEnvironment.getApplication()).getNextStoppedService()) {
            stopped.add(intent.getComponent());
        }
        return stopped;
    }

    private static ActivityController<MainActivity> open() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
    }

    private static void close(ActivityController<MainActivity> controller) {
        controller.pause().stop().destroy();
        ShadowLooper.runUiThreadTasks();
    }

    private static View root(ActivityController<MainActivity> controller) {
        return controller.get().findViewById(android.R.id.content);
    }

    /** The collapsible header is the grandparent of its title: header → title row → title. */
    private static View header(View title) {
        return (View) title.getParent().getParent();
    }

    static View find(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return view;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = find(group.getChildAt(index), text);
                if (match != null) return match;
            }
        }
        return null;
    }

    /** Visible on screen only if the view and every ancestor are VISIBLE. */
    static boolean shown(View view) {
        if (view == null) return false;
        for (View current = view; current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getVisibility() != View.VISIBLE) return false;
        }
        return true;
    }
}
