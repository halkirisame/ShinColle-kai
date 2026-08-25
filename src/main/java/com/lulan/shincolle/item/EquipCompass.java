package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.Enums.EnumEquipEffectSP;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Equipment Compass - compass equipment with 1 variant.
 */
public class EquipCompass extends BasicEquip {

    public EquipCompass() {
        super(1);
    }

    @Override
    public EnumEquipEffectSP getSpecialEffect(ItemStack stack) {
        return EnumEquipEffectSP.COMPASS;
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        if (this.getEquipType(stack) == ID.EquipType.COMPASS_LO) { // 90
            return new ResourceAmount(
                    itemRand.nextInt(5) + 5,
                    itemRand.nextInt(3) + 4,
                    itemRand.nextInt(2) + 2,
                    itemRand.nextInt(2) + 2
            );
        }
        return ResourceAmount.ZERO;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.shincolle_kai.compass").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
