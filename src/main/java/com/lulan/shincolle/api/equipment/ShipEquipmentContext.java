package com.lulan.shincolle.api.equipment;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Immutable, side-safe input supplied when resolving one ship-equipment stack.
 *
 * <p>The captured stack and every value returned by {@link #stack()} are defensive
 * copies. Implementations may inspect their copy, but must not change a world,
 * entity, packet state, or the caller's stack while resolving attributes.</p>
 */
public final class ShipEquipmentContext {

    private final ItemStack stack;
    private final ShipAttributeLayout layout;

    public ShipEquipmentContext(ItemStack stack, ShipAttributeLayout layout) {
        this.stack = Objects.requireNonNull(stack, "stack").copy();
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Returns a defensive copy of the stack being resolved. */
    public ItemStack stack() {
        return this.stack.copy();
    }

    /** Returns the canonical attribute layout expected from this resolution. */
    public ShipAttributeLayout layout() {
        return this.layout;
    }
}
