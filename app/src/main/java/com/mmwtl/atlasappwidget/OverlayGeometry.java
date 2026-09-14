package com.mmwtl.atlasappwidget;

final class OverlayGeometry {
    record Position(int x, int y) {}

    record Offset(int x, int y) {}

    private OverlayGeometry() {}

    static Position positionFor(OverlayCorner corner, int left, int top, int right, int bottom,
            int panelWidth, int panelHeight, int offsetX, int offsetY) {
        int safeOffsetX = Math.max(0, offsetX);
        int safeOffsetY = Math.max(0, offsetY);
        long x = isEnd(corner)
                ? (long) right - panelWidth - safeOffsetX : (long) left + safeOffsetX;
        long y = isBottom(corner)
                ? (long) bottom - panelHeight - safeOffsetY : (long) top + safeOffsetY;
        return new Position(clamp(x, left, Math.max(left, right - panelWidth)),
                clamp(y, top, Math.max(top, bottom - panelHeight)));
    }

    static Offset offsetsFor(OverlayCorner corner, int left, int top, int right, int bottom,
            int panelWidth, int panelHeight, int absoluteX, int absoluteY) {
        Position position = positionFor(OverlayCorner.TOP_START, left, top, right, bottom,
                panelWidth, panelHeight, absoluteX - left, absoluteY - top);
        int x = isEnd(corner) ? right - panelWidth - position.x : position.x - left;
        int y = isBottom(corner) ? bottom - panelHeight - position.y : position.y - top;
        return new Offset(Math.max(0, x), Math.max(0, y));
    }

    private static boolean isEnd(OverlayCorner corner) {
        return corner == OverlayCorner.TOP_END || corner == OverlayCorner.BOTTOM_END;
    }

    private static boolean isBottom(OverlayCorner corner) {
        return corner == OverlayCorner.BOTTOM_START || corner == OverlayCorner.BOTTOM_END;
    }

    private static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }
}
