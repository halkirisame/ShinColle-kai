package com.lulan.shincolle.api.equipment;

import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deeply immutable canonical result of resolving one item as ship equipment.
 */
public final class ResolvedShipEquipment {

    public static final String CANNON_COMPATIBILITY = "cannon";
    public static final String AIRCRAFT_COMPATIBILITY = "aircraft";
    public static final Set<String> DEFAULT_COMPATIBILITY =
            Set.of(CANNON_COMPATIBILITY, AIRCRAFT_COMPATIBILITY);

    private final ShipAttributeValues attributes;
    private final Set<String> compatibility;
    private final Map<ResourceLocation, ShipAttackEffect> attackEffects;
    private final ResourceLocation definitionId;
    private final ResourceLocation providerId;

    public ResolvedShipEquipment(ShipAttributeValues attributes, Set<String> compatibility) {
        this(attributes, compatibility, Map.of(), null, null);
    }

    public ResolvedShipEquipment(ShipAttributeValues attributes, Set<String> compatibility,
                                 Map<ResourceLocation, ShipAttackEffect> attackEffects) {
        this(attributes, compatibility, attackEffects, null, null);
    }

    public ResolvedShipEquipment(ShipAttributeValues attributes, Set<String> compatibility,
                                 ResourceLocation definitionId, ResourceLocation providerId) {
        this(attributes, compatibility, Map.of(), definitionId, providerId);
    }

    public ResolvedShipEquipment(ShipAttributeValues attributes, Set<String> compatibility,
                                 Map<ResourceLocation, ShipAttackEffect> attackEffects,
                                 ResourceLocation definitionId, ResourceLocation providerId) {
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.compatibility = validateCompatibility(compatibility);
        this.attackEffects = validateAttackEffects(attackEffects);
        this.definitionId = definitionId;
        this.providerId = providerId;
    }

    public ShipAttributeValues attributes() {
        return this.attributes;
    }

    public Set<String> compatibility() {
        return this.compatibility;
    }

    public boolean isCompatibleWith(String value) {
        return this.compatibility.contains(Objects.requireNonNull(value, "value"));
    }

    /** Declarative MobEffects contributed by this resolved equipment stack. */
    public Map<ResourceLocation, ShipAttackEffect> attackEffects() {
        return this.attackEffects;
    }

    /** Optional datapack definition that contributed the JSON base values. */
    public Optional<ResourceLocation> definitionId() {
        return Optional.ofNullable(this.definitionId);
    }

    /** Optional external provider selected after an Item did not implement the canonical API. */
    public Optional<ResourceLocation> providerId() {
        return Optional.ofNullable(this.providerId);
    }

    private static Set<String> validateCompatibility(Set<String> values) {
        Objects.requireNonNull(values, "compatibility");
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            String nonNullValue = Objects.requireNonNull(value, "compatibility value");
            if (!CANNON_COMPATIBILITY.equals(nonNullValue) && !AIRCRAFT_COMPATIBILITY.equals(nonNullValue)) {
                throw new IllegalArgumentException("Unknown ship equipment compatibility " + nonNullValue);
            }
            copy.add(nonNullValue);
        }
        return Set.copyOf(copy);
    }

    private static Map<ResourceLocation, ShipAttackEffect> validateAttackEffects(
            Map<ResourceLocation, ShipAttackEffect> values) {
        Objects.requireNonNull(values, "attackEffects");
        java.util.LinkedHashMap<ResourceLocation, ShipAttackEffect> copy = new java.util.LinkedHashMap<>();
        values.forEach((id, effect) -> {
            ResourceLocation nonNullId = Objects.requireNonNull(id, "attack effect id");
            ShipAttackEffect nonNullEffect = Objects.requireNonNull(effect, "attack effect");
            if (!nonNullId.equals(nonNullEffect.effectId())) {
                throw new IllegalArgumentException("Attack effect key does not match value ID " + nonNullId);
            }
            copy.put(nonNullId, nonNullEffect);
        });
        return Map.copyOf(copy);
    }
}
