package com.lulan.shincolle.shipdata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistries;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Strict known-field types, bounded values, and server-side reference validation. */
public final class ShipDefinitionValidator {
    private final Predicate<ResourceLocation> aiExists;
    private final Predicate<ResourceLocation> attackExists;
    private final Predicate<ResourceLocation> equipmentExists;

    public ShipDefinitionValidator() {
        this(ShipBehaviorRegistries::isSelectableAi, ShipBehaviorRegistries::isSelectableAttack,
                id -> EquipDataRegistry.server().byId().containsKey(id));
    }

    /** Explicit reference views also permit isolated preflight tests without mutating frozen registries. */
    ShipDefinitionValidator(Predicate<ResourceLocation> aiExists, Predicate<ResourceLocation> attackExists,
                            Predicate<ResourceLocation> equipmentExists) {
        this.aiExists = aiExists;
        this.attackExists = attackExists;
        this.equipmentExists = equipmentExists;
    }

    public ShipDefinition validate(ShipDefinitionId id, JsonElement element, Consumer<String> warning) {
        JsonObject json = object(element, "definition");
        known(json, ShipDefinitionFormat.FIELDS, "definition", warning);
        requireSchema(json);
        if (json.has("$schema")) {
            string(json.get("$schema"), "$schema");
        }
        String name = json.has("display_name") ? string(json.get("display_name"), "display_name") : id.toString();
        if (name.codePointCount(0, name.length()) > ShipDefinitionFormat.MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("display_name is too long");
        }
        ResourceLocation type = resource(json.get("ship_type"), "ship_type");
        if (!Reference.MOD_ID.equals(type.getNamespace()) || !ShipDefinitionFormat.SHIP_TYPES.contains(type.getPath())) {
            throw new IllegalArgumentException("unknown ship_type '" + type + "'");
        }
        ResourceLocation visual = resource(json.get("visual"), "visual");
        Map<String, Double> base = stats(object(json.get("base_stats"), "base_stats"), ShipDefinitionFormat.BASE_MAX, warning);
        Map<String, Double> growth = stats(optionalObject(json, "growth"), ShipDefinitionFormat.GROWTH_MAX, warning);

        JsonObject equip = optionalObject(json, "equipment");
        known(equip, Set.of("slots", "initial"), "equipment", warning);
        int slots = equip.has("slots") ? integer(equip.get("slots"), "equipment.slots", ShipDefinitionFormat.MAX_SLOTS) : 0;
        List<ResourceLocation> initial = ids(equip, "initial", slots, false);
        for (ResourceLocation item : initial) {
            if (!equipmentExists.test(item)) {
                throw new IllegalArgumentException("unknown equipment definition '" + item + "'");
            }
        }

        JsonObject behavior = optionalObject(json, "behavior");
        known(behavior, Set.of("ai", "attacks"), "behavior", warning);
        Optional<ResourceLocation> ai = behavior.has("ai") ? Optional.of(resource(behavior.get("ai"), "behavior.ai")) : Optional.empty();
        if (ai.isPresent() && (!ShipBehaviorRegistries.isSelectableNamespace(ai.get()) || !aiExists.test(ai.get()))) {
            throw new IllegalArgumentException("unknown or unselectable AI '" + ai.get() + "'");
        }
        List<ResourceLocation> attacks = ids(behavior, "attacks", ShipDefinitionFormat.MAX_ATTACKS, true);
        for (ResourceLocation attack : attacks) {
            if (!ShipBehaviorRegistries.isSelectableNamespace(attack) || !attackExists.test(attack)) {
                throw new IllegalArgumentException("unknown or unselectable attack '" + attack + "'");
            }
        }
        JsonObject dimensions = optionalObject(json, "dimensions");
        known(dimensions, Set.of("width", "height"), "dimensions", warning);
        float width = (float) number(dimensions, "width", ShipDefinitionFormat.DEFAULT_DIMENSIONS.width(),
                ShipDefinitionFormat.MAX_WIDTH, true);
        float height = (float) number(dimensions, "height", ShipDefinitionFormat.DEFAULT_DIMENSIONS.height(),
                ShipDefinitionFormat.MAX_HEIGHT, true);
        // Keep the public minimum explicit, not dependent on float rounding near zero.
        if (width <= 0 || height <= 0 || belowPositiveFloatMinimum(dimensions, "width")
                || belowPositiveFloatMinimum(dimensions, "height")) {
            throw new IllegalArgumentException("dimensions must be positive floats");
        }
        double mountOffset = number(json, "mount_offset", ShipDefinitionFormat.DEFAULT_MOUNT_OFFSET,
                ShipDefinitionFormat.MAX_HEIGHT, false);
        int equipType = json.has("equip_type") ? integer(json.get("equip_type"), "equip_type", 3) : 1;
        ResourceLocation profile = json.has("attack_profile")
                ? resource(json.get("attack_profile"), "attack_profile") : ShipDefinitionFormat.DEFAULT_ATTACK_PROFILE;
        if (!profile.equals(ShipDefinitionFormat.DEFAULT_ATTACK_PROFILE)) {
            throw new IllegalArgumentException("unknown attack_profile '" + profile + "'");
        }
        JsonObject modifiers = optionalObject(json, "hostile_modifiers");
        known(modifiers, ShipDefinitionFormat.BASE_MAX.keySet(), "hostile_modifiers", warning);
        Map<String, Double> hostile = new HashMap<>();
        for (String stat : ShipDefinitionFormat.BASE_MAX.keySet()) {
            hostile.put(stat, number(modifiers, stat, 1, 2.4, true));
            if (belowPositiveFloatMinimum(modifiers, stat)) {
                throw new IllegalArgumentException("hostile_modifiers." + stat + " must be at least Float.MIN_VALUE");
            }
        }
        return new ShipDefinition(id, ShipDefinitionFormat.SCHEMA, name, type, visual, base, growth,
                new ShipDefinition.Equipment(slots, initial), acquisition(json, "construction", warning),
                acquisition(json, "spawn", warning), new ShipDefinition.Behavior(ai, attacks),
                new ShipDefinition.Dimensions(width, height), mountOffset, equipType, profile, hostile);
    }

    private static boolean belowPositiveFloatMinimum(JsonObject json, String field) {
        return json.has(field) && json.get(field).getAsBigDecimal().compareTo(BigDecimal.valueOf((double) Float.MIN_VALUE)) < 0;
    }

    private static double number(JsonObject json, String field, double fallback, double max, boolean positive) {
        if (!json.has(field)) {
            return fallback;
        }
        JsonElement value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < 0 || (positive && number == 0) || number > max
                || value.getAsBigDecimal().signum() < (positive ? 1 : 0)
                || value.getAsBigDecimal().compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException(field + " must be finite and " + (positive ? "positive" : "nonnegative")
                    + ", at most " + max);
        }
        return number;
    }

    private static void requireSchema(JsonObject json) {
        JsonElement value = json.get("schema");
        if (value == null) {
            throw new IllegalArgumentException("schema is required (expected integer " + ShipDefinitionFormat.SCHEMA + ")");
        }
        int declared;
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new ArithmeticException("not a number");
            }
            declared = value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("schema must be an integer (expected "
                    + ShipDefinitionFormat.SCHEMA + ", got " + value + ")", exception);
        }
        if (declared != ShipDefinitionFormat.SCHEMA) {
            throw new IllegalArgumentException("schema " + declared + " is unsupported (this version reads schema "
                    + ShipDefinitionFormat.SCHEMA + ")");
        }
    }

    private static ShipDefinition.Acquisition acquisition(JsonObject json, String field, Consumer<String> warning) {
        JsonObject settings = optionalObject(json, field);
        known(settings, Set.of("enabled", "weight"), field, warning);
        boolean enabled = false;
        if (settings.has("enabled")) {
            JsonElement value = settings.get("enabled");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException(field + ".enabled must be boolean");
            }
            enabled = value.getAsBoolean();
        }
        int weight = settings.has("weight") ? integer(settings.get("weight"), field + ".weight", Integer.MAX_VALUE) : 0;
        return new ShipDefinition.Acquisition(enabled, weight);
    }

    private static Map<String, Double> stats(JsonObject json, Map<String, Double> limits, Consumer<String> warning) {
        known(json, limits.keySet(), "stats", warning);
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Double> limit : limits.entrySet()) {
            double value = 0;
            if (json.has(limit.getKey())) {
                JsonElement element = json.get(limit.getKey());
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException(limit.getKey() + " must be a number");
                }
                value = element.getAsDouble();
                if (!Double.isFinite(value) || value < 0 || value > limit.getValue()
                        || element.getAsBigDecimal().compareTo(BigDecimal.valueOf(limit.getValue())) > 0
                        || element.getAsBigDecimal().signum() < 0) {
                    throw new IllegalArgumentException(limit.getKey() + " must be finite and within 0.." + limit.getValue());
                }
            }
            result.put(limit.getKey(), value);
        }
        return Map.copyOf(result);
    }

    private static List<ResourceLocation> ids(JsonObject json, String field, int maximum, boolean unique) {
        if (!json.has(field)) {
            return List.of();
        }
        JsonElement element = json.get(field);
        if (!element.isJsonArray() || element.getAsJsonArray().size() > maximum) {
            throw new IllegalArgumentException(field + " must be an array of at most " + maximum + " IDs");
        }
        List<ResourceLocation> result = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (JsonElement value : element.getAsJsonArray()) {
            ResourceLocation id = resource(value, field);
            if (unique && !seen.add(id)) {
                throw new IllegalArgumentException("duplicate " + field + " ID '" + id + "'");
            }
            result.add(id);
        }
        return List.copyOf(result);
    }

    private static JsonObject optionalObject(JsonObject json, String field) {
        return json.has(field) ? object(json.get(field), field) : new JsonObject();
    }

    private static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return element.getAsString();
    }

    private static ResourceLocation resource(JsonElement value, String field) {
        return ShipDefinitionFormat.requireId(string(value, field));
    }

    private static int integer(JsonElement element, String field, int max) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            int number = element.getAsBigDecimal().intValueExact();
            if (number < 0 || number > max) {
                throw new IllegalArgumentException(field + " must be within 0.." + max);
            }
            return number;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a bounded integer", exception);
        }
    }

    private static void known(JsonObject json, Set<String> fields, String context, Consumer<String> warning) {
        for (String key : json.keySet()) {
            if (!fields.contains(key)) {
                warning.accept("ignored unknown " + context + " key '" + key + "'");
            }
        }
    }
}
