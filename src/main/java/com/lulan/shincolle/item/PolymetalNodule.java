package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import net.minecraft.world.item.ItemStack;

/**
 * Polymetallic Nodule material item.
 * In 1.10.2, this was AbyssMetal with meta=1.
 * In 1.20.1, it is a separate item.
 */
public class PolymetalNodule extends BasicItem implements IShipResourceItem, IShipFoodItem {

    public PolymetalNodule() {
        super(new Properties());
    }

    @Override
    public float getFoodValue(int meta) {
        return 30.0F;
    }

    @Override
    public float getSaturationValue(int meta) {
        return 0.8F;
    }

    @Override
    public int getSpecialEffect(int meta) {
        return 4;
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        return new ResourceAmount(0, 0, 0, 1);
    }
}
