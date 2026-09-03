package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.List;

public final class SettingsBackupTest {
    @Test public void jsonRoundTripPreservesPortableSettings() throws Exception {
        SettingsBackup.Data original = data(true, true, 17, 321, 654);

        String json = SettingsBackup.encode(original, "1.2.3");
        SettingsBackup.Data restored = SettingsBackup.decode(json);

        assertTrue(restored.autoStart);
        assertEquals(List.of("com.example/.MainActivity"),
                restored.climateTransitionComponents);
        assertEquals(1_000, restored.climateTransitionDurationMs);
        assertTrue(restored.showOnlyInAppList);
        assertEquals(17, restored.appUiScaleTenths);
        assertEquals(80, restored.freeformHideThresholdPercent);
        assertEquals(Integer.valueOf(321), restored.positionX);
        assertEquals(Integer.valueOf(654), restored.positionY);
        assertEquals(List.of("com.example/.MainActivity", AppEntry.FUEL_COMPONENT_KEY),
                restored.selectedComponents);
        assertTrue(restored.content.showAppLabels);
        assertEquals(PanelConfig.APP_LABEL_TEXT_SIZE_DEFAULT_SP,
                restored.content.appLabelTextSizeSp);
        assertEquals(PanelConfig.APP_LABEL_GAP_DEFAULT_DP,
                restored.content.appLabelGapDp);
        assertEquals(PanelConfig.HANDLE_BOTTOM, restored.movement.dragHandlePosition);
        assertFalse(restored.systemStatus.showRam);
        assertEquals(800, restored.systemStatus.textWeight);
        assertEquals(0.466f, restored.fuel.multiplier, 0f);
        assertEquals(6, restored.geometry.columns);
        assertEquals(0xFF123456, restored.appearance.backgroundColor);
        JSONObject root = new JSONObject(json);
        assertEquals("atlas-app-widget-settings", root.getString("format"));
        assertEquals("1.2.3", root.getString("appVersion"));
        JSONObject settings = root.getJSONObject("settings");
        assertEquals(0.466, settings.getJSONObject("fuel").getDouble("multiplier"), 0d);
        assertEquals(80, settings.getInt("freeformHideThresholdPercent"));
        assertTrue(settings.getBoolean("showOnlyInAppList"));
        assertFalse(settings.has("useLaunchProxy"));
        assertFalse(settings.has("useDiagnosticLaunchActivity"));
        assertFalse(settings.has("serviceEnabled"));
        assertTrue(settings.has("customIcons"));
        assertTrue(settings.getJSONObject("customIcons").length() == 0);
    }

    @Test public void schema3LegacyLaunchModeMigratesToSelectedComponents() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(false, 15, null, null), "test"));
        root.getJSONObject("settings").put("useLaunchProxy", true);
        root.getJSONObject("settings").remove("showOnlyInAppList");
        root.getJSONObject("settings").remove("climateTransitionComponents");
        root.getJSONObject("settings").remove("climateTransitionDurationMs");
        root.put("schemaVersion", 3);

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(List.of("com.example/.MainActivity"),
                restored.climateTransitionComponents);
        assertEquals(1_000, restored.climateTransitionDurationMs);
        assertFalse(restored.showOnlyInAppList);
    }

    @Test public void climateTransitionSettingsRoundTrip() throws Exception {
        SettingsBackup.Data base = data(15, null, null);
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, base.showOnlyInAppList, base.appUiScaleTenths,
                base.freeformHideThresholdPercent,
                base.positionX, base.positionY, base.selectedComponents, base.shortcuts,
                List.of("com.example/.MainActivity"), 725, base.customIcons,
                base.content, base.movement,
                base.systemStatus, base.fuel, base.geometry, base.appearance);

        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(original, "test"));

        assertEquals(List.of("com.example/.MainActivity"),
                restored.climateTransitionComponents);
        assertEquals(725, restored.climateTransitionDurationMs);
    }

    @Test public void schema4RejectsClimateTransitionOutsideSelectedComponents() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONArray("climateTransitionComponents")
                .put("com.other/.Activity");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("climateTransitionComponents"));
    }

    @Test public void schema4RejectsClimateTransitionDurationOutsideRange() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").put("climateTransitionDurationMs", 1_001);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("climateTransitionDurationMs"));
    }

    @Test public void schema4RequiresClimateTransitionFields() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").remove("climateTransitionComponents");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("скрытия климат"));
    }

    @Test public void oldLegacyFlagsAreNotExported() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(true, 15, null, null), "test"));
        JSONObject settings = root.getJSONObject("settings");
        assertFalse(settings.has("useLaunchProxy"));
        assertFalse(settings.has("useDiagnosticLaunchActivity"));
    }

    @Test public void jsonRoundTripPreservesDefaultPosition() throws Exception {
        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(data(15, null, null), "test"));

        assertNull(restored.positionX);
        assertNull(restored.positionY);
    }

    @Test public void rejectsUnsupportedSchemaVersion() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.put("schemaVersion", 7);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("Неподдерживаемая версия"));
    }

    @Test public void appLabelTextSizeRoundTrips() throws Exception {
        SettingsBackup.Data base = data(15, null, null);
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, base.showOnlyInAppList, base.appUiScaleTenths,
                base.freeformHideThresholdPercent, base.positionX, base.positionY,
                base.selectedComponents, base.shortcuts, base.climateTransitionComponents,
                base.climateTransitionDurationMs, base.customIcons,
                new SettingsBackup.ContentData(true, PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP),
                base.movement, base.systemStatus, base.fuel, base.geometry, base.appearance);

        String json = SettingsBackup.encode(original, "test");
        SettingsBackup.Data restored = SettingsBackup.decode(json);

        assertEquals(PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP,
                restored.content.appLabelTextSizeSp);
        assertEquals(PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP,
                new JSONObject(json).getJSONObject("settings").getJSONObject("content")
                        .getInt("appLabelTextSizeSp"));
    }

    @Test public void olderBackupDefaultsMissingAppLabelTextSize() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.put("schemaVersion", 4);
        root.getJSONObject("settings").getJSONObject("content")
                .remove("appLabelTextSizeSp");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(PanelConfig.APP_LABEL_TEXT_SIZE_DEFAULT_SP,
                restored.content.appLabelTextSizeSp);
    }

    @Test public void appLabelGapRoundTrips() throws Exception {
        SettingsBackup.Data base = data(15, null, null);
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, base.showOnlyInAppList, base.appUiScaleTenths,
                base.freeformHideThresholdPercent, base.positionX, base.positionY,
                base.selectedComponents, base.shortcuts, base.climateTransitionComponents,
                base.climateTransitionDurationMs, base.customIcons,
                new SettingsBackup.ContentData(true, PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP,
                        PanelConfig.ONEOS_APP_LABEL_GAP_DP),
                base.movement, base.systemStatus, base.fuel, base.geometry, base.appearance);

        String json = SettingsBackup.encode(original, "test");
        SettingsBackup.Data restored = SettingsBackup.decode(json);

        assertEquals(PanelConfig.ONEOS_APP_LABEL_GAP_DP, restored.content.appLabelGapDp);
        assertEquals(PanelConfig.ONEOS_APP_LABEL_GAP_DP,
                new JSONObject(json).getJSONObject("settings").getJSONObject("content")
                        .getInt("appLabelGapDp"));
    }

    @Test public void olderBackupDefaultsMissingAppLabelGap() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.put("schemaVersion", 5);
        root.getJSONObject("settings").getJSONObject("content").remove("appLabelGapDp");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(PanelConfig.APP_LABEL_GAP_DEFAULT_DP, restored.content.appLabelGapDp);
    }

    @Test public void currentBackupRequiresAppLabelGap() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONObject("content").remove("appLabelGapDp");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("интервал до названий"));
    }

    @Test public void rejectsAppLabelGapOutsideRange() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONObject("content")
                .put("appLabelGapDp", PanelConfig.APP_LABEL_GAP_MAX_DP + 1);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("appLabelGapDp"));
    }

    @Test public void currentBackupRequiresAppLabelTextSize() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONObject("content")
                .remove("appLabelTextSizeSp");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("размер названий"));
    }

    @Test public void rejectsAppLabelTextSizeOutsideRange() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONObject("content")
                .put("appLabelTextSizeSp", PanelConfig.APP_LABEL_TEXT_SIZE_MAX_SP + 1);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("appLabelTextSizeSp"));
    }

    @Test public void shortcutCatalogRoundTripsAndOrphanSelectionIsFiltered() throws Exception {
        String uri = "#Intent;action=android.intent.action.VIEW;"
                + "component=com.salat.gsplit/.PresetLauncherActivity;S.id=7;end";
        ShortcutSpec shortcut = new ShortcutSpec(
                ShortcutSpec.stableKey(uri), "Preset 7", uri,
                "com.salat.gsplit/.PresetLauncherActivity");
        SettingsBackup.Data base = data(15, null, null);
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, base.showOnlyInAppList,
                base.appUiScaleTenths, base.freeformHideThresholdPercent,
                base.positionX, base.positionY,
                List.of(shortcut.key, "com.example/.MainActivity"), List.of(shortcut),
                List.of(), Prefs.CLIMATE_TRANSITION_DURATION_DEFAULT_MS, Map.of(),
                base.content, base.movement, base.systemStatus, base.fuel, base.geometry,
                base.appearance);

        JSONObject encoded = new JSONObject(SettingsBackup.encode(original, "test"));
        encoded.getJSONObject("settings").getJSONArray("selectedComponents")
                .put(ShortcutSpec.KEY_PREFIX + "orphan");
        SettingsBackup.Data restored = SettingsBackup.decode(encoded.toString());

        assertEquals(List.of(shortcut.key, "com.example/.MainActivity"),
                restored.selectedComponents);
        assertEquals(1, restored.shortcuts.size());
        assertEquals(uri, restored.shortcuts.get(0).intentUri);
    }

    @Test public void customIconsRoundTripAsPortableBase64Payload() throws Exception {
        SettingsBackup.Data base = data(15, null, null);
        byte[] icon = new byte[]{0, 1, 2, 3, 4};
        String uri = "#Intent;action=android.intent.action.VIEW;"
                + "component=com.salat.gsplit/.PresetLauncherActivity;end";
        ShortcutSpec shortcut = new ShortcutSpec(
                ShortcutSpec.stableKey(uri), "Preset", uri,
                "com.salat.gsplit/.PresetLauncherActivity");
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, base.showOnlyInAppList,
                base.appUiScaleTenths, base.freeformHideThresholdPercent,
                base.positionX, base.positionY, List.of(shortcut.key), List.of(shortcut),
                List.of(), Prefs.CLIMATE_TRANSITION_DURATION_DEFAULT_MS,
                Map.of(shortcut.key, icon), base.content, base.movement,
                base.systemStatus, base.fuel, base.geometry, base.appearance);

        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(original, "test"));

        assertEquals(1, restored.customIcons.size());
        assertEquals(List.of(0, 1, 2, 3, 4),
                toList(restored.customIcons.get(shortcut.key)));
    }

    @Test public void gsplitPresetTitleExposesBothAppLabels() {
        assertEquals(List.of("YT Music", "Atlas App Widget"),
                ShortcutSpec.gsplitAppLabels(
                        "ID1: [YT Music] - 1x1 - [Atlas App Widget]"));
        assertEquals(List.of("Navigator", "Poweramp"),
                ShortcutSpec.gsplitAppLabels(
                        "ID2: [Navigator][▶] - 1x2[D] - [▶][Poweramp]"));
        assertEquals(List.of(), ShortcutSpec.gsplitAppLabels("Last launched"));
    }

    private static List<Integer> toList(byte[] bytes) {
        java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
        for (byte value : bytes) result.add((int) value);
        return result;
    }

    @Test public void olderJsonDefaultsMissingFreeformThreshold() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").remove("freeformHideThresholdPercent");

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertEquals(WindowVisibilityPolicy.DEFAULT_HIDE_THRESHOLD_PERCENT,
                restored.freeformHideThresholdPercent);
    }

    @Test public void rejectsValuesOutsideUiLimits() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONObject("geometry")
                .put("iconSizeDp", 10_000);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("iconSizeDp"));
    }

    @Test public void rejectsCoercedBooleanStrings() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").put("autoStart", "true");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("true или false"));
    }

    @Test public void rejectsDuplicateSelectedComponents() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").getJSONArray("selectedComponents")
                .put("com.example/.MainActivity");

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("Повторяющийся"));
    }

    private static SettingsBackup.Data data(int scale, Integer x, Integer y)
            throws IOException {
        return data(false, scale, x, y);
    }

    private static SettingsBackup.Data data(boolean climateTransitionEnabled, int scale, Integer x,
            Integer y) throws IOException {
        return data(climateTransitionEnabled, false, scale, x, y);
    }

    private static SettingsBackup.Data data(boolean climateTransitionEnabled,
            boolean showOnlyInAppList, int scale, Integer x, Integer y) throws IOException {
        return new SettingsBackup.Data(
                true,
                showOnlyInAppList,
                scale,
                80,
                x,
                y,
                List.of("com.example/.MainActivity", AppEntry.FUEL_COMPONENT_KEY),
                List.of(),
                climateTransitionEnabled ? List.of("com.example/.MainActivity") : List.of(),
                Prefs.CLIMATE_TRANSITION_DURATION_DEFAULT_MS,
                Map.of(),
                new SettingsBackup.ContentData(true),
                new SettingsBackup.MovementData(false, PanelConfig.HANDLE_BOTTOM),
                new SettingsBackup.SystemStatusData(
                        true,
                        true,
                        false,
                        true,
                        PanelConfig.STATUS_TOP,
                        8,
                        14,
                        800),
                new SettingsBackup.FuelData(true, 0.466f, 3.25f),
                new SettingsBackup.GeometryData(80, 6, 2, 96, 20, 16, 10),
                new SettingsBackup.AppearanceData(
                        0xFF123456,
                        210,
                        true,
                        3,
                        180,
                        0xFF7893A0,
                        12));
    }
}
