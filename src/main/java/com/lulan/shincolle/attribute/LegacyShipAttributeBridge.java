package com.lulan.shincolle.attribute;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Explicit compatibility bridge between the original 21-slot arrays and stable attribute IDs.
 */
public final class LegacyShipAttributeBridge {

    public static final int LEGACY_LENGTH = 21;
    private static final List<ResourceLocation> LEGACY_ORDER = CoreShipAttributes.LEGACY_ORDER;

    private LegacyShipAttributeBridge() {
    }

    public static ResourceLocation idFromLegacyIndex(int index) {
        if (index < 0 || index >= LEGACY_ORDER.size()) {
            throw new IndexOutOfBoundsException("Legacy ship attribute index " + index);
        }
        return LEGACY_ORDER.get(index);
    }

    public static int legacyIndex(ResourceLocation id) {
        return LEGACY_ORDER.indexOf(Objects.requireNonNull(id, "id"));
    }

    public static ShipAttributeValues fromLegacyArray(float[] values) {
        return fromLegacyArray(values, ShipAttributeLayout.current());
    }

    /**
     * Converts against a supplied layout for addon preflight validation and isolated tests.
     */
    public static ShipAttributeValues fromLegacyArray(float[] values, ShipAttributeLayout layout) {
        requireLegacyLength(values);
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(layout);
        for (int i = 0; i < LEGACY_LENGTH; i++) {
            result.set(LEGACY_ORDER.get(i), values[i]);
        }
        return result.build();
    }

    public static float[] toLegacyArray(ShipAttributeValues values) {
        Objects.requireNonNull(values, "values");
        float[] result = new float[LEGACY_LENGTH];
        for (int i = 0; i < LEGACY_LENGTH; i++) {
            result[i] = values.get(LEGACY_ORDER.get(i));
        }
        return result;
    }

    private static void requireLegacyLength(float[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length != LEGACY_LENGTH) {
            throw new IllegalArgumentException("Legacy ship attribute array must contain exactly 21 values");
        }
    }
}
