package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;

import net.minecraft.world.item.ItemStack;

/**
 * Equipment Radar - radar equipment with 9 variants.
 * meta:
 * 0: Air Radar Mk.I
 * 1: Air Radar Mk.II
 * 2: Surface Radar Mk.I
 * 3: Surface Radar Mk.II
 * 4: Abyssal Sonar
 * 5: Abyssal Air Radar
 * 6: Abyssal Surface Radar
 * 7: Abyssal Sonar Mk.II
 * 8: Abyssal FCS + CIC
 */
public class EquipRadar extends BasicEquip {

    public EquipRadar() {
        super(9);
    }

    @Override
    public int getItemEnchantability(ItemStack stack) {
        switch (this.getEquipType(stack)) {
            case ID.EquipType.RADAR_LO:
                return 12;
            case ID.EquipType.RADAR_HI:
                return 15;
            default:
                return 9;
        }
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        switch (this.getEquipType(stack)) {
            case ID.EquipType.RADAR_LO: // 200
                return new ResourceAmount(
                        itemRand.nextInt(7) + 12,
                        itemRand.nextInt(6) + 10,
                        itemRand.nextInt(5) + 9,
                        itemRand.nextInt(4) + 7
                );
            case ID.EquipType.RADAR_HI: // 2000
                return new ResourceAmount(
                        itemRand.nextInt(40) + 110,
                        itemRand.nextInt(35) + 90,
                        itemRand.nextInt(30) + 70,
                        itemRand.nextInt(25) + 50
                );
            default:
                return ResourceAmount.ZERO;
        }
    }
}
