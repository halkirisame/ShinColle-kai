package com.lulan.shincolle.api.attribute;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, code-registered definition of one extensible ship attribute.
 */
public final class ShipAttributeType {

    private final Map<ShipAttributeLayer, Float> layerDefaults;
    private final ShipAttributeCombiner combiner;
    private final Float minimum;
    private final Float maximum;
    private final ShipAttributeScaleGroup scaleGroup;
    private final ShipAttributeDisplayFormat displayFormat;
    private final ShipAttributeEnchantRule enchantRule;
    private final ResourceLocation enchantEffectSource;
    private final String translationKey;

    private ShipAttributeType(Builder builder) {
        this.layerDefaults = Map.copyOf(builder.layerDefaults);
        this.combiner = builder.combiner;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.scaleGroup = builder.scaleGroup;
        this.displayFormat = builder.displayFormat;
        this.enchantRule = builder.enchantRule;
        this.enchantEffectSource = builder.enchantEffectSource;
        this.translationKey = builder.translationKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float defaultValue(ShipAttributeLayer layer) {
        Objects.requireNonNull(layer, "layer");
        if (layer == ShipAttributeLayer.BUFFED) {
            throw new IllegalArgumentException("BUFFED is calculated and has no layer default");
        }
        return this.layerDefaults.get(layer);
    }

    public float combine(ShipAttributeCombineContext context) {
        return this.constrain(this.combineUnbounded(context));
    }

    /**
     * Combines layers without applying type bounds. The authoritative engine uses this so legacy
     * config limits can be applied before the original minimum constraints.
     */
    public float combineUnbounded(ShipAttributeCombineContext context) {
        float result = this.combiner.combine(Objects.requireNonNull(context, "context"));
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException("Ship attribute combiner returned a non-finite value");
        }
        return result;
    }

    public float constrain(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Ship attribute value must be finite");
        }
        float result = value;
        if (this.minimum != null && result < this.minimum) {
            result = this.minimum;
        }
        if (this.maximum != null && result > this.maximum) {
            result = this.maximum;
        }
        return result;
    }

    public Float minimum() {
        return this.minimum;
    }

    public Float maximum() {
        return this.maximum;
    }

    public ShipAttributeScaleGroup scaleGroup() {
        return this.scaleGroup;
    }

    public ShipAttributeDisplayFormat displayFormat() {
        return this.displayFormat;
    }

    public ShipAttributeEnchantRule enchantRule() {
        return this.enchantRule;
    }

    /**
     * Attribute whose enchant-effect value drives this attribute. Defaults to the attribute itself.
     */
    public ResourceLocation enchantEffectSource(ResourceLocation ownId) {
        return this.enchantEffectSource == null
                ? Objects.requireNonNull(ownId, "ownId")
                : this.enchantEffectSource;
    }

    public String translationKey(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (this.translationKey != null) {
            return this.translationKey;
        }
        return "ship_attribute." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /**
     * Builder defaults describe an additive, unbounded custom attribute whose layers start at zero.
     */
    public static final class Builder {

        private final EnumMap<ShipAttributeLayer, Float> layerDefaults =
                new EnumMap<>(ShipAttributeLayer.class);
        private ShipAttributeCombiner combiner = ShipAttributeCombiners.ADDITIVE;
        private Float minimum;
        private Float maximum;
        private ShipAttributeScaleGroup scaleGroup = ShipAttributeScaleGroup.NONE;
        private ShipAttributeDisplayFormat displayFormat = ShipAttributeDisplayFormat.DECIMAL;
        private ShipAttributeEnchantRule enchantRule = ShipAttributeEnchantRule.NONE;
        private ResourceLocation enchantEffectSource;
        private String translationKey;

        private Builder() {
            for (ShipAttributeLayer layer : ShipAttributeLayer.values()) {
                if (layer != ShipAttributeLayer.BUFFED) {
                    this.layerDefaults.put(layer, 0F);
                }
            }
        }

        public Builder defaultValue(ShipAttributeLayer layer, float value) {
            Objects.requireNonNull(layer, "layer");
            if (layer == ShipAttributeLayer.BUFFED) {
                throw new IllegalArgumentException("BUFFED is calculated and cannot have a default");
            }
            requireFinite(value, "layer default");
            this.layerDefaults.put(layer, value);
            return this;
        }

        public Builder combiner(ShipAttributeCombiner value) {
            this.combiner = Objects.requireNonNull(value, "combiner");
            return this;
        }

        public Builder minimum(float value) {
            requireFinite(value, "minimum");
            this.minimum = value;
            return this;
        }

        public Builder maximum(float value) {
            requireFinite(value, "maximum");
            this.maximum = value;
            return this;
        }

        public Builder scaleGroup(ShipAttributeScaleGroup value) {
            this.scaleGroup = Objects.requireNonNull(value, "scaleGroup");
            return this;
        }

        public Builder displayFormat(ShipAttributeDisplayFormat value) {
            this.displayFormat = Objects.requireNonNull(value, "displayFormat");
            return this;
        }

        public Builder enchantRule(ShipAttributeEnchantRule value) {
            this.enchantRule = Objects.requireNonNull(value, "enchantRule");
            return this;
        }

        public Builder enchantEffectSource(ResourceLocation value) {
            this.enchantEffectSource = Objects.requireNonNull(value, "enchantEffectSource");
            return this;
        }

        public Builder translationKey(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("translationKey must not be blank");
            }
            this.translationKey = value;
            return this;
        }

        public ShipAttributeType build() {
            if (this.minimum != null && this.maximum != null && this.minimum > this.maximum) {
                throw new IllegalArgumentException("minimum must not be greater than maximum");
            }
            return new ShipAttributeType(this);
        }

        private static void requireFinite(float value, String name) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
    }
}
