package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class FuelLevelProviderTest {
    @Test
    public void emptySensorRangeKeepsFourLiterReserve() {
        FuelLevelProvider.Reading reading = FuelLevelProvider.fromSensorValue(0f);

        assertNotNull(reading);
        assertEquals(4, reading.liters);
        assertEquals(7, reading.percent);
    }

    @Test
    public void fullSensorRangeMapsToTankCapacity() {
        FuelLevelProvider.Reading reading = FuelLevelProvider.fromSensorValue(50f);

        assertNotNull(reading);
        assertEquals(54, reading.liters);
        assertEquals(100, reading.percent);
    }

    @Test
    public void responseUsesStringExtrasAndRoundsLiters() {
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.parseResponse("1050112", "25.4");

        assertNotNull(reading);
        assertEquals(29, reading.liters);
        assertEquals(54, reading.percent);
    }

    @Test
    public void outOfRangeHighValueIsClampedToFullTank() {
        FuelLevelProvider.Reading reading = FuelLevelProvider.fromSensorValue(80f);

        assertNotNull(reading);
        assertEquals(54, reading.liters);
        assertEquals(100, reading.percent);
    }

    @Test
    public void rejectsWrongSensorAndInvalidValues() {
        assertNull(FuelLevelProvider.parseResponse("42", "20"));
        assertNull(FuelLevelProvider.parseResponse("1050112", "not-a-number"));
        assertNull(FuelLevelProvider.fromSensorValue(-1f));
        assertNull(FuelLevelProvider.fromSensorValue(Float.NaN));
    }
}
