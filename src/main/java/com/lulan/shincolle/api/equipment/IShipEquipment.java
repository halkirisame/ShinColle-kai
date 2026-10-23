package com.lulan.shincolle.api.equipment;

/**
 * Highest-priority canonical extension point for an Item that is ship equipment itself.
 */
public interface IShipEquipment {

    /**
     * Resolves side-safe dynamic values for this Item. The result must use
     * {@link ShipEquipmentContext#layout()} and must not mutate game state.
     */
    ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context);
}
