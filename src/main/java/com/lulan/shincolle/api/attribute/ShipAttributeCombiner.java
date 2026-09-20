package com.lulan.shincolle.api.attribute;

/**
 * Combines one ship attribute's raw and modifier layers into its final value.
 */
@FunctionalInterface
public interface ShipAttributeCombiner {

    float combine(ShipAttributeCombineContext context);
}
