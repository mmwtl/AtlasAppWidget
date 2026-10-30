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
