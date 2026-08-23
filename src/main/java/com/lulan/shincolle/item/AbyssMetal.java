package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import net.minecraft.world.item.ItemStack;

/**
 * Abyss Metal material item - abyssium and polymetal resource.
 * Meta 0 = Abyssium, Meta 1 = Polymetal
 */
public class AbyssMetal extends BasicItem implements IShipResourceItem, IShipFoodItem {

    public AbyssMetal() {
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
        if (meta == 1) {
            return 4;
        }
        return 2;
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        int meta = stack.getDamageValue();
        // [PORT] 1.10.2 -> 1.20.1: 旧 meta==1 は polymetal 変種だったが、
        // 1.20.1 では abyss_metal_1 の Item が存在せず到達しない。
        // variant を Item 分割へ移す際に復活させるかは未決。
        if (meta == 1) {
            return new ResourceAmount(0, 0, 0, 1);
        }
        return new ResourceAmount(0, 1, 0, 0);
    }
}
