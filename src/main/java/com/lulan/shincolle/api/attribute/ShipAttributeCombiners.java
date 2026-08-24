package com.lulan.shincolle.api.attribute;

/**
 * Reusable layer-combination rules. Addons may also provide their own pure combiner.
 */
public final class ShipAttributeCombiners {

    public static final ShipAttributeCombiner ADDITIVE = context -> context.raw()
            + context.equipment() + context.morale() + context.potion() + context.formation();

    public static final ShipAttributeCombiner DEFENSE = context -> (context.raw() + context.equipment()
            + (context.morale() + context.potion()) * context.resolvedScale()) * context.formation();

    private ShipAttributeCombiners() {
    }

    public static ShipAttributeCombiner scaledAdditive(boolean includeFormation) {
        return context -> context.raw() + context.equipment()
                + (context.morale() + context.potion()
                + (includeFormation ? context.formation() : 0F)) * context.resolvedScale();
    }

    public static ShipAttributeCombiner multiplicative(float potionMultiplier) {
        if (!Float.isFinite(potionMultiplier)) {
            throw new IllegalArgumentException("potionMultiplier must be finite");
        }
        return context -> (context.raw() + context.equipment()
                + context.potion() * potionMultiplier * context.resolvedScale())
                * context.morale() * context.formation();
    }
}
