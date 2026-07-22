package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class SystemMetricsSamplerTest {
    @Test
    public void parsesAggregateCpuTicksAndCalculatesBusyPercent() {
        SystemMetricsSampler.CpuTicks previous = SystemMetricsSampler.parseCpuTicks(
                "cpu  100 10 40 800 20 5 5 0 0 0"
        );
        SystemMetricsSampler.CpuTicks current = SystemMetricsSampler.parseCpuTicks(
                "cpu  140 10 60 860 20 5 5 0 0 0"
        );

        assertNotNull(previous);
        assertNotNull(current);
        assertEquals(50, SystemMetricsSampler.cpuPercent(previous, current));
    }

    @Test
    public void firstCpuSampleIsUnavailable() {
        SystemMetricsSampler.CpuTicks current = SystemMetricsSampler.parseCpuTicks(
                "cpu  100 10 40 800 20 5 5 0 0 0"
        );

        assertEquals(SystemStatusSnapshot.UNAVAILABLE,
                SystemMetricsSampler.cpuPercent(null, current));
    }

    @Test
    public void thermalSensorPreferenceRejectsBatteryAndPrefersCpu() {
        assertEquals(0, SystemMetricsSampler.thermalTypeScore("battery"));
        assertEquals(0, SystemMetricsSampler.thermalTypeScore("gpu-thermal"));
        assertEquals(80, SystemMetricsSampler.thermalTypeScore("soc-thermal"));
        assertEquals(90, SystemMetricsSampler.thermalTypeScore("big-cluster"));
        assertEquals(100, SystemMetricsSampler.thermalTypeScore("cpu0-thermal"));
    }

    @Test
    public void parsesCommonThermalValueFormats() {
        assertEquals(Integer.valueOf(54),
                SystemMetricsSampler.parseTemperatureCelsius("54000"));
        assertEquals(Integer.valueOf(54),
                SystemMetricsSampler.parseTemperatureCelsius("538"));
        assertEquals(Integer.valueOf(54),
                SystemMetricsSampler.parseTemperatureCelsius("53.6"));
        assertEquals(null,
                SystemMetricsSampler.parseTemperatureCelsius("0"));
        assertEquals(null,
                SystemMetricsSampler.parseTemperatureCelsius("not-a-temperature"));
    }
}
