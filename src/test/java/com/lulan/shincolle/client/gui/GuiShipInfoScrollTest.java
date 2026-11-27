package com.lulan.shincolle.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiShipInfoScrollTest {
    @Test
    void rowsClampAndWheelStopsAtBothEnds() {
        for (int count : new int[]{12, 14}) {
            int max = count - 5;
            assertEquals(0, GuiShipInventory.clampInfoScroll(-1, count));
            assertEquals(max, GuiShipInventory.clampInfoScroll(100, count));
            assertEquals(0, GuiShipInventory.scrollInfoWheel(0, count, 1));
            assertEquals(max, GuiShipInventory.scrollInfoWheel(max, count, -1));
            assertEquals(1, GuiShipInventory.scrollInfoWheel(0, count, -1));
            assertEquals(max - 1, GuiShipInventory.scrollInfoWheel(max, count, 1));
            assertEquals(2, GuiShipInventory.scrollInfoWheel(0, count, -2));
        }
    }

    @Test
    void thumbFillsTheSpecifiedTrackAtEachEnd() {
        assertEquals(44, GuiShipInventory.infoThumbHeight(12));
        assertEquals(37, GuiShipInventory.infoThumbHeight(14));
        for (int count : new int[]{12, 14}) {
            assertEquals(18, GuiShipInventory.infoThumbTop(0, count));
            assertEquals(124, GuiShipInventory.infoThumbTop(count - 5, count)
                    + GuiShipInventory.infoThumbHeight(count));
        }
        assertEquals(106, GuiShipInventory.infoThumbHeight(5));
        assertEquals(18, GuiShipInventory.infoThumbTop(2, 5));
    }

    @Test
    void trackClicksMapToClampedRows() {
        for (int count : new int[]{12, 14}) {
            assertEquals(0, GuiShipInventory.infoScrollAtY(18, count));
            assertEquals(count - 5, GuiShipInventory.infoScrollAtY(123, count));
            assertEquals(0, GuiShipInventory.infoScrollAtY(-100, count));
            assertEquals(count - 5, GuiShipInventory.infoScrollAtY(300, count));
        }
    }
}
