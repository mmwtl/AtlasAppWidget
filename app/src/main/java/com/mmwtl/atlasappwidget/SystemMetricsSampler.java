package com.mmwtl.atlasappwidget;

import android.app.ActivityManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/** Best-effort unprivileged CPU and RAM metrics. */
final class SystemMetricsSampler {
    private static final File PROC_STAT = new File("/proc/stat");

    private final ActivityManager activityManager;
    private CpuTicks previousCpuTicks;

    SystemMetricsSampler(Context context) {
        activityManager = context.getApplicationContext()
                .getSystemService(ActivityManager.class);
    }

    SystemStatusSnapshot sample() {
        return new SystemStatusSnapshot(readCpuPercent(), readRamPercent());
    }

    void resetCpuBaseline() {
        previousCpuTicks = null;
    }

    private int readCpuPercent() {
        CpuTicks current;
        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_STAT))) {
            current = parseCpuTicks(reader.readLine());
        } catch (IOException | SecurityException error) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
        if (current == null) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
        int result = cpuPercent(previousCpuTicks, current);
        previousCpuTicks = current;
        return result;
    }

    private int readRamPercent() {
        if (activityManager == null) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memory);
        if (memory.totalMem <= 0) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
        long used = Math.max(0, memory.totalMem - memory.availMem);
        return clampPercent(Math.round(used * 100f / memory.totalMem));
    }

    static CpuTicks parseCpuTicks(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 5 || !"cpu".equals(parts[0])) {
            return null;
        }
        try {
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long ioWait = part(parts, 5);
            long irq = part(parts, 6);
            long softIrq = part(parts, 7);
            long steal = part(parts, 8);
            return new CpuTicks(
                    user + nice + system + idle + ioWait + irq + softIrq + steal,
                    idle + ioWait
            );
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static int cpuPercent(CpuTicks previous, CpuTicks current) {
        if (previous == null || current == null) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
        long totalDelta = current.total - previous.total;
        long idleDelta = current.idle - previous.idle;
        if (totalDelta <= 0 || idleDelta < 0) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
        return clampPercent(Math.round((totalDelta - idleDelta) * 100f / totalDelta));
    }

    private static long part(String[] parts, int index) {
        return index < parts.length ? Long.parseLong(parts[index]) : 0;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    static final class CpuTicks {
        final long total;
        final long idle;

        CpuTicks(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
    }
}
