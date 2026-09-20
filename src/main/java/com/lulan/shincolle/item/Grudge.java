package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import net.minecraft.world.item.ItemStack;

/**
 * Grudge material item - primary resource for ship entities.
 */
public class Grudge extends BasicItem implements IShipResourceItem, IShipFoodItem {

    public Grudge() {
        super(new Properties());
    }

    @Override
    public float getFoodValue(int meta) {
        return 10.0F;
    }

    @Override
    public float getSaturationValue(int meta) {
        return 0.5F;
    }

    @Override
    public int getSpecialEffect(int meta) {
        return 1;
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        return new ResourceAmount(1, 0, 0, 0);
    }
}
