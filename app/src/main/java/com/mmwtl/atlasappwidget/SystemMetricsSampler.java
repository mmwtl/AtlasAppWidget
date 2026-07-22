package com.mmwtl.atlasappwidget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.HardwarePropertiesManager;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Best-effort unprivileged system metrics. Unsupported OEM data is reported as unavailable. */
final class SystemMetricsSampler {
    static final String TEMPERATURE_SOURCE_AUTO = "auto";
    static final String TEMPERATURE_SOURCE_HARDWARE_CPU = "hardware_cpu";
    static final String TEMPERATURE_SOURCE_BATTERY = "battery";
    private static final String TEMPERATURE_SOURCE_THERMAL_PREFIX = "thermal:";

    private static final File PROC_STAT = new File("/proc/stat");
    private static final File THERMAL_ROOT = new File("/sys/class/thermal");
    private static final long THERMAL_RESCAN_INTERVAL_MS = 30_000L;

    private final Context context;
    private final Prefs prefs;
    private final ActivityManager activityManager;
    private CpuTicks previousCpuTicks;
    private File automaticThermalFile;
    private long nextThermalScanElapsedMs;
    private boolean hardwareCpuApiDenied;

    SystemMetricsSampler(Context context, Prefs prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
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
        String source = prefs.getString(
                Prefs.KEY_TEMPERATURE_SOURCE,
                TEMPERATURE_SOURCE_AUTO
        );
        Integer value;
        if (TEMPERATURE_SOURCE_HARDWARE_CPU.equals(source)) {
            value = readHardwareCpuTemperature();
        } else if (TEMPERATURE_SOURCE_BATTERY.equals(source)) {
            value = readBatteryTemperature();
        } else if (source.startsWith(TEMPERATURE_SOURCE_THERMAL_PREFIX)) {
            value = readSelectedThermalTemperature(source);
        } else {
            value = readAutomaticTemperature();
        }
        return value == null ? SystemStatusSnapshot.UNAVAILABLE : value;
    }

    private Integer readAutomaticTemperature() {
        Integer hardware = readHardwareCpuTemperature();
        if (hardware != null) {
            return hardware;
        }
        Integer thermal = readAutomaticThermalTemperature();
        return thermal != null ? thermal : readBatteryTemperature();
    }

    private Integer readHardwareCpuTemperature() {
        if (hardwareCpuApiDenied) {
            return null;
        }
        HardwarePropertiesManager manager = context.getSystemService(
                HardwarePropertiesManager.class);
        if (manager == null) {
            return null;
        }
        try {
            float[] values = manager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
            );
            Integer hottest = null;
            for (float value : values) {
                if (Float.isFinite(value) && value != 0f
                        && isPlausibleTemperature(value)) {
                    int rounded = Math.round(value);
                    hottest = hottest == null ? rounded : Math.max(hottest, rounded);
                }
            }
            return hottest;
        } catch (SecurityException error) {
            hardwareCpuApiDenied = true;
            return null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Integer readBatteryTemperature() {
        try {
            Intent battery = context.registerReceiver(
                    null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );
            if (battery == null) {
                return null;
            }
            int tenths = battery.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Integer.MIN_VALUE
            );
            if (tenths == Integer.MIN_VALUE || tenths == 0) {
                return null;
            }
            int celsius = Math.round(tenths / 10f);
            return isPlausibleTemperature(celsius) ? celsius : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Integer readAutomaticThermalTemperature() {
        if (automaticThermalFile != null) {
            Integer cached = readTemperatureFile(automaticThermalFile);
            if (cached != null) {
                return cached;
            }
            automaticThermalFile = null;
            nextThermalScanElapsedMs = 0;
        }

        long now = SystemClock.elapsedRealtime();
        if (now < nextThermalScanElapsedMs) {
            return null;
        }
        nextThermalScanElapsedMs = now + THERMAL_RESCAN_INTERVAL_MS;
        ThermalScan scan = scanThermalSources();
        TemperatureSource best = preferredThermalSource(scan.sources);
        if (best == null) {
            return null;
        }
        automaticThermalFile = best.temperatureFile;
        return best.temperatureCelsius;
    }

    private Integer readSelectedThermalTemperature(String source) {
        String zoneName = source.substring(TEMPERATURE_SOURCE_THERMAL_PREFIX.length());
        if (!isSafeThermalZoneName(zoneName)) {
            return null;
        }
        return readTemperatureFile(new File(new File(THERMAL_ROOT, zoneName), "temp"));
    }

    TemperatureDiagnostics temperatureDiagnostics() {
        Integer hardware = readHardwareCpuTemperature();
        Integer battery = readBatteryTemperature();
        ThermalScan scan = scanThermalSources();
        TemperatureSource preferred = preferredThermalSource(scan.sources);
        String automaticSourceId = hardware != null
                ? TEMPERATURE_SOURCE_HARDWARE_CPU
                : preferred != null
                ? preferred.id
                : battery != null
                ? TEMPERATURE_SOURCE_BATTERY
                : null;
        return new TemperatureDiagnostics(
                hardware,
                battery,
                scan.rootAccessible,
                scan.sources,
                automaticSourceId
        );
    }

    private static TemperatureSource preferredThermalSource(List<TemperatureSource> sources) {
        TemperatureSource best = null;
        for (TemperatureSource source : sources) {
            if (source.temperatureCelsius == null || source.score <= 0) {
                continue;
            }
            if (best == null || source.score > best.score) {
                best = source;
            }
        }
        return best;
    }

    private static ThermalScan scanThermalSources() {
        File[] zones;
        try {
            zones = THERMAL_ROOT.listFiles(file -> file.getName().startsWith("thermal_zone"));
        } catch (SecurityException error) {
            return new ThermalScan(false, new ArrayList<>());
        }
        if (zones == null) {
            return new ThermalScan(false, new ArrayList<>());
        }
        Arrays.sort(zones, Comparator.comparing(File::getName));
        ArrayList<TemperatureSource> sources = new ArrayList<>();
        for (File zone : zones) {
            String type = readLine(new File(zone, "type"));
            File temperatureFile = new File(zone, "temp");
            sources.add(new TemperatureSource(
                    thermalSourceId(zone.getName()),
                    zone.getName(),
                    type == null || type.trim().isEmpty() ? "?" : type.trim(),
                    readTemperatureFile(temperatureFile),
                    thermalTypeScore(type),
                    temperatureFile
            ));
        }
        return new ThermalScan(true, sources);
    }

    private static String thermalSourceId(String zoneName) {
        return TEMPERATURE_SOURCE_THERMAL_PREFIX + zoneName;
    }

    private static boolean isSafeThermalZoneName(String value) {
        return value.matches("thermal_zone[0-9]+");
    }

    static int thermalTypeScore(String rawType) {
        if (rawType == null) {
            return 0;
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()
                || containsAny(type, "battery", "batt", "gpu", "skin", "ambient",
                "charger", "usb", "modem", "wifi", "display", "camera")) {
            return 0;
        }
        if (type.contains("cpu") || type.contains("mtktscpu")) {
            return 100;
        }
        if (containsAny(type, "cluster", "package", "little", "big")) {
            return 90;
        }
        if (type.contains("soc") || type.contains("mtktsap")
                || hasToken(type, "ap")) {
            return 80;
        }
        if (type.contains("tsens")) {
            return 70;
        }
        return 0;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasToken(String value, String token) {
        String[] parts = value.split("[^a-z0-9]+");
        for (String part : parts) {
            if (token.equals(part)) {
                return true;
            }
        }
        return false;
    }

    static Integer parseTemperatureCelsius(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(rawValue.trim());
            if (!Double.isFinite(value) || value == 0d) {
                return null;
            }
            double absolute = Math.abs(value);
            double celsius = absolute >= 10_000
                    ? value / 1_000d
                    : absolute >= 200
                    ? value / 10d
                    : value;
            return isPlausibleTemperature(celsius) ? (int) Math.round(celsius) : null;
        } catch (NumberFormatException error) {
            return null;
        }
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

    private static boolean isPlausibleTemperature(double celsius) {
        return celsius >= -40 && celsius <= 150;
    }

    private static Integer readTemperatureFile(File file) {
        return parseTemperatureCelsius(readLine(file));
    }

    private static String readLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException | SecurityException error) {
            return null;
        }
    }

    static final class TemperatureDiagnostics {
        final Integer hardwareCpuCelsius;
        final Integer batteryCelsius;
        final boolean thermalRootAccessible;
        final List<TemperatureSource> thermalSources;
        final String automaticSourceId;

        TemperatureDiagnostics(
                Integer hardwareCpuCelsius,
                Integer batteryCelsius,
                boolean thermalRootAccessible,
                List<TemperatureSource> thermalSources,
                String automaticSourceId
        ) {
            this.hardwareCpuCelsius = hardwareCpuCelsius;
            this.batteryCelsius = batteryCelsius;
            this.thermalRootAccessible = thermalRootAccessible;
            this.thermalSources = thermalSources;
            this.automaticSourceId = automaticSourceId;
        }
    }

    static final class TemperatureSource {
        final String id;
        final String zoneName;
        final String type;
        final Integer temperatureCelsius;
        final int score;
        final File temperatureFile;

        TemperatureSource(
                String id,
                String zoneName,
                String type,
                Integer temperatureCelsius,
                int score,
                File temperatureFile
        ) {
            this.id = id;
            this.zoneName = zoneName;
            this.type = type;
            this.temperatureCelsius = temperatureCelsius;
            this.score = score;
            this.temperatureFile = temperatureFile;
        }
    }

    private static final class ThermalScan {
        final boolean rootAccessible;
        final List<TemperatureSource> sources;

        ThermalScan(boolean rootAccessible, List<TemperatureSource> sources) {
            this.rootAccessible = rootAccessible;
            this.sources = sources;
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
