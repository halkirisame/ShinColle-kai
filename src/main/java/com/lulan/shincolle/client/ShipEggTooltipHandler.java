package com.lulan.shincolle.client;

import java.util.List;

import com.lulan.shincolle.client.gui.ShipEggContentsTooltip;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipEggContents;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.Reference;

import com.mojang.datafixers.util.Either;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Puts the ship egg's item grid at the bottom of its tooltip.
 * <p>
 * Item#getTooltipImage would work, but vanilla always inserts that right under
 * the item's name, leaving the "stored items" line stranded several rows below
 * the grid it labels. Appending the component here keeps the two together.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShipEggTooltipHandler {

    private ShipEggTooltipHandler() {
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != ModItems.SHIP_SPAWN_EGG.get()) {
            return;
        }

        ShipEggContents contents = ShipSpawnEgg.readStoredInventory(stack);
        if (contents.isEmpty()) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.add(Either.right(new ShipEggContentsTooltip(contents)));
    }
}
