package com.lulan.shincolle.visualdata;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.List;
import java.util.Set;

/** Immutable resource-pack presentation data; no client or GeckoLib types. */
public record ShipVisualDefinition(ResourceLocation model, ResourceLocation texture, ResourceLocation animation,
                                   float scale, float shadowRadius, Map<String, String> rig,
                                    Map<ShipVisualEvent, String> animations, Map<String, List<String>> expressions,
                                    List<String> glowBones, HeldItem heldItem, Lod lod) {
    public static final Set<String> ROLES = Set.of("root", "body", "head", "arm_left", "arm_right",
            "leg_left", "leg_right", "equipment", "held_item", "held_item_left", "muzzle_light", "muzzle_heavy", "face", "mouth", "flush");

    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
    }

    public record HeldItem(Vector offset, Vector rotate) {
        public static final HeldItem DEFAULT = new HeldItem(Vector.ZERO, Vector.ZERO);
    }

    public record Lod(float distance, List<String> hideBones) {
        public static final Lod DEFAULT = new Lod(48, List.of());

        public Lod {
            hideBones = List.copyOf(hideBones);
        }
    }

    public ShipVisualDefinition(ResourceLocation model, ResourceLocation texture, ResourceLocation animation,
                                float scale, float shadowRadius, Map<String, String> rig,
                                Map<ShipVisualEvent, String> animations, Map<String, List<String>> expressions) {
        this(model, texture, animation, scale, shadowRadius, rig, animations, expressions,
                List.of(), HeldItem.DEFAULT, Lod.DEFAULT);
    }

    public ShipVisualDefinition {
        rig = Map.copyOf(rig);
        animations = Map.copyOf(animations);
        expressions = expressions.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        glowBones = List.copyOf(glowBones);
    }

    public String bone(String role) {
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Unknown rig role " + role);
        return rig.getOrDefault(role, role);
    }

    public String clip(ShipVisualEvent event) {
        return animations.getOrDefault(event, event.key());
    }
}
