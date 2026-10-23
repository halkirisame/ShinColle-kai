package com.lulan.shincolle.shipdata;

import net.minecraft.resources.ResourceLocation;

/** The complete client-visible projection. Deliberately excludes stats and attack policy. */
public record ShipPresentation(String displayName, ResourceLocation visual, ShipDefinition.Dimensions dimensions,
                               ResourceLocation shipType, double mountOffset, int equipType) {
    public static ShipPresentation of(ShipDefinition definition) {
        return new ShipPresentation(definition.displayName(), definition.visual(), definition.dimensions(),
                definition.shipType(), definition.mountOffset(), definition.equipType());
    }
}
