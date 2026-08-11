package com.lulan.shincolle.equip.curios;

import com.lulan.shincolle.entity.BasicEntityShip;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * ShinColle only recalculates a ship's equipment stats when its own equip
 * slots change, so swapping something in the Curios-backed slot would
 * otherwise have no effect until something else triggered a recalc.
 *
 * <p>Only registered on the Forge event bus when Curios is loaded (see
 * {@code ShinColle}'s constructor) - never referenced otherwise.
 */
public class ShipCuriosRecalcHandler {

    @SubscribeEvent
    public void onCurioChanged(CurioChangeEvent event) {
        if (event.getEntity() instanceof BasicEntityShip ship && !ship.level().isClientSide) {
            // flag 2 = recalculate equipment contributions, true = sync to clients
            ship.calcShipAttributes(2, true);
        }
    }
}
