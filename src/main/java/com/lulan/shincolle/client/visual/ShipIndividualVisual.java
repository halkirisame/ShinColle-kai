package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.shipdata.ShipIndividual;
import net.minecraft.world.entity.Entity;

/** Reads the existing per-entity presentation; never writes shared visual data. */
public final class ShipIndividualVisual {
    private ShipIndividualVisual() { }

    public static ShipIndividual.Presentation presentation(Entity entity) {
        if (entity instanceof GenericHostileShipEntity ship) return ship.individualPresentation();
        if (entity instanceof GenericShipEntity ship) return ship.individualPresentation();
        return ShipIndividual.Presentation.NORMAL;
    }
}
