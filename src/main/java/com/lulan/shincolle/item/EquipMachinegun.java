package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;

import net.minecraft.world.item.ItemStack;

/**
 * Equipment Machine Gun - anti-air gun equipment with 7 variants.
 * meta:
 * 0: 3-Inch Single High-Angle Gun Mount
 * 1: 5-Inch Single High-Angle Gun Mount
 * 2: 12.7mm Abyssal Machine Gun
 * 3: 20mm Abyssal Machine Gun
 * 4: 40mm Abyssal Twin Autocannon Mount
 * 5: 40mm Abyssal Quad Autocannon Mount
 * 6: 4-Inch Twin Dual Purpose Gun Mount + CIC
 */
public class EquipMachinegun extends BasicEquip {

    public EquipMachinegun() {
        super(7);
    }

    @Override
    public int getItemEnchantability(ItemStack stack) {
        switch (this.getEquipType(stack)) {
            case ID.EquipType.GUN_LO:
                return 12;
            case ID.EquipType.GUN_HI:
                return 15;
            default:
                return 9;
        }
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        switch (this.getEquipType(stack)) {
            case ID.EquipType.GUN_LO: // 100
                return new ResourceAmount(
                        itemRand.nextInt(3) + 4,
                        itemRand.nextInt(4) + 7,
                        itemRand.nextInt(5) + 8,
                        itemRand.nextInt(2) + 4
                );
            case ID.EquipType.GUN_HI: // 800
                return new ResourceAmount(
                        itemRand.nextInt(20) + 30,
                        itemRand.nextInt(25) + 40,
                        itemRand.nextInt(30) + 50,
                        itemRand.nextInt(15) + 20
                );
            default:
                return ResourceAmount.ZERO;
        }
    }
}
