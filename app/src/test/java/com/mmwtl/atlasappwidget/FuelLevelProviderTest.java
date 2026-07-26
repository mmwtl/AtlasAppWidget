package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        Float sensorValue =
                FuelLevelProvider.parseSensorValue("1050112", "25.4");
        assertNotNull(sensorValue);
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.fromSensorValue(sensorValue);

        assertNotNull(reading);
        assertEquals(29, reading.liters);
        assertEquals(54, reading.percent);
        assertEquals(25, reading.freeLiters);
    }

    @Test
    public void customMultiplierAndOffsetAreAppliedBeforeClamping() {
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.fromSensorValue(20f, 0.5f, 2f);

        assertNotNull(reading);
        assertEquals(12, reading.liters);
        assertEquals(22, reading.percent);
        assertEquals(42, reading.freeLiters);
    }

    @Test
    public void negativeMultiplierSupportsInvertedSensors() {
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.fromSensorValue(10f, -1f, 54f);

        assertNotNull(reading);
        assertEquals(44, reading.liters);
        assertEquals(81, reading.percent);
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
        assertNull(FuelLevelProvider.parseSensorValue("42", "20"));
        assertNull(FuelLevelProvider.parseSensorValue("1050112", "not-a-number"));
        assertNull(FuelLevelProvider.fromSensorValue(Float.NaN));
        assertNull(FuelLevelProvider.fromSensorValue(10f, Float.NaN, 0f));
    }

    @Test
    public void recentReadingRemainsFreshThroughTimeoutBoundary() {
        long receivedAt = 10_000L;

        assertTrue(FuelLevelProvider.isReadingFresh(receivedAt, receivedAt));
        assertTrue(FuelLevelProvider.isReadingFresh(
                receivedAt,
                receivedAt + FuelLevelProvider.READING_STALE_AFTER_MS
        ));
    }

    @Test
    public void missingFutureAndExpiredReadingsAreNotFresh() {
        long receivedAt = 10_000L;

        assertFalse(FuelLevelProvider.isReadingFresh(0L, receivedAt));
        assertFalse(FuelLevelProvider.isReadingFresh(receivedAt, receivedAt - 1L));
        assertFalse(FuelLevelProvider.isReadingFresh(
                receivedAt,
                receivedAt + FuelLevelProvider.READING_STALE_AFTER_MS + 1L
        ));
    }
}
