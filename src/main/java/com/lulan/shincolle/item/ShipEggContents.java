package com.lulan.shincolle.item;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * What a saved ship egg is holding, split the way the ship's own inventory
 * screen splits it: a fixed six-slot equipment row (empty slots included, so
 * the row keeps its shape) and everything else it is carrying (empty slots
 * omitted).
 */
public record ShipEggContents(List<ItemStack> equipment, List<ItemStack> cargo) {

    public static final ShipEggContents EMPTY = new ShipEggContents(List.of(), List.of());

    public boolean isEmpty() {
        return this.equipment.stream().allMatch(ItemStack::isEmpty) && this.cargo.isEmpty();
    }

    /** Count of non-empty stacks across both equipment and cargo. */
    public int itemCount() {
        return (int) this.equipment.stream().filter(s -> !s.isEmpty()).count() + this.cargo.size();
    }
}
