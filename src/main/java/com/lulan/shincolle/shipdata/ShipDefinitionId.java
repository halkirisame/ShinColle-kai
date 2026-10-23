package com.lulan.shincolle.shipdata;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** File-derived ship identity. Explicit namespaces are required in textual references. */
public record ShipDefinitionId(ResourceLocation location) {
    public ShipDefinitionId {
        Objects.requireNonNull(location, "location");
        ShipDefinitionFormat.requireId(location.toString());
    }

    public static ShipDefinitionId parse(String value) {
        return new ShipDefinitionId(ShipDefinitionFormat.requireId(value));
    }

    @Override
    public String toString() {
        return location.toString();
    }
}
