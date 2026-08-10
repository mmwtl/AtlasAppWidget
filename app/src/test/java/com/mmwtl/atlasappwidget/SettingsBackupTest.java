package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public final class SettingsBackupTest {
    @Test public void jsonRoundTripPreservesPortableSettings() throws Exception {
        SettingsBackup.Data original = data(17, 321, 654);

        String json = SettingsBackup.encode(original, "1.2.3");
        SettingsBackup.Data restored = SettingsBackup.decode(json);

        assertTrue(restored.autoStart);
        assertEquals(17, restored.appUiScaleTenths);
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
        assertFalse(settings.has("serviceEnabled"));
        assertFalse(settings.has("customIcons"));
    }

    @Test public void jsonRoundTripPreservesDefaultPosition() throws Exception {
        SettingsBackup.Data restored = SettingsBackup.decode(
                SettingsBackup.encode(data(15, null, null), "test"));

        assertNull(restored.positionX);
        assertNull(restored.positionY);
    }

    @Test public void rejectsUnsupportedSchemaVersion() throws Exception {
        JSONObject root = new JSONObject(SettingsBackup.encode(data(15, null, null), "test"));
        root.put("schemaVersion", 2);

        IOException error = assertThrows(IOException.class,
                () -> SettingsBackup.decode(root.toString()));

        assertTrue(error.getMessage().contains("Неподдерживаемая версия"));
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
        return new SettingsBackup.Data(
                true,
                scale,
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
