package com.lulan.shincolle.integration.kubejs;

import com.lulan.shincolle.api.attribute.ShipAttributeCombiners;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeEnchantRule;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Deterministic KubeJS DSL for {@link ShipAttributeType}. Script callbacks are deliberately not accepted as
 * combiners: combat calculation remains Java-owned and server-authoritative.
 */
public final class ShipAttributeTypeKubeJSBuilder extends BuilderBase<ShipAttributeType> {

    private static final int MAX_ENUM_NAME_LENGTH = 64;
    private static final int MAX_RESOURCE_LOCATION_LENGTH = 256;

    private final ShipAttributeType.Builder delegate = ShipAttributeType.builder();

    public ShipAttributeTypeKubeJSBuilder(ResourceLocation id) {
        super(id);
    }

    @Override
    public RegistryInfo<ShipAttributeType> getRegistryType() {
        return ShinColleKubeJSPlugin.shipAttributeRegistry();
    }

    public ShipAttributeTypeKubeJSBuilder raw(float value) {
        return this.defaultValue(ShipAttributeLayer.RAW, value);
    }

    public ShipAttributeTypeKubeJSBuilder equipment(float value) {
        return this.defaultValue(ShipAttributeLayer.EQUIPMENT, value);
    }

    public ShipAttributeTypeKubeJSBuilder morale(float value) {
        return this.defaultValue(ShipAttributeLayer.MORALE, value);
    }

    public ShipAttributeTypeKubeJSBuilder potion(float value) {
        return this.defaultValue(ShipAttributeLayer.POTION, value);
    }

    public ShipAttributeTypeKubeJSBuilder formation(float value) {
        return this.defaultValue(ShipAttributeLayer.FORMATION, value);
    }

    public ShipAttributeTypeKubeJSBuilder defaultValue(String layer, float value) {
        return this.defaultValue(parseEnum(ShipAttributeLayer.class, layer, "layer"), value);
    }

    public ShipAttributeTypeKubeJSBuilder additive() {
        this.delegate.combiner(ShipAttributeCombiners.ADDITIVE);
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder defense() {
        this.delegate.combiner(ShipAttributeCombiners.DEFENSE);
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder scaledAdditive(boolean includeFormation) {
        this.delegate.combiner(ShipAttributeCombiners.scaledAdditive(includeFormation));
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder multiplicative(float potionMultiplier) {
        this.delegate.combiner(ShipAttributeCombiners.multiplicative(potionMultiplier));
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder minimum(float value) {
        this.delegate.minimum(value);
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder maximum(float value) {
        this.delegate.maximum(value);
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder scaleGroup(String value) {
        this.delegate.scaleGroup(parseEnum(ShipAttributeScaleGroup.class, value, "scaleGroup"));
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder displayFormat(String value) {
        this.delegate.displayFormat(parseEnum(ShipAttributeDisplayFormat.class, value, "displayFormat"));
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder enchantRule(String value) {
        this.delegate.enchantRule(parseEnum(ShipAttributeEnchantRule.class, value, "enchantRule"));
        return this;
    }

    public ShipAttributeTypeKubeJSBuilder enchantEffectSource(String value) {
        this.delegate.enchantEffectSource(parseResourceLocation(value, "enchantEffectSource"));
        return this;
    }

    @Override
    public ShipAttributeTypeKubeJSBuilder translationKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("translationKey must not be blank");
        }
        super.translationKey(key.trim());
        return this;
    }

    @Override
    public ShipAttributeType createObject() {
        return this.delegate.translationKey(this.getBuilderTranslationKey()).build();
    }

    private ShipAttributeTypeKubeJSBuilder defaultValue(ShipAttributeLayer layer, float value) {
        this.delegate.defaultValue(layer, value);
        return this;
    }

    private static ResourceLocation parseResourceLocation(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_RESOURCE_LOCATION_LENGTH) {
            throw new IllegalArgumentException(field + " must be a non-blank ResourceLocation of at most "
                    + MAX_RESOURCE_LOCATION_LENGTH + " characters");
        }
        ResourceLocation id = ResourceLocation.tryParse(value.trim());
        if (id == null) {
            throw new IllegalArgumentException("Invalid " + field + " ResourceLocation '" + value + "'");
        }
        return id;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_ENUM_NAME_LENGTH) {
            throw new IllegalArgumentException(field + " must be a non-blank name of at most "
                    + MAX_ENUM_NAME_LENGTH + " characters");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            String choices = Arrays.stream(type.getEnumConstants()).map(Enum::name)
                    .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Unknown " + field + " '" + value + "'; expected one of: "
                    + choices, exception);
        }
    }
}
