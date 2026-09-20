package com.lulan.shincolle.api.attribute;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Read-only public view of a ship's dynamic attribute layers.
 *
 * <p>The server is authoritative. Tracking clients receive complete dynamic
 * snapshots and may use the same read-only queries for presentation.
 */
public interface ShipAttributeAccess {

    /** Returns an immutable snapshot of one complete attribute layer. */
    ShipAttributeValues shipAttributes(ShipAttributeLayer layer);

    /** Returns one value from a specific immutable layer snapshot. */
    default float shipAttribute(ShipAttributeLayer layer, ResourceLocation id) {
        return shipAttributes(Objects.requireNonNull(layer, "layer"))
                .get(Objects.requireNonNull(id, "id"));
    }

    /** Returns one final, combined attribute value. */
    default float shipAttribute(ResourceLocation id) {
        return shipAttributes(ShipAttributeLayer.BUFFED).get(Objects.requireNonNull(id, "id"));
    }
}
