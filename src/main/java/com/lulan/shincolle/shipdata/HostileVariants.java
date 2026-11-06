package com.lulan.shincolle.shipdata;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable definition metadata, not the selected or saved state of an entity. */
public record HostileVariants(int normalWeight, List<Variant> variants) {
    public static final HostileVariants NONE = new HostileVariants(100, List.of());

    public HostileVariants {
        variants = List.copyOf(variants);
    }

    /** Returns null when this definition does not contain the stable variant ID. */
    public Variant get(String id) {
        return variants.stream().filter(variant -> variant.id().equals(id)).findFirst().orElse(null);
    }

    /** Bounded by 17 slots of at most 10000 each in validated definitions. */
    public int totalWeight() {
        return normalWeight + variants.stream().mapToInt(Variant::weight).sum();
    }

    public enum Rarity { COMMON, RARE, EPIC, LEGENDARY }

    public record Variant(String id, Optional<String> name, Rarity rarity, int weight,
                          List<ResourceLocation> traits, Map<String, Double> hostileModifiers,
                          Optional<ResourceLocation> visualVariant, boolean redAura) {
        public Variant {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(rarity, "rarity");
            traits = List.copyOf(traits);
            hostileModifiers = Map.copyOf(hostileModifiers);
            Objects.requireNonNull(visualVariant, "visualVariant");
        }
    }
}
