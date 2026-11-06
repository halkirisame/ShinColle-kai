package com.lulan.shincolle.block;

import com.lulan.shincolle.item.IShipResourceItem;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Custom BlockItem for GrudgeHeavy that displays stored materials in tooltip.
 * When the multi-block is broken, it stores material stock in "mats" NBT array.
 */
public class ItemBlockGrudgeHeavy extends BlockItem implements IShipResourceItem {

    public ItemBlockGrudgeHeavy(Block block, Properties properties) {
        super(block, properties);
    }

    public static int[] getStoredMaterials(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int[] mats = tag == null ? new int[0] : tag.getIntArray("Mats").clone();
        if (mats.length != 4) return new int[4];
        for (int i = 0; i < mats.length; i++) {
            mats[i] = Math.max(0, Math.min(mats[i], TileMultiGrudgeHeavy.MAX_STOCK));
        }
        return mats;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag()) {
            CompoundTag nbt = stack.getTag();
            assert nbt != null;
            if (nbt.contains("mats")) {
                int[] mats = nbt.getIntArray("mats");
                if (mats.length >= 4) {
                    tooltip.add(Component.literal(
                            ChatFormatting.WHITE + "" + mats[0] + " " + Component.translatable("item.shincolle_kai.grudge").getString()));
                    tooltip.add(Component.literal(
                            ChatFormatting.RED + "" + mats[1] + " " + Component.translatable("item.shincolle_kai.abyss_metal").getString()));
                    tooltip.add(Component.literal(
                            ChatFormatting.GREEN + "" + mats[2] + " " + Component.translatable("item.shincolle_kai.ammo").getString()));
                    tooltip.add(Component.literal(
                            ChatFormatting.AQUA + "" + mats[3] + " " + Component.translatable("item.shincolle_kai.abyss_metal_1").getString()));
                }
            }
        }
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        // GrudgeHeavy provides 81 grudge (9x9 grudge items)
        return new ResourceAmount(81, 0, 0, 0);
    }
}
