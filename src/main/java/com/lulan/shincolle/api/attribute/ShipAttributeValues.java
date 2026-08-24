package com.lulan.shincolle.api.attribute;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deeply immutable ship-attribute values backed by a deterministic dense layout.
 */
public final class ShipAttributeValues {

    private final ShipAttributeLayout layout;
    private final float[] values;

    private ShipAttributeValues(ShipAttributeLayout layout, float[] values) {
        this.layout = Objects.requireNonNull(layout, "layout");
        if (values.length != layout.size()) {
            throw new IllegalArgumentException("Value count must match the ship attribute layout");
        }
        this.values = values.clone();
        for (float value : this.values) {
            requireFinite(value);
        }
    }

    public static Builder builder(ShipAttributeLayout layout) {
        return new Builder(layout, new float[Objects.requireNonNull(layout, "layout").size()]);
    }

    public static ShipAttributeValues zero(ShipAttributeLayout layout) {
        return builder(layout).build();
    }

    public static ShipAttributeValues defaults(ShipAttributeLayout layout, ShipAttributeLayer layer) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(layer, "layer");
        if (layer == ShipAttributeLayer.BUFFED) {
            throw new IllegalArgumentException("BUFFED is calculated and has no layer defaults");
        }
        float[] defaults = new float[layout.size()];
        for (int i = 0; i < defaults.length; i++) {
            defaults[i] = layout.typeAt(i).defaultValue(layer);
        }
        return new ShipAttributeValues(layout, defaults);
    }

    public ShipAttributeLayout layout() {
        return this.layout;
    }

    public float get(ResourceLocation id) {
        int index = this.layout.indexOf(id);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown ship attribute " + id);
        }
        return this.values[index];
    }

    public float get(ShipAttributeType type) {
        int index = this.layout.indexOf(type);
        if (index < 0) {
            throw new IllegalArgumentException("Ship attribute type is not part of this layout");
        }
        return this.values[index];
    }

    public Map<ResourceLocation, Float> asMap() {
        Map<ResourceLocation, Float> result = new LinkedHashMap<>();
        for (int i = 0; i < this.values.length; i++) {
            result.put(this.layout.idAt(i), this.values[i]);
        }
        return Collections.unmodifiableMap(result);
    }

    public Builder toBuilder() {
        return new Builder(this.layout, this.values);
    }

    public static final class Builder {

        private final ShipAttributeLayout layout;
        private final float[] values;

        private Builder(ShipAttributeLayout layout, float[] initialValues) {
            this.layout = Objects.requireNonNull(layout, "layout");
            this.values = initialValues.clone();
        }

        public Builder set(ResourceLocation id, float value) {
            requireFinite(value);
            this.values[requireIndex(id)] = value;
            return this;
        }

        public Builder set(ShipAttributeType type, float value) {
            requireFinite(value);
            int index = this.layout.indexOf(type);
            if (index < 0) {
                throw new IllegalArgumentException("Ship attribute type is not part of this layout");
            }
            this.values[index] = value;
            return this;
        }

        public Builder add(ResourceLocation id, float value) {
            requireFinite(value);
            int index = requireIndex(id);
            float result = this.values[index] + value;
            requireFinite(result);
            this.values[index] = result;
            return this;
        }

        public Builder add(ShipAttributeType type, float value) {
            requireFinite(value);
            int index = this.layout.indexOf(type);
            if (index < 0) {
                throw new IllegalArgumentException("Ship attribute type is not part of this layout");
            }
            float result = this.values[index] + value;
            requireFinite(result);
            this.values[index] = result;
            return this;
        }

        public ShipAttributeValues build() {
            return new ShipAttributeValues(this.layout, this.values);
        }

        private int requireIndex(ResourceLocation id) {
            int index = this.layout.indexOf(Objects.requireNonNull(id, "id"));
            if (index < 0) {
                throw new IllegalArgumentException("Unknown ship attribute " + id);
            }
            return index;
        }
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Ship attribute value must be finite");
        }
    }
}
