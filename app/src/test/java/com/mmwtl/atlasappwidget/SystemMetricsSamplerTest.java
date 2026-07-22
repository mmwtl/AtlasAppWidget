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
        assertEquals(3, SystemMetricsSampler.thermalTypeScore("soc-thermal"));
        assertEquals(4, SystemMetricsSampler.thermalTypeScore("cpu0-thermal"));
    }
}
