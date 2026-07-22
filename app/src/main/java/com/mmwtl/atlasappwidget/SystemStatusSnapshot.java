package com.mmwtl.atlasappwidget;

final class SystemStatusSnapshot {
    static final int UNAVAILABLE = -1;

    final int cpuPercent;
    final int ramPercent;
    final int temperatureCelsius;

    SystemStatusSnapshot(int cpuPercent, int ramPercent, int temperatureCelsius) {
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
        this.temperatureCelsius = temperatureCelsius;
    }

    static SystemStatusSnapshot unavailable() {
        return new SystemStatusSnapshot(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE);
    }

    static SystemStatusSnapshot preview() {
        return new SystemStatusSnapshot(38, 62, 54);
    }
}
