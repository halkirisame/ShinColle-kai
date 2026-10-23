package com.lulan.shincolle.shipdata;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, validated server data. Contains neither live entities nor client visual resources. */
public record ShipDefinition(ShipDefinitionId id, int schema, String displayName,
                             ResourceLocation shipType, ResourceLocation visual,
                             Map<String, Double> baseStats, Map<String, Double> growth,
                              Equipment equipment, Acquisition construction, Acquisition spawn, Behavior behavior,
                              Dimensions dimensions, double mountOffset, int equipType, ResourceLocation attackProfile,
                              Map<String, Double> hostileModifiers) {
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

    /** Explicit behavior selections; omission does not override an entity's built-in defaults. */
    public record Behavior(Optional<ResourceLocation> ai, List<ResourceLocation> attacks) {
        public Behavior {
            Objects.requireNonNull(ai, "ai");
            attacks = List.copyOf(attacks);
        }
    }
}
