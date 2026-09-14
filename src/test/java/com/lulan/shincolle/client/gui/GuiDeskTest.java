package com.lulan.shincolle.client.gui;

import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiDeskTest {

    @Test
    void scaledCenteredOriginCentersWidthAtDeskScale() {
        assertEquals(64, GuiDesk.scaledCenteredOrigin(480, 1.25F, 256));
    }

    @Test
    void scaledCenteredOriginCentersHeightAtDeskScale() {
        assertEquals(12, GuiDesk.scaledCenteredOrigin(270, 1.25F, 192));
    }

    @Test
    void scaledCenteredOriginClampsToZeroWhenScreenIsTooSmall() {
        assertEquals(0, GuiDesk.scaledCenteredOrigin(200, 1.25F, 192));
    }

    @Test
    void scaledIconAreaConvertsBookCoordinatesToScreenPixels() {
        Rect2i area = GuiDesk.scaledIconArea(64, 12, 16, 45);

        assertEquals(100, area.getX());
        assertEquals(71, area.getY());
        assertEquals(20, area.getWidth());
        assertEquals(20, area.getHeight());
    }
}
