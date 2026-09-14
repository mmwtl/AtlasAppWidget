package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PanelLayoutTest {
    @Test
    public void fullWidthWithOutlineNeverExceedsAvailableBounds() {
        PanelLayout layout = layout(1440, 1920, PanelConfig.WIDTH_MAX_PIXELS,
                5, 1, 72, 12, 14, 20);

        assertEquals(1440, layout.panelWidth);
        assertTrue(layout.panelHeight <= 1920);
    }

    @Test
    public void excessiveColumnsAndGapStayInsideGrid() {
        PanelLayout layout = layout(360, 1920, 360,
                10, 1, 240, 40, 40, 0);
        int occupied = layout.cellWidth * 10 + layout.horizontalGap * 9;

        assertTrue(occupied <= layout.gridWidth);
        assertTrue(layout.iconSize <= layout.cellWidth);
    }

    @Test
    public void horizontalShrinkAlsoReducesPanelHeight() {
        PanelLayout layout = layout(360, 1920, 360,
                10, 4, 240, 40, 40, 0);

        assertEquals(layout.iconSize + 24, layout.cellHeight);
        assertTrue(layout.iconSize < 240);
        assertTrue(layout.panelHeight <= 1920);
    }

    @Test
    public void verticalLimitShrinksIconsAndGaps() {
        PanelLayout layout = layout(1440, 420, PanelConfig.WIDTH_DEFAULT_PIXELS,
                5, 4, 240, 40, 40, 12);

        assertTrue(layout.panelHeight <= 420);
        assertTrue(layout.iconSize < 240);
        assertTrue(layout.verticalGap <= 40);
    }

    @Test
    public void defaultConfigurationKeepsRequestedIconSize() {
        PanelLayout layout = layout(1440, 1920, PanelConfig.WIDTH_DEFAULT_PIXELS,
                5, 1, 72, 12, 14, 0);

        assertEquals(72, layout.iconSize);
        assertEquals(12, layout.horizontalGap);
    }

    @Test
    public void manualHeightIsExactAndShrinksIconsToFit() {
        PanelLayout automatic = layout(1440, 1920, PanelConfig.WIDTH_DEFAULT_PIXELS,
                5, 2, 240, 12, 14, 0);
        PanelLayout manual = PanelLayout.calculate(
                1440, 1920, PanelConfig.WIDTH_DEFAULT_PIXELS,
                PanelConfig.WIDTH_MIN_PIXELS, 54, 240, 20, 4,
                2, 5, 14, 40, true, false, 34, 4,
                false, false, 30, 16, 0, 300);

        assertEquals(300, manual.backgroundHeight);
        assertTrue(manual.iconSize < automatic.iconSize);
        assertTrue(manual.gridHeight + manual.padding * 2 <= manual.backgroundHeight);
    }

    @Test
    public void manualHeightMinimumIncludesLabelsAndVerticalChrome() {
        PanelLayout layout = PanelLayout.calculate(
                1440, 1920, PanelConfig.WIDTH_DEFAULT_PIXELS,
                PanelConfig.WIDTH_MIN_PIXELS, 54, 240, 40, 8,
                3, 5, 40, 40, true, true, 34, 4,
                true, false, 80, 16, 2, 1);

        assertEquals(layout.minimumBackgroundHeight, layout.backgroundHeight);
        assertTrue(layout.backgroundHeight <= layout.maximumBackgroundHeight);
        assertTrue(layout.iconSize >= 1);
        assertTrue(layout.gridHeight + layout.padding * 2 + 38 + 80 + 16
                <= layout.backgroundHeight);
    }

    @Test
    public void minimumManualHeightKeepsSideHandleVisibleWithBottomStatus() {
        PanelLayout layout = PanelLayout.calculate(
                1440, 1920, 1000, 1, 54, 104, 0, 0,
                1, 5, 14, 12, true, false, 34, 4,
                true, false, 30, 16, 2, 1);

        assertEquals(80, layout.minimumBackgroundHeight);
        assertTrue(layout.gridHeight >= 34);
        assertEquals(80, layout.backgroundHeight);
        assertEquals(84, layout.panelHeight);
    }

    @Test
    public void largerManualHeightDoesNotEnlargeIcons() {
        PanelLayout layout = PanelLayout.calculate(
                1440, 1920, 1000, 1, 54, 72, 20, 4,
                2, 5, 14, 12, false, false, 34, 4,
                false, false, 30, 16, 2, 600);

        assertEquals(600, layout.backgroundHeight);
        assertEquals(72, layout.iconSize);
        assertTrue(layout.gridHeight + layout.padding * 2 < layout.backgroundHeight);
    }

    @Test
    public void requestedWidthIsAppliedAsPixels() {
        PanelLayout layout = layout(1440, 1920, 800, 5, 1, 72, 12, 14, 0);

        assertEquals(800, layout.panelWidth);
    }

    @Test
    public void narrowSingleColumnWidthIsAppliedAsPixels() {
        PanelLayout layout = layout(1440, 1920, 120, 1, 1, 72, 12, 14, 0);

        assertEquals(120, layout.panelWidth);
        assertTrue(layout.iconSize <= layout.cellWidth);
    }

    @Test
    public void hiddenHandleLeavesItsSpaceForGrid() {
        PanelLayout withHandle = customLayout(true, false);
        PanelLayout hiddenHandle = customLayout(false, false);

        assertTrue(hiddenHandle.gridWidth > withHandle.gridWidth);
        assertEquals(withHandle.panelWidth, hiddenHandle.panelWidth);
    }

    @Test
    public void verticalHandleConsumesHeightWithoutOverflow() {
        PanelLayout layout = customLayout(true, true);

        assertTrue(layout.panelHeight <= 1920);
        assertEquals(72, layout.iconSize);
    }

    @Test
    public void systemStatusAddsOnlyItsReservedVerticalSpace() {
        PanelLayout withoutStatus = customLayout(false, false, false);
        PanelLayout withStatus = customLayout(false, false, true);

        assertEquals(46, withStatus.panelHeight - withoutStatus.panelHeight);
        assertEquals(withoutStatus.iconSize, withStatus.iconSize);
    }

    @Test
    public void enlargedSystemStatusReservesDynamicHeightAndGap() {
        PanelLayout withoutStatus = customLayout(false, false, false, 30);
        PanelLayout enlargedStatus = customLayout(false, false, true, 54);

        assertEquals(70, enlargedStatus.panelHeight - withoutStatus.panelHeight);
        assertEquals(withoutStatus.iconSize, enlargedStatus.iconSize);
    }

    @Test
    public void sideSystemStatusReservesWidthWithoutIncreasingHeight() {
        PanelLayout withoutStatus = customLayout(false, false, false, false, 26);
        PanelLayout sideStatus = customLayout(false, false, true, true, 26);

        assertEquals(withoutStatus.panelHeight, sideStatus.panelHeight);
        assertEquals(42, withoutStatus.gridWidth - sideStatus.gridWidth);
    }

    @Test
    public void singleSideMetricUsesOnlyItsOwnLineAndOuterGap() {
        PanelLayout withoutStatus = customLayout(false, false, false, false, 6);
        PanelLayout sideStatus = customLayout(false, false, true, true, 6);

        assertEquals(withoutStatus.panelHeight, sideStatus.panelHeight);
        assertEquals(22, withoutStatus.gridWidth - sideStatus.gridWidth);
    }

    @Test
    public void largerLabelHeightIsReservedByEachCell() {
        PanelLayout smallLabels = layoutWithLabelHeight(20);
        PanelLayout largeLabels = layoutWithLabelHeight(40);

        assertEquals(smallLabels.iconSize, largeLabels.iconSize);
        assertEquals(20, largeLabels.cellHeight - smallLabels.cellHeight);
        assertEquals(20, largeLabels.gridHeight - smallLabels.gridHeight);
    }

    private static PanelLayout layout(
            int width,
            int height,
            int widthPixels,
            int columns,
            int rows,
            int icon,
            int gap,
            int padding,
            int outline
    ) {
        return PanelLayout.calculate(
                width,
                height,
                widthPixels,
                PanelConfig.WIDTH_MIN_PIXELS,
                54,
                icon,
                20,
                4,
                rows,
                columns,
                padding,
                gap,
                true,
                false,
                34,
                4,
                false,
                false,
                30,
                16,
                outline
        );
    }

    private static PanelLayout layoutWithLabelHeight(int labelHeight) {
        return PanelLayout.calculate(
                1440, 1920, PanelConfig.WIDTH_DEFAULT_PIXELS,
                PanelConfig.WIDTH_MIN_PIXELS, 54, 72, labelHeight, 4,
                1, 5, 14, 12, true, false, 34, 4,
                false, false, 30, 16, 0
        );
    }

    private static PanelLayout customLayout(boolean showHandle, boolean verticalHandle) {
        return customLayout(showHandle, verticalHandle, false);
    }

    private static PanelLayout customLayout(
            boolean showHandle,
            boolean verticalHandle,
            boolean showSystemStatus
    ) {
        return customLayout(showHandle, verticalHandle, showSystemStatus, 30);
    }

    private static PanelLayout customLayout(
            boolean showHandle,
            boolean verticalHandle,
            boolean showSystemStatus,
            int systemStatusHeight
    ) {
        return customLayout(showHandle, verticalHandle, showSystemStatus,
                false, systemStatusHeight);
    }

    private static PanelLayout customLayout(
            boolean showHandle,
            boolean verticalHandle,
            boolean showSystemStatus,
            boolean sideSystemStatus,
            int systemStatusSize
    ) {
        return PanelLayout.calculate(
                1440, 1920, PanelConfig.WIDTH_DEFAULT_PIXELS,
                PanelConfig.WIDTH_MIN_PIXELS, 54, 72, 20, 4,
                1, 5, 14, 12, showHandle, verticalHandle, 34, 4,
                showSystemStatus, sideSystemStatus, systemStatusSize, 16, 0
        );
    }
}
