package com.mmwtl.atlasappwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PanelLayoutTest {
    @Test
    public void fullWidthWithOutlineNeverExceedsAvailableBounds() {
        PanelLayout layout = layout(1440, 1920, 100, 5, 1, 72, 12, 14, 20);

        assertEquals(1440, layout.panelWidth);
        assertTrue(layout.panelHeight <= 1920);
    }

    @Test
    public void excessiveColumnsAndGapStayInsideGrid() {
        PanelLayout layout = layout(360, 1920, 25, 10, 1, 240, 40, 40, 0);
        int occupied = layout.cellWidth * 10 + layout.horizontalGap * 9;

        assertTrue(occupied <= layout.gridWidth);
        assertTrue(layout.iconSize <= layout.cellWidth);
    }

    @Test
    public void horizontalShrinkAlsoReducesPanelHeight() {
        PanelLayout layout = layout(360, 1920, 25, 10, 4, 240, 40, 40, 0);

        assertEquals(layout.iconSize + 24, layout.cellHeight);
        assertTrue(layout.iconSize < 240);
        assertTrue(layout.panelHeight <= 1920);
    }

    @Test
    public void verticalLimitShrinksIconsAndGaps() {
        PanelLayout layout = layout(1440, 420, 72, 5, 4, 240, 40, 40, 12);

        assertTrue(layout.panelHeight <= 420);
        assertTrue(layout.iconSize < 240);
        assertTrue(layout.verticalGap <= 40);
    }

    @Test
    public void defaultConfigurationKeepsRequestedIconSize() {
        PanelLayout layout = layout(1440, 1920, 72, 5, 1, 72, 12, 14, 0);

        assertEquals(72, layout.iconSize);
        assertEquals(12, layout.horizontalGap);
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

    private static PanelLayout layout(
            int width,
            int height,
            int widthPercent,
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
                widthPercent,
                180,
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
                1440, 1920, 72, 180, 54, 72, 20, 4,
                1, 5, 14, 12, showHandle, verticalHandle, 34, 4,
                showSystemStatus, sideSystemStatus, systemStatusSize, 16, 0
        );
    }
}
