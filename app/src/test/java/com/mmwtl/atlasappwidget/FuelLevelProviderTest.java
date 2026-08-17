package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FuelLevelProviderTest {
    @Test
    public void fuelPollingRunsOncePerMinuteAfterImmediateStartRequest() {
        assertEquals(60_000L, FuelLevelProvider.SENSOR_REFRESH_INTERVAL_MS);
    }

    @Test
    public void emptySensorRangeKeepsFourLiterReserve() {
        FuelLevelProvider.Reading reading = FuelLevelProvider.fromSensorValue(0f);

        assertNotNull(reading);
        assertEquals(4, reading.liters);
        assertEquals(54, reading.tankCapacityLiters);
        assertEquals("<4", reading.filledDisplayValue());
        assertEquals(">50", reading.freeDisplayValue());
        assertEquals(7, reading.percent);
    }

    @Test
    public void fullApiPercentageUsesNewFormulaAndDerivedTankCapacity() {
        FuelLevelProvider.Reading reading = FuelLevelProvider.fromSensorValue(100f);

        assertNotNull(reading);
        assertEquals(54, reading.liters);
        assertEquals(54, reading.tankCapacityLiters);
        assertEquals(100, reading.percent);
        assertEquals(0, reading.freeLiters);
    }

    @Test
    public void responseUsesStringExtrasAndRoundsLiters() {
        Float sensorValue =
                FuelLevelProvider.parseSensorValue("4211968", "25.4");
        assertNotNull(sensorValue);
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.fromSensorValue(sensorValue);

        assertNotNull(reading);
        assertEquals(17, reading.liters);
        assertEquals(31, reading.percent);
        assertEquals(37, reading.freeLiters);
    }

    @Test
    public void responseAlsoAcceptsNumericExtras() {
        assertEquals(
                25.4f,
                FuelLevelProvider.parseSensorValue(4_211_968, 25.4f),
                0.001f
        );
    }

    @Test
    public void rangeResponseUsesFx11EnduranceMileageSensor() {
        Float sensorValue = FuelLevelProvider.parseRangeSensorValue("1054720", "312.9");

        assertNotNull(sensorValue);
        assertEquals(312, FuelLevelProvider.rangeKmFromSensorValue(sensorValue));
        assertNull(FuelLevelProvider.parseRangeSensorValue("4211968", "312.9"));
    }

    @Test
    public void fx11TreatsRangeAtOrBelowOneAsUnavailable() {
        assertEquals(SystemStatusSnapshot.UNAVAILABLE,
                FuelLevelProvider.rangeKmFromSensorValue(1f));
        assertEquals(SystemStatusSnapshot.UNAVAILABLE,
                FuelLevelProvider.rangeKmFromSensorValue(Float.NaN));
    }

    @Test
    public void customMultiplierAndOffsetAreAppliedBeforeClamping() {
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.fromSensorValue(20f, 0.5f, 2f);

        assertNotNull(reading);
        assertEquals(12, reading.liters);
        assertEquals(52, reading.tankCapacityLiters);
        assertEquals(23, reading.percent);
        assertEquals(40, reading.freeLiters);
    }

    @Test
    public void negativeMultiplierSupportsInvertedSensors() {
        FuelLevelProvider.Reading reading =
                FuelLevelProvider.fromSensorValue(10f, -1f, 54f);

        assertNotNull(reading);
        assertEquals(44, reading.liters);
        assertEquals(54, reading.tankCapacityLiters);
        assertEquals(81, reading.percent);
    }

    @Test
    public void outOfRangeHighValueIsClampedToFullTank() {
        FuelLevelProvider.Reading reading = FuelLevelProvider.fromSensorValue(200f);

        assertNotNull(reading);
        assertEquals(54, reading.liters);
        assertEquals(54, reading.tankCapacityLiters);
        assertEquals(100, reading.percent);
    }

    @Test
    public void rejectsWrongSensorAndInvalidValues() {
        assertNull(FuelLevelProvider.parseSensorValue("42", "20"));
        assertNull(FuelLevelProvider.parseSensorValue("4211968", "not-a-number"));
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
