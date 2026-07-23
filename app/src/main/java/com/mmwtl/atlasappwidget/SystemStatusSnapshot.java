package com.mmwtl.atlasappwidget;

final class SystemStatusSnapshot {
    static final int UNAVAILABLE = -1;

    final int cpuPercent;
    final int ramPercent;

    SystemStatusSnapshot(int cpuPercent, int ramPercent) {
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
    }

    static SystemStatusSnapshot unavailable() {
        return new SystemStatusSnapshot(UNAVAILABLE, UNAVAILABLE);
    }

    static SystemStatusSnapshot preview() {
        return new SystemStatusSnapshot(38, 62);
    }
}
