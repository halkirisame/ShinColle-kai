package com.lulan.shincolle.visualdata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Known fields are strict; future fields warn and are ignored. */
public final class ShipVisualValidator {
    public static final String DIRECTORY = "shincolle_kai/visuals";
    public static final int MAX_NAME_LENGTH = 128;
    public static final Set<String> FIELDS = Set.of("$schema", "schema", "model", "texture", "animation", "scale",
            "shadow_radius", "rig", "animations", "expressions", "glow_bones", "held_item", "lod");

    public ShipVisualDefinition validate(JsonElement element, Consumer<String> warning) {
        JsonObject json = object(element, "visual");
        known(json, FIELDS, warning);
        JsonElement schema = json.get("schema");
        try {
            if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()
                    || schema.getAsBigDecimal().intValueExact() != 1) {
                throw new IllegalArgumentException("schema must be integer 1");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("schema must be integer 1", exception);
        }
        if (json.has("$schema")) string(json.get("$schema"), "$schema");
        ResourceLocation model = id(json.get("model"), "model");
        ResourceLocation texture = id(json.get("texture"), "texture");
        ResourceLocation animation = id(json.get("animation"), "animation");
        Map<String, String> rig = new HashMap<>();
        JsonObject roles = json.has("rig") ? object(json.get("rig"), "rig") : new JsonObject();
        known(roles, ShipVisualDefinition.ROLES, warning);
        for (String role : ShipVisualDefinition.ROLES) {
            if (roles.has(role)) rig.put(role, name(roles.get(role), "rig." + role));
        }
        Map<ShipVisualEvent, String> clips = new HashMap<>();
        JsonObject animations = json.has("animations") ? object(json.get("animations"), "animations") : new JsonObject();
        Set<String> events = java.util.Arrays.stream(ShipVisualEvent.values()).map(ShipVisualEvent::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        known(animations, events, warning);
        for (ShipVisualEvent event : ShipVisualEvent.values()) {
            if (animations.has(event.key())) clips.put(event, name(animations.get(event.key()), "animations." + event.key()));
        }
        Map<String, List<String>> expressions = new HashMap<>();
        JsonObject slots = json.has("expressions") ? object(json.get("expressions"), "expressions") : new JsonObject();
        known(slots, ShipVisualPresentation.SLOT_SIZES.keySet(), warning);
        for (var slot : ShipVisualPresentation.SLOT_SIZES.entrySet()) {
            if (!slots.has(slot.getKey())) continue;
            JsonElement value = slots.get(slot.getKey());
            if (!value.isJsonArray() || value.getAsJsonArray().isEmpty() || value.getAsJsonArray().size() > slot.getValue()) {
                throw new IllegalArgumentException("expressions." + slot.getKey() + " must be a nonempty bounded bone list");
            }
            List<String> names = new ArrayList<>();
            for (JsonElement bone : value.getAsJsonArray()) names.add(name(bone, "expressions." + slot.getKey()));
            if (Set.copyOf(names).size() != names.size()) throw new IllegalArgumentException("Duplicate expression bone");
            expressions.put(slot.getKey(), names);
        }
        JsonObject held = json.has("held_item") ? object(json.get("held_item"), "held_item") : new JsonObject();
        known(held, Set.of("offset", "rotate"), warning);
        var heldItem = new ShipVisualDefinition.HeldItem(vector(held, "offset"), vector(held, "rotate"));
        JsonObject lod = json.has("lod") ? object(json.get("lod"), "lod") : new JsonObject();
        known(lod, Set.of("distance", "hide_bones"), warning);
        return new ShipVisualDefinition(model, texture, animation,
                number(json, "scale", 1, BigDecimal.valueOf((double) Float.MIN_VALUE), 16),
                number(json, "shadow_radius", 0.35F, BigDecimal.ZERO, 16), rig, clips, expressions,
                boneList(json, "glow_bones"), heldItem,
                new ShipVisualDefinition.Lod(number(lod, "distance", 48, BigDecimal.ONE, 256), boneList(lod, "hide_bones")));
    }

    private static List<String> boneList(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        JsonElement value = json.get(key);
        if (!value.isJsonArray() || value.getAsJsonArray().size() > 128) {
            throw new IllegalArgumentException(key + " must be a bone list of at most 128 entries");
        }
        List<String> names = new ArrayList<>();
        for (JsonElement bone : value.getAsJsonArray()) names.add(name(bone, key));
        if (Set.copyOf(names).size() != names.size()) throw new IllegalArgumentException("Duplicate bone in " + key);
        return names;
    }

    private static ShipVisualDefinition.Vector vector(JsonObject json, String key) {
        if (!json.has(key)) return ShipVisualDefinition.Vector.ZERO;
        JsonElement value = json.get(key);
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(key + " must be a three-number vector");
        }
        double[] components = new double[3];
        for (int index = 0; index < 3; index++) {
            JsonElement component = value.getAsJsonArray().get(index);
            if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()
                    || component.getAsBigDecimal().abs().compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException(key + " must contain finite doubles");
            }
            components[index] = component.getAsDouble();
        }
        return new ShipVisualDefinition.Vector(components[0], components[1], components[2]);
    }

    private static float number(JsonObject json, String key, float fallback, BigDecimal min, int max) {
        if (!json.has(key)) return fallback;
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(element.getAsDouble())) throw new IllegalArgumentException(key + " must be finite number");
        BigDecimal value = element.getAsBigDecimal();
        if (value.compareTo(min) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException(key + " outside " + min + ".." + max);
        }
        return value.floatValue();
    }

    private static String name(JsonElement element, String key) {
        String value = string(element, key);
        if (value.isEmpty() || value.codePointCount(0, value.length()) > MAX_NAME_LENGTH
                || value.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException(key + " must be 1..128 characters without controls");
        }
        return value;
    }

    private static ResourceLocation id(JsonElement element, String key) {
        String value = string(element, key);
        if (value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException(key + " must be explicit namespaced resource ID (max 256 characters)");
        }
        return new ResourceLocation(value);
    }

    private static String string(JsonElement element, String key) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be string");
        }
        return element.getAsString();
    }

    private static JsonObject object(JsonElement element, String key) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(key + " must be object");
        return element.getAsJsonObject();
    }

    private static void known(JsonObject json, Set<String> keys, Consumer<String> warning) {
        for (String key : json.keySet()) {
            if (!keys.contains(key)) warning.accept("ignored unknown key '" + key + "'");
        }
    }
}
