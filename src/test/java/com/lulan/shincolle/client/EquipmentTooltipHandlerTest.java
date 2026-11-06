package com.lulan.shincolle.client;

import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTooltipHandlerTest {

    @Test
    void flareAtCreativeHotbarKeepsItsEntireFrameInsideTheViewport() {
        int screenHeight = 334;
        int tooltipHeight = 13 * 10;
        int mouseY = 218;
        int vanillaY = positionedY(mouseY, screenHeight, tooltipHeight);
        assertEquals(201, vanillaY);
        assertTrue(vanillaY + tooltipHeight + 4 > screenHeight,
                "Vanilla's bottom horizontal background line extends one pixel outside the viewport");

        int adjustedMouseY = EquipmentTooltipHandler.adjustedMouseY(mouseY, screenHeight, tooltipHeight);
        int adjustedY = positionedY(adjustedMouseY, screenHeight, tooltipHeight);
        assertEquals(199, adjustedY);
        assertTrue(adjustedY + tooltipHeight + 4 < screenHeight,
                "Keep the outer background line and one pixel for GUI-scale rounding");
    }

    @Test
    void tooltipAwayFromEdgesKeepsTheOriginalAnchor() {
        assertEquals(100, EquipmentTooltipHandler.adjustedMouseY(100, 334, 130));
    }

    @Test
    void tooltipAtTheTopKeepsItsFrameVisible() {
        int mouseY = EquipmentTooltipHandler.adjustedMouseY(0, 334, 130);
        int contentY = positionedY(mouseY, 334, 130);
        assertEquals(5, contentY);
        assertTrue(contentY - 4 > 0);
    }

    @Test
    void singleLineHeightUsesTheSameSafeBounds() {
        int mouseY = EquipmentTooltipHandler.adjustedMouseY(333, 334, 8);
        assertEquals(321, positionedY(mouseY, 334, 8));
    }

    @Test
    void tooltipThatCannotFitKeepsVanillaBehaviorRatherThanHidingContent() {
        assertEquals(218, EquipmentTooltipHandler.adjustedMouseY(218, 334, 325));
    }

    @Test
    void exactFitRetainsBothFrameMargins() {
        int mouseY = EquipmentTooltipHandler.adjustedMouseY(218, 334, 324);
        assertEquals(5, positionedY(mouseY, 334, 324));
    }

    @Test
    void wrappedOrExpandedTooltipUsesItsActualHeight() {
        for (int screenHeight : new int[]{250, 334, 501, 1001}) {
            for (int height : new int[]{80, 130, 140, 180, 240}) {
                if (height + 10 > screenHeight) {
                    continue;
                }
                for (int mouseY = 0; mouseY <= screenHeight; mouseY++) {
                    int anchor = EquipmentTooltipHandler.adjustedMouseY(mouseY, screenHeight, height);
                    int contentY = positionedY(anchor, screenHeight, height);
                    assertTrue(contentY - 4 > 0);
                    assertTrue(contentY + height + 4 < screenHeight);
                }
            }
        }
    }

    private static int positionedY(int mouseY, int screenHeight, int tooltipHeight) {
        return DefaultTooltipPositioner.INSTANCE.positionTooltip(640, screenHeight, 350, mouseY, 160, tooltipHeight).y();
    }
}
