package com.lulan.shincolle.api.attribute;

/**
 * Immutable input for one attribute's authoritative layer combination.
 */
public record ShipAttributeCombineContext(
        float raw,
        float equipment,
        float morale,
        float potion,
        float formation,
        float resolvedScale
) {

    public ShipAttributeCombineContext {
        requireFinite(raw, "raw");
        requireFinite(equipment, "equipment");
        requireFinite(morale, "morale");
        requireFinite(potion, "potion");
        requireFinite(formation, "formation");
        requireFinite(resolvedScale, "resolvedScale");
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
