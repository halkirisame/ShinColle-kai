package com.lulan.shincolle.item;

import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import net.minecraft.world.item.ItemStack;

/**
 * Interface for items that provide resources when fed or consumed.
 * Returns an immutable amount of grudge, abyssium, ammo, and polymetal.
 */
public interface IShipResourceItem {
    ResourceAmount getResourceAmount(ItemStack stack);
}
