package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class ThemeColorsTest {
    private static final int LIGHT = 0xFFF0E6D2;
    private static final int DARK = 0xFF102030;

    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void cleanInstallFollowsTheTheme() {
        PanelConfig config = new Prefs(context).panelConfig();

        assertEquals(PanelConfig.THEME_COLOR, config.backgroundColor);
        assertEquals(PanelConfig.THEME_COLOR, config.dayBackgroundColor);
        assertEquals(PanelConfig.TextMode.THEME, config.textMode());
        assertEquals(PanelConfig.GRAPHITE, config.overlayBackgroundColor());
    }

    @Test public void textReadsOnTheBackgroundOfEachMode() {
        assertEquals(PanelConfig.TextMode.THEME, textMode(PanelConfig.THEME_COLOR, DARK));
        assertEquals(PanelConfig.TextMode.THEME, textMode(LIGHT, DARK));
        assertEquals(PanelConfig.TextMode.LIGHT, textMode(DARK, PanelConfig.THEME_COLOR));
        assertEquals(PanelConfig.TextMode.DARK, textMode(PanelConfig.THEME_COLOR, LIGHT));
        assertEquals(PanelConfig.TextMode.INVERSE, textMode(DARK, LIGHT));
    }

    @Test public void themeSurfacesMatchTheResources() {
        assertEquals(context.getColor(R.color.widget_surface), PanelConfig.DAY_SURFACE);
    }

    @Test public void darkTextHasNoOutlineByDay() {
        assertEquals(0, context.getColor(R.color.widget_label_outline));
        assertEquals(0xFF000000, context.getColor(R.color.widget_label_outline_inverse));
    }

    @Config(qualifiers = "night")
    @Test public void darkTextHasNoOutlineAtNight() {
        assertEquals(0xFF000000, context.getColor(R.color.widget_label_outline));
        assertEquals(0, context.getColor(R.color.widget_label_outline_inverse));
    }

    @Config(qualifiers = "night")
    @Test public void nightSurfaceMatchesTheResources() {
        assertEquals(context.getColor(R.color.widget_surface), PanelConfig.NIGHT_SURFACE);
    }

    @Test public void upgradeMakesOldDefaultsFollowTheTheme() {
        stored().putInt(Prefs.KEY_BACKGROUND_COLOR, PanelConfig.GRAPHITE)
                .putInt(Prefs.KEY_BACKGROUND_STROKE_COLOR, PanelConfig.STROKE_ACCENT).commit();

        PanelConfig config = new Prefs(context).panelConfig();
        assertEquals(PanelConfig.THEME_COLOR, config.backgroundColor);
        assertEquals(PanelConfig.THEME_COLOR, config.dayBackgroundColor);
        assertEquals(PanelConfig.THEME_COLOR, config.backgroundStrokeColor);
        assertEquals(PanelConfig.THEME_COLOR, config.dayBackgroundStrokeColor);
    }

    @Test public void upgradeKeepsAPickedColorInBothModes() {
        stored().putInt(Prefs.KEY_BACKGROUND_COLOR, LIGHT).commit();

        PanelConfig config = new Prefs(context).panelConfig();
        assertEquals(LIGHT, config.backgroundColor);
        assertEquals(LIGHT, config.dayBackgroundColor);
        assertEquals(PanelConfig.TextMode.DARK, config.textMode());
    }

    @Test public void graphitePickedAfterTheMigrationStaysPicked() {
        new Prefs(context).putInt(Prefs.KEY_BACKGROUND_COLOR, PanelConfig.GRAPHITE);

        assertEquals(PanelConfig.GRAPHITE, new Prefs(context).panelConfig().backgroundColor);
    }

    @Test public void fixedThemeShowsItsColorsInBothModes() {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_DAY_BACKGROUND_COLOR, LIGHT);
        prefs.putInt(Prefs.KEY_BACKGROUND_COLOR, DARK);
        prefs.putInt(Prefs.KEY_THEME_MODE, PanelConfig.THEME_DAY);

        PanelConfig config = prefs.panelConfig();
        assertEquals(LIGHT, config.widgetBackground(false));
        assertEquals(LIGHT, config.widgetBackground(true));
        assertEquals(PanelConfig.TextMode.DARK, config.textMode());

        prefs.putInt(Prefs.KEY_THEME_MODE, PanelConfig.THEME_NIGHT);
        config = prefs.panelConfig();
        assertEquals(DARK, config.widgetBackground(false));
        assertEquals(PanelConfig.TextMode.LIGHT, config.textMode());
    }

    @Test public void fixedThemeResolvesTheThemeColorItself() {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_THEME_MODE, PanelConfig.THEME_NIGHT);

        PanelConfig config = prefs.panelConfig();
        assertEquals(PanelConfig.NIGHT_SURFACE, config.widgetBackground(false));
        assertEquals(PanelConfig.NIGHT_SURFACE, config.widgetBackground(true));
        assertEquals(PanelConfig.TextMode.LIGHT, config.textMode());
        assertEquals("the overlay keeps its own colors",
                PanelConfig.GRAPHITE, config.overlayBackgroundColor());

        prefs.putInt(Prefs.KEY_THEME_MODE, PanelConfig.THEME_DAY);
        assertEquals(PanelConfig.TextMode.DARK, prefs.panelConfig().textMode());
    }

    @Test public void autoThemeLeavesTheColorsToTheHost() {
        PanelConfig config = new Prefs(context).panelConfig();

        assertEquals(PanelConfig.THEME_AUTO, config.themeMode);
        assertEquals(PanelConfig.THEME_COLOR, config.widgetBackground(false));
        assertEquals(PanelConfig.THEME_COLOR, config.widgetBackground(true));
    }

    @Test public void themeTextMatchesTheResources() {
        assertEquals(context.getColor(R.color.widget_text), PanelConfig.DAY_TEXT);
    }

    @Config(qualifiers = "night")
    @Test public void nightTextMatchesTheResources() {
        assertEquals(context.getColor(R.color.widget_text), PanelConfig.NIGHT_TEXT);
    }

    @Test public void darkMeansLightTextReadsBetter() {
        assertTrue(PanelConfig.isDark(PanelConfig.NIGHT_SURFACE));
        assertFalse(PanelConfig.isDark(PanelConfig.DAY_SURFACE));
    }

    private PanelConfig.TextMode textMode(int day, int night) {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_DAY_BACKGROUND_COLOR, day);
        prefs.putInt(Prefs.KEY_BACKGROUND_COLOR, night);
        return prefs.panelConfig().textMode();
    }

    private SharedPreferences.Editor stored() {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("atlas_app_widget_settings", Context.MODE_PRIVATE).edit();
    }
}
