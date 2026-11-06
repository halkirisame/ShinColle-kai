package com.lulan.shincolle.client;

import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps equipment tooltip frames away from the top and bottom edges of the viewport. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EquipmentTooltipHandler {

    // The background extends four pixels past the content; one more covers GUI-scale rounding.
    private static final int FRAME_MARGIN = TooltipRenderUtil.PADDING_BOTTOM + 2;

    private EquipmentTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (!(event.getItemStack().getItem() instanceof BasicEquip)
                || event.getTooltipPositioner() != DefaultTooltipPositioner.INSTANCE) {
            return;
        }

        // Match GuiGraphics' measured height, including its special case for a single component.
        int tooltipHeight = event.getComponents().size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : event.getComponents()) {
            tooltipHeight += component.getHeight();
        }
        event.setY(adjustedMouseY(event.getY(), event.getScreenHeight(), tooltipHeight));
    }

    static int adjustedMouseY(int mouseY, int screenHeight, int tooltipHeight) {
        int maximumY = screenHeight - tooltipHeight - FRAME_MARGIN;
        if (maximumY < FRAME_MARGIN) {
            // Oversized tooltips need a content-layout policy, not a different origin.
            return mouseY;
        }
        int contentY = mouseY - TooltipRenderUtil.MOUSE_OFFSET;
        int boundedY = Math.max(FRAME_MARGIN, Math.min(contentY, maximumY));
        // Pre's Y is still an anchor: DefaultTooltipPositioner subtracts the mouse offset later.
        return boundedY + TooltipRenderUtil.MOUSE_OFFSET;
    }
}
