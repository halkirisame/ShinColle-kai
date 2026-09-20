package com.lulan.shincolle.item;

import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;

import net.minecraft.world.item.ItemStack;

/**
 * Equipment Cannon - ship cannon equipment with 16 variants.
 * meta:
 * 0: 5-Inch Single Cannon
 * 1: 6-Inch Single Cannon
 * 2: 5-Inch Twin Cannon
 * 3: 6-Inch Twin Rapid-Fire Cannon
 * 4: 5-Inch Twin Dual Purpose Cannon
 * 5: 12.5-Inch Twin Secondary Cannon
 * 6: 14-Inch Twin Cannon
 * 7: 16-Inch Twin Cannon
 * 8: 20-Inch Twin Cannon
 * 9: 8-Inch Triple Cannon
 * 10: 16-Inch Triple Cannon
 * 11: 15-Inch Fortress Gun
 * 12: 5-inch Coastal Gun
 * 13: 8-inch Long Range Twin Cannon
 * 14: 15-inch Quadruple Cannon
 * 15: 12-inch Triple Cannon
 */
public class EquipCannon extends BasicEquip {

    public EquipCannon() {
        super(16);
    }

    @Override
    public int getIconIndex(EquipDefinition definition) {
        switch (definition == null ? -1 : definition.equipType()) {
            case ID.EquipType.CANNON_SI:
                return 0; // single cannon
            case ID.EquipType.CANNON_TW_LO:
            case ID.EquipType.CANNON_TW_HI:
                return 1; // twin cannon
            case ID.EquipType.CANNON_TR:
                return 2; // triple cannon
            default:
                return 0;
        }
    }

    @Override
    public int getItemEnchantability(ItemStack stack) {
        switch (this.getEquipType(stack)) {
            case ID.EquipType.CANNON_TW_LO:
                return 12;
            case ID.EquipType.CANNON_TW_HI:
                return 18;
            case ID.EquipType.CANNON_TR:
                return 25;
            default:
                return 9;
        }
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        switch (this.getEquipType(stack)) {
            case ID.EquipType.CANNON_SI: // 128
                return new ResourceAmount(
                        itemRand.nextInt(4) + 5,
                        itemRand.nextInt(4) + 5,
                        itemRand.nextInt(5) + 11,
                        itemRand.nextInt(3) + 3
                );
            case ID.EquipType.CANNON_TW_LO: // 320
                return new ResourceAmount(
                        itemRand.nextInt(7) + 10,
                        itemRand.nextInt(7) + 10,
                        itemRand.nextInt(8) + 16,
                        itemRand.nextInt(6) + 6
                );
            case ID.EquipType.CANNON_TW_HI: // 1600
                return new ResourceAmount(
                        itemRand.nextInt(10) + 50,
                        itemRand.nextInt(15) + 70,
                        itemRand.nextInt(35) + 90,
                        itemRand.nextInt(20) + 80
                );
            case ID.EquipType.CANNON_TR: // 4400
                return new ResourceAmount(
                        itemRand.nextInt(60) + 170,
                        itemRand.nextInt(70) + 210,
                        itemRand.nextInt(80) + 250,
                        itemRand.nextInt(50) + 130
                );
            default:
                return ResourceAmount.ZERO;
        }
    }
}
