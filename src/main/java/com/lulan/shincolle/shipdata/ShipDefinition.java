package com.lulan.shincolle.shipdata;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable, validated server data. Contains neither live entities nor client visual resources. */
public record ShipDefinition(ShipDefinitionId id, int schema, String displayName,
                             ResourceLocation shipType, ResourceLocation visual,
                             Map<String, Double> baseStats, Map<String, Double> growth,
                              Equipment equipment, Construction construction, Acquisition spawn, Behavior behavior,
                              Dimensions dimensions, double mountOffset, int equipType, ResourceLocation attackProfile,
                               Map<String, Double> hostileModifiers, HostileVariants hostileVariants) {
    public ShipDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(shipType, "shipType");
        Objects.requireNonNull(visual, "visual");
        baseStats = Map.copyOf(baseStats);
        growth = Map.copyOf(growth);
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(construction, "construction");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(dimensions, "dimensions");
        Objects.requireNonNull(attackProfile, "attackProfile");
        hostileModifiers = Map.copyOf(hostileModifiers);
        Objects.requireNonNull(hostileVariants, "hostileVariants");
    }

    public record Dimensions(float width, float height) {
    }

    public record Equipment(int slots, List<ResourceLocation> initial) {
        public Equipment {
            initial = List.copyOf(initial);
        }
    }

    /** Eligibility and relative selection weight; consumers are separate from the loader. */
    public record Acquisition(boolean enabled, int weight) {
    }

    public enum ConstructionMode { MATERIAL, WEIGHTED }

    public enum ConstructionTier { SMALL, LARGE }

    public enum MaterialBias {
        GRUDGE(0), ABYSSIUM(1), AMMO(2), POLYMETAL(3);

        private final int index;

        MaterialBias(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }

    public record Construction(boolean enabled, int weight, ConstructionMode mode,
                               Optional<ConstructionTier> tier, OptionalInt materialMean, Optional<MaterialBias> materialBias) {
        public Construction {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(materialMean, "materialMean");
            Objects.requireNonNull(materialBias, "materialBias");
        }

        public boolean matches(int buildType) {
            return tier.isEmpty() || tier.get() == (buildType == 0 ? ConstructionTier.SMALL : ConstructionTier.LARGE);
        }
    }

    /** Explicit behavior selections; omission does not override an entity's built-in defaults. */
    public record Behavior(Optional<ResourceLocation> ai, List<ResourceLocation> attacks) {
        public Behavior {
            Objects.requireNonNull(ai, "ai");
            attacks = List.copyOf(attacks);
        }
    }
}
