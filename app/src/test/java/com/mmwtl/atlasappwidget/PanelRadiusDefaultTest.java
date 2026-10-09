package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class PanelRadiusDefaultTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void cleanInstallUsesOneOsCardRadius() throws Exception {
        Prefs prefs = new Prefs(context);

        assertFalse(prefs.raw().contains(Prefs.KEY_PANEL_RADIUS_DP));
        assertEquals(24, PanelConfig.PANEL_RADIUS_DEFAULT_DP);
        assertEquals(PanelConfig.PANEL_RADIUS_DEFAULT_DP, prefs.panelConfig().panelRadiusDp);
        assertEquals(PanelConfig.PANEL_RADIUS_DEFAULT_DP,
                SettingsBackup.capture(prefs).appearance.panelRadiusDp);
    }

    @Test
    public void cleanInstallKeepsNewDefaultAfterOtherSettingsAreSaved() {
        new Prefs(context).putInt(Prefs.KEY_COLUMNS, 5);

        assertEquals(PanelConfig.PANEL_RADIUS_DEFAULT_DP,
                new Prefs(context).panelConfig().panelRadiusDp);
    }

    @Test
    public void upgradeWithoutStoredRadiusKeepsLegacyDefault() {
        storage().edit().putInt(Prefs.KEY_COLUMNS, 5).commit();

        Prefs prefs = new Prefs(context);

        assertEquals(PanelConfig.PANEL_RADIUS_LEGACY_DEFAULT_DP,
                prefs.getInt(Prefs.KEY_PANEL_RADIUS_DP, -1));
        assertEquals(8, prefs.panelConfig().panelRadiusDp);
    }

    @Test
    public void upgradeKeepsStoredRadius() {
        storage().edit().putInt(Prefs.KEY_PANEL_RADIUS_DP, 30).commit();

        assertEquals(30, new Prefs(context).panelConfig().panelRadiusDp);
    }

    private SharedPreferences storage() {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("atlas_app_widget_settings", Context.MODE_PRIVATE);
    }
}
