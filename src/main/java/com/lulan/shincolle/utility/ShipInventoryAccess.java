package com.lulan.shincolle.utility;

import com.lulan.shincolle.shipdata.DefinitionBackedShip;
import net.minecraft.world.entity.Entity;

/** Gates live transactions without blocking NBT initialization or snapshot restoration. */
public final class ShipInventoryAccess {
    private ShipInventoryAccess() { }

    public static boolean isAvailable(Entity owner) {
        if (!(owner instanceof DefinitionBackedShip ship)) return true;
        var state = ship.definitionState();
        // Block immediately after reload, even when a block tick or packet precedes the entity tick.
        // On restoration, keep blocking until the entity has restored its preserved snapshot.
        return !state.dormant() && (owner.level().isClientSide() || state.definition() != null);
    }
}
