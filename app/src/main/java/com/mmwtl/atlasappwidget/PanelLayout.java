package com.mmwtl.atlasappwidget;

/** Pure pixel geometry used by both the overlay and its preview. */
final class PanelLayout {
    final int backgroundWidth;
    final int backgroundHeight;
    final int panelWidth;
    final int panelHeight;
    final int padding;
    final int horizontalGap;
    final int verticalGap;
    final int gridWidth;
    final int gridHeight;
    final int cellWidth;
    final int cellHeight;
    final int iconSize;

    private PanelLayout(
            int backgroundWidth,
            int backgroundHeight,
            int outlineInset,
            int padding,
            int horizontalGap,
            int verticalGap,
            int gridWidth,
            int gridHeight,
            int cellWidth,
            int cellHeight,
            int iconSize
    ) {
        this.backgroundWidth = backgroundWidth;
        this.backgroundHeight = backgroundHeight;
        panelWidth = backgroundWidth + outlineInset * 2;
        panelHeight = backgroundHeight + outlineInset * 2;
        this.padding = padding;
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.iconSize = iconSize;
    }

    static PanelLayout calculate(
            int availableWidth,
            int availableHeight,
            int widthPercent,
            int minimumWidth,
            int minimumHeight,
            int requestedIconSize,
            int labelHeight,
            int labelGap,
            int rows,
            int columns,
            int requestedPadding,
            int requestedGap,
            boolean showHandle,
            boolean verticalHandle,
            int handleSize,
            int handleGap,
            int outlineInset
    ) {
        int safeWidth = Math.max(1, availableWidth);
        int safeHeight = Math.max(1, availableHeight);
        int safeOutline = Math.max(0, outlineInset);
        int maxBackgroundWidth = Math.max(1, safeWidth - safeOutline * 2);
        int maxBackgroundHeight = Math.max(1, safeHeight - safeOutline * 2);
        int safeRows = Math.max(1, rows);
        int safeColumns = Math.max(1, columns);
        int safeLabelHeight = Math.max(0, labelHeight);
        int safeLabelGap = safeLabelHeight == 0 ? 0 : Math.max(0, labelGap);
        int fixedCellHeight = safeLabelHeight + safeLabelGap;
        int horizontalHandleSpace = showHandle && !verticalHandle
                ? Math.max(0, handleSize) + Math.max(0, handleGap)
                : 0;
        int verticalHandleSpace = showHandle && verticalHandle
                ? Math.max(0, handleSize) + Math.max(0, handleGap)
                : 0;

        int requestedWidth = Math.round(
                safeWidth * Math.max(25, Math.min(100, widthPercent)) / 100f);
        int backgroundWidth = Math.min(
                maxBackgroundWidth,
                Math.max(Math.max(1, minimumWidth), requestedWidth)
        );

        int maxHorizontalPadding = Math.max(
                0,
                (backgroundWidth - horizontalHandleSpace - safeColumns) / 2
        );
        int maxVerticalPadding = Math.max(
                0,
                (maxBackgroundHeight - verticalHandleSpace
                        - safeRows * (fixedCellHeight + 1)) / 2
        );
        int padding = Math.max(0, Math.min(
                requestedPadding,
                Math.min(maxHorizontalPadding, maxVerticalPadding)
        ));

        int gridWidth = Math.max(
                1,
                backgroundWidth - padding * 2 - horizontalHandleSpace
        );
        int maxHorizontalGap = safeColumns == 1
                ? 0
                : Math.max(0, (gridWidth - safeColumns) / (safeColumns - 1));
        int horizontalGap = Math.min(Math.max(0, requestedGap), maxHorizontalGap);
        int cellWidth = Math.max(
                1,
                (gridWidth - horizontalGap * (safeColumns - 1)) / safeColumns
        );

        int availableGridHeight = Math.max(
                1,
                maxBackgroundHeight - padding * 2 - verticalHandleSpace
        );
        int maxVerticalGap = safeRows == 1
                ? 0
                : Math.max(0,
                (availableGridHeight - safeRows * (fixedCellHeight + 1))
                        / (safeRows - 1));
        int verticalGap = Math.min(Math.max(0, requestedGap), maxVerticalGap);
        int iconHeightLimit = Math.max(
                1,
                (availableGridHeight - verticalGap * (safeRows - 1)) / safeRows
                        - fixedCellHeight
        );
        int iconSize = Math.max(
                1,
                Math.min(Math.max(1, requestedIconSize), Math.min(cellWidth, iconHeightLimit))
        );
        int cellHeight = iconSize + fixedCellHeight;
        int gridHeight = safeRows * cellHeight + verticalGap * (safeRows - 1);
        int contentHeight = gridHeight + padding * 2 + verticalHandleSpace;
        int backgroundHeight = Math.min(
                maxBackgroundHeight,
                Math.max(Math.max(1, minimumHeight), contentHeight)
        );

        return new PanelLayout(
                backgroundWidth,
                backgroundHeight,
                safeOutline,
                padding,
                horizontalGap,
                verticalGap,
                gridWidth,
                gridHeight,
                cellWidth,
                cellHeight,
                iconSize
        );
    }
}
