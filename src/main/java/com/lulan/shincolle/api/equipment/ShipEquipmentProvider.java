package com.lulan.shincolle.api.equipment;

import net.minecraft.world.item.ItemStack;

/**
 * Adapter for third-party Items that cannot implement {@link IShipEquipment} directly.
 */
public interface ShipEquipmentProvider {

    /** Side-safe predicate which must not mutate the supplied stack or game state. */
    boolean matches(ItemStack stack);

    /** Resolves side-safe dynamic values for one stack accepted by {@link #matches(ItemStack)}. */
    ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context);

    /**
     * Precedence among providers: higher values are consulted first, and providers with the same value
     * are consulted in registry ID order. Read each time the provider order is built: for a temporary order
     * answering a lookup made before registration has finished, and once more for the fixed order built when
     * it has. Changes after that are not seen.
     */
    default int priority() {
        return 0;
    }
}
