package com.lulan.shincolle.equipdata;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * One immutable generation of all ship-equipment definitions and their resolved indexes.
 */
public record EquipDataSnapshot(
        Map<ResourceLocation, EquipDefinition> byId,
        Map<ResourceLocation, Map<Integer, EquipDefinition>> byItemVariant,
        Map<Integer, EquipDefinition> byLegacyId
) {

    public static final EquipDataSnapshot EMPTY = new EquipDataSnapshot(Map.of(), Map.of(), Map.of());

    public EquipDataSnapshot {
        byId = Map.copyOf(byId);

        Map<ResourceLocation, Map<Integer, EquipDefinition>> immutableItemVariants = new HashMap<>();
        byItemVariant.forEach((item, variants) -> immutableItemVariants.put(item, Map.copyOf(variants)));
        byItemVariant = Map.copyOf(immutableItemVariants);

        byLegacyId = Map.copyOf(byLegacyId);
    }

    public EquipDefinition get(ResourceLocation id) {
        return byId.get(id);
    }

    public EquipDefinition byItemVariant(ResourceLocation item, int variant) {
        Map<Integer, EquipDefinition> variants = byItemVariant.get(item);
        return variants == null ? null : variants.get(variant);
    }

    public EquipDefinition byLegacyId(int legacyId) {
        return byLegacyId.get(legacyId);
    }

    public Collection<EquipDefinition> all() {
        return byId.values();
    }
}
