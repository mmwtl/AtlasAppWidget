package com.mmwtl.atlasappwidget;

final class SystemStatusSnapshot {
    static final int UNAVAILABLE = -1;

    final int cpuPercent;
    final int ramPercent;
    final int fuelLiters;
    final int fuelPercent;

    SystemStatusSnapshot(
            int cpuPercent,
            int ramPercent,
            int fuelLiters,
            int fuelPercent
    ) {
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
        this.fuelLiters = fuelLiters;
        this.fuelPercent = fuelPercent;
    }

    static SystemStatusSnapshot unavailable() {
        return new SystemStatusSnapshot(
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE
        );
    }

    static SystemStatusSnapshot preview() {
        return new SystemStatusSnapshot(38, 62, 32, 59);
    }
}
