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
        assertTrue(restored.useLaunchProxy);
        assertFalse(restored.useDiagnosticLaunchActivity);
        assertTrue(restored.showOnlyInAppList);
        assertEquals(17, restored.appUiScaleTenths);
        assertEquals(80, restored.freeformHideThresholdPercent);
        assertEquals(Integer.valueOf(321), restored.positionX);
        assertEquals(Integer.valueOf(654), restored.positionY);
        assertEquals(List.of("com.example/.MainActivity", AppEntry.FUEL_COMPONENT_KEY),
                restored.selectedComponents);
        assertTrue(restored.content.showAppLabels);
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
        assertFalse(settings.has("serviceEnabled"));
        assertTrue(settings.has("customIcons"));
        assertTrue(settings.getJSONObject("customIcons").length() == 0);
    }

    @Test public void olderJsonDefaultsMissingLaunchProxyToFalse() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(false, 15, null, null), "test"));
        root.getJSONObject("settings").remove("useLaunchProxy");
        root.getJSONObject("settings").remove("showOnlyInAppList");
        root.put("schemaVersion", 1);

        SettingsBackup.Data restored = SettingsBackup.decode(root.toString());

        assertFalse(restored.useLaunchProxy);
        assertFalse(restored.useDiagnosticLaunchActivity);
        assertFalse(restored.showOnlyInAppList);
    }

    @Test public void diagnosticLaunchModeRoundTrips() throws Exception {
        SettingsBackup.Data base = data(15, null, null);
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, false, true, base.showOnlyInAppList,
                base.appUiScaleTenths, base.freeformHideThresholdPercent,
                base.positionX, base.positionY, base.selectedComponents,
                base.shortcuts, base.customIcons, base.content, base.movement,
                base.systemStatus, base.fuel, base.geometry, base.appearance);

        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(original, "test"));

        assertTrue(restored.useDiagnosticLaunchActivity);
        assertFalse(restored.useLaunchProxy);
    }

    @Test public void rejectsBackupWithBothLaunchModesEnabled() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.getJSONObject("settings").put("useLaunchProxy", true)
                .put("useDiagnosticLaunchActivity", true);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("одновременно"));
    }

    @Test public void jsonRoundTripPreservesDefaultPosition() throws Exception {
        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(data(15, null, null), "test"));

        assertNull(restored.positionX);
        assertNull(restored.positionY);
    }

    @Test public void rejectsUnsupportedSchemaVersion() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.put("schemaVersion", 4);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("Неподдерживаемая версия"));
    }

    @Test public void shortcutCatalogRoundTripsAndOrphanSelectionIsFiltered() throws Exception {
        String uri = "#Intent;action=android.intent.action.VIEW;"
                + "component=com.salat.gsplit/.PresetLauncherActivity;S.id=7;end";
        ShortcutSpec shortcut = new ShortcutSpec(
                ShortcutSpec.stableKey(uri), "Preset 7", uri,
                "com.salat.gsplit/.PresetLauncherActivity");
        SettingsBackup.Data base = data(15, null, null);
        SettingsBackup.Data original = new SettingsBackup.Data(
                base.autoStart, base.useLaunchProxy, base.showOnlyInAppList,
                base.appUiScaleTenths, base.freeformHideThresholdPercent,
                base.positionX, base.positionY,
                List.of(shortcut.key, "com.example/.MainActivity"), List.of(shortcut),
                base.content, base.movement, base.systemStatus, base.fuel,
                base.geometry, base.appearance);

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
                base.autoStart, base.useLaunchProxy, base.showOnlyInAppList,
                base.appUiScaleTenths, base.freeformHideThresholdPercent,
                base.positionX, base.positionY, List.of(shortcut.key), List.of(shortcut),
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

    private static SettingsBackup.Data data(boolean useLaunchProxy, int scale, Integer x,
            Integer y) throws IOException {
        return data(useLaunchProxy, false, scale, x, y);
    }

    private static SettingsBackup.Data data(boolean useLaunchProxy, boolean showOnlyInAppList,
            int scale, Integer x, Integer y) throws IOException {
        return new SettingsBackup.Data(
                true,
                useLaunchProxy,
                showOnlyInAppList,
                scale,
                80,
                x,
                y,
                List.of("com.example/.MainActivity", AppEntry.FUEL_COMPONENT_KEY),
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
