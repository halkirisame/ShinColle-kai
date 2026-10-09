package com.lulan.shincolle.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerFeedbackOverlayTest {

    @Test
    void leftMaxWidthStopsBeforeOffhandSlotAt640() {
        assertEquals(192, PointerFeedbackOverlay.leftMaxWidth(640));
    }

    @Test
    void leftMaxWidthSwitchesToCenteredBelow496() {
        assertEquals(119, PointerFeedbackOverlay.leftMaxWidth(495));
        assertEquals(120, PointerFeedbackOverlay.leftMaxWidth(496));
        assertTrue(PointerFeedbackOverlay.shouldCenter(495));
        assertFalse(PointerFeedbackOverlay.shouldCenter(496));
    }
}
