package com.lulan.shincolle.handler;

import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Applies enchanted books to ship equipment at a fixed level cost. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EquipmentAnvilHandler {

    private EquipmentAnvilHandler() {
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()
                || !(left.getItem() instanceof BasicEquip) || !right.is(Items.ENCHANTED_BOOK)) {
            return;
        }

        ItemStack output = left.copy();
        Map<Enchantment, Integer> equipmentEnchantments = new HashMap<>(EnchantmentHelper.getEnchantments(output));
        Map<Enchantment, Integer> bookEnchantments = EnchantmentHelper.getEnchantments(right);
        for (Map.Entry<Enchantment, Integer> entry : bookEnchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int equipmentLevel = equipmentEnchantments.getOrDefault(enchantment, 0);
            int bookLevel = entry.getValue();
            int level = equipmentLevel == bookLevel ? bookLevel + 1 : Math.max(equipmentLevel, bookLevel);
            equipmentEnchantments.put(enchantment, Math.min(level, enchantment.getMaxLevel()));
        }

        EnchantmentHelper.setEnchantments(equipmentEnchantments, output);
        String name = event.getName();
        if (name != null && !Util.isBlank(name)) {
            if (!name.equals(output.getHoverName().getString())) {
                output.setHoverName(Component.literal(name));
            }
        } else if (output.hasCustomHoverName()) {
            output.resetHoverName();
        }
        event.setCost(30);
        event.setMaterialCost(1);
        event.setOutput(output);
    }
}
