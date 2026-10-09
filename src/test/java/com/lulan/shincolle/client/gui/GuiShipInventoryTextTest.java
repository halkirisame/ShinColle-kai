package com.lulan.shincolle.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiShipInventoryTextTest {

    @Test
    void marriageAndUnmarriedMoveOntoSeparateLinesWithinExistingRow() {
        int offset = GuiShipInventory.statValueOffset(43, 48, 58, 0, 9);
        assertTrue(offset > 9, "Label and value glyph boxes must not intersect");
        assertTrue(offset + 9 < 21, "Both lines must fit before the next stat row");
    }

    @Test
    void shortLabelAndValueKeepTheSameLineWithFourPixelGap() {
        assertEquals(0, GuiShipInventory.statValueOffset(20, 20, 58, 0, 9));
        assertEquals(0, GuiShipInventory.statValueOffset(20, 34, 58, 0, 9));
        assertEquals(10, GuiShipInventory.statValueOffset(20, 35, 58, 0, 9));
    }

    @Test
    void longExperienceValueDoesNotShareItsLabelRow() {
        assertEquals(10, GuiShipInventory.statValueOffset(20, 100, 58, 0, 9));
    }

    @Test
    void forcedAttackValueRowKeepsOnePixelVerticalGap() {
        assertEquals(10, GuiShipInventory.statValueOffset(12, 12, 58, 9, 9));
    }
}
