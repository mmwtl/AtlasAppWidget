package com.mmwtl.atlasappwidget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Best-effort unprivileged system metrics. Unsupported OEM data is reported as unavailable. */
final class SystemMetricsSampler {
    private static final File PROC_STAT = new File("/proc/stat");
    private static final File THERMAL_ROOT = new File("/sys/class/thermal");

    private final Context context;
    private final ActivityManager activityManager;
    private CpuTicks previousCpuTicks;
    private File thermalTemperatureFile;
    private boolean thermalScanComplete;

    SystemMetricsSampler(Context context) {
        this.context = context.getApplicationContext();
        activityManager = this.context.getSystemService(ActivityManager.class);
    }

    SystemStatusSnapshot sample() {
        return new SystemStatusSnapshot(
                readCpuPercent(),
                readRamPercent(),
                readTemperatureCelsius()
        );
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

    private int readTemperatureCelsius() {
        Integer thermal = readThermalTemperature();
        if (thermal != null) {
            return thermal;
        }
        try {
            Intent battery = context.registerReceiver(
                    null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );
            if (battery == null) {
                return SystemStatusSnapshot.UNAVAILABLE;
            }
            int tenths = battery.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Integer.MIN_VALUE
            );
            if (tenths == Integer.MIN_VALUE || tenths == 0) {
                return SystemStatusSnapshot.UNAVAILABLE;
            }
            int celsius = Math.round(tenths / 10f);
            return isPlausibleTemperature(celsius)
                    ? celsius : SystemStatusSnapshot.UNAVAILABLE;
        } catch (RuntimeException error) {
            return SystemStatusSnapshot.UNAVAILABLE;
        }
    }

    private Integer readThermalTemperature() {
        if (!thermalScanComplete) {
            thermalTemperatureFile = findPreferredThermalFile();
            thermalScanComplete = true;
        }
        if (thermalTemperatureFile == null) {
            return null;
        }
        Integer value = readInteger(thermalTemperatureFile);
        if (value == null) {
            return null;
        }
        int absolute = Math.abs(value);
        int celsius = absolute >= 10_000
                ? Math.round(value / 1_000f)
                : absolute >= 200
                ? Math.round(value / 10f)
                : value;
        return isPlausibleTemperature(celsius) ? celsius : null;
    }

    private File findPreferredThermalFile() {
        File[] zones;
        try {
            zones = THERMAL_ROOT.listFiles(file -> file.getName().startsWith("thermal_zone"));
        } catch (SecurityException error) {
            return null;
        }
        if (zones == null) {
            return null;
        }
        File best = null;
        int bestScore = 0;
        for (File zone : zones) {
            String type = readLine(new File(zone, "type"));
            int score = thermalTypeScore(type);
            File temperature = new File(zone, "temp");
            if (score > bestScore && readInteger(temperature) != null) {
                best = temperature;
                bestScore = score;
            }
        }
        return best;
    }

    static int thermalTypeScore(String rawType) {
        if (rawType == null) {
            return 0;
        }
        String type = rawType.toLowerCase(Locale.ROOT);
        if (type.contains("battery")) {
            return 0;
        }
        if (type.contains("cpu")) {
            return 4;
        }
        if (type.contains("soc")) {
            return 3;
        }
        if (type.contains("tsens") || type.contains("cluster")
                || type.contains("package")) {
            return 2;
        }
        return type.contains("ap") ? 1 : 0;
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

    private static boolean isPlausibleTemperature(int celsius) {
        return celsius >= -40 && celsius <= 150;
    }

    private static Integer readInteger(File file) {
        String value = readLine(file);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static String readLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException | SecurityException error) {
            return null;
        }
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
