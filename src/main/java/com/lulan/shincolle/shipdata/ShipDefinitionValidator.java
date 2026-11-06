package com.lulan.shincolle.shipdata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistries;
import com.lulan.shincolle.ai.registry.ShipTraitRegistries;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Strict known-field types, bounded values, and server-side reference validation. */
public final class ShipDefinitionValidator {
    private final Predicate<ResourceLocation> aiExists;
    private final Predicate<ResourceLocation> attackExists;
    private final Predicate<ResourceLocation> equipmentExists;
    private final Predicate<ResourceLocation> traitExists;

    public ShipDefinitionValidator() {
        this(ShipBehaviorRegistries::isSelectableAi, ShipBehaviorRegistries::isSelectableAttack,
                id -> EquipDataRegistry.server().byId().containsKey(id));
    }

    /** Explicit reference views also permit isolated preflight tests without mutating frozen registries. */
    ShipDefinitionValidator(Predicate<ResourceLocation> aiExists, Predicate<ResourceLocation> attackExists,
                            Predicate<ResourceLocation> equipmentExists) {
        this(aiExists, attackExists, equipmentExists, ShipTraitRegistries::isSelectableTrait);
    }

    ShipDefinitionValidator(Predicate<ResourceLocation> aiExists, Predicate<ResourceLocation> attackExists,
                            Predicate<ResourceLocation> equipmentExists, Predicate<ResourceLocation> traitExists) {
        this.aiExists = aiExists;
        this.attackExists = attackExists;
        this.equipmentExists = equipmentExists;
        this.traitExists = traitExists;
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
        if (behavior.has("attacks") && attacks.isEmpty()) {
            throw new IllegalArgumentException("behavior.attacks must not be empty; omit it for the default attacks");
        }
        for (ResourceLocation attack : attacks) {
            if (!ShipBehaviorRegistries.isSelectableNamespace(attack) || !attackExists.test(attack)) {
                if (ShipBehaviorRegistries.isRegisteredAttack(attack)) {
                    throw new IllegalArgumentException("attack '" + attack + "' is registered but cannot be selected in 1.0");
                }
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
        Map<String, Double> hostile = hostileModifiers(json, warning);
        return new ShipDefinition(id, ShipDefinitionFormat.SCHEMA, name, type, visual, base, growth,
                new ShipDefinition.Equipment(slots, initial), construction(json, warning),
                acquisition(json, "spawn", warning), new ShipDefinition.Behavior(ai, attacks),
                new ShipDefinition.Dimensions(width, height), mountOffset, equipType, profile, hostile,
                hostileVariants(json, warning));
    }

    private static Map<String, Double> hostileModifiers(JsonObject json, Consumer<String> warning) {
        JsonObject modifiers = optionalObject(json, "hostile_modifiers");
        known(modifiers, ShipDefinitionFormat.BASE_MAX.keySet(), "hostile_modifiers", warning);
        Map<String, Double> hostile = new HashMap<>();
        for (String stat : ShipDefinitionFormat.BASE_MAX.keySet()) {
            hostile.put(stat, number(modifiers, stat, 1, 2.4, true));
            if (belowPositiveFloatMinimum(modifiers, stat)) {
                throw new IllegalArgumentException("hostile_modifiers." + stat + " must be at least Float.MIN_VALUE");
            }
        }
        return Map.copyOf(hostile);
    }

    private HostileVariants hostileVariants(JsonObject json, Consumer<String> warning) {
        if (!json.has("hostile_variants")) return HostileVariants.NONE;
        JsonObject settings = optionalObject(json, "hostile_variants");
        known(settings, Set.of("normal_weight", "variants"), "hostile_variants", warning);
        int normalWeight = settings.has("normal_weight")
                ? integer(settings.get("normal_weight"), "normal_weight", ShipDefinitionFormat.MAX_VARIANT_WEIGHT) : 100;
        List<HostileVariants.Variant> variants = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (settings.has("variants")) {
            JsonElement array = settings.get("variants");
            if (!array.isJsonArray() || array.getAsJsonArray().size() > ShipDefinitionFormat.MAX_HOSTILE_VARIANTS) {
                throw new IllegalArgumentException("variants must be an array of at most " + ShipDefinitionFormat.MAX_HOSTILE_VARIANTS);
            }
            for (JsonElement element : array.getAsJsonArray()) {
                JsonObject variant = object(element, "variant");
                known(variant, Set.of("id", "name", "rarity", "weight", "traits", "hostile_modifiers", "visual_variant", "red_aura"),
                        "variant", warning);
                String variantId = string(variant.get("id"), "variant.id");
                if (!variantId.matches("[a-z0-9_]{1,32}") || !seen.add(variantId)) {
                    throw new IllegalArgumentException("invalid or duplicate variant ID '" + variantId + "'");
                }
                Optional<String> name = Optional.empty();
                if (variant.has("name")) {
                    String key = string(variant.get("name"), "variant.name");
                    int length = key.codePointCount(0, key.length());
                    if (length < 1 || length > ShipDefinitionFormat.MAX_VARIANT_NAME_LENGTH) {
                        throw new IllegalArgumentException("variant.name must contain 1..128 characters");
                    }
                    name = Optional.of(key);
                }
                HostileVariants.Rarity rarity = HostileVariants.Rarity.RARE;
                if (variant.has("rarity")) {
                    String value = string(variant.get("rarity"), "variant.rarity");
                    rarity = java.util.Arrays.stream(HostileVariants.Rarity.values())
                            .filter(candidate -> candidate.name().toLowerCase(Locale.ROOT).equals(value)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("unknown variant.rarity '" + value + "'"));
                }
                int weight = integer(variant.get("weight"), "variant.weight", ShipDefinitionFormat.MAX_VARIANT_WEIGHT);
                List<ResourceLocation> traits = ids(variant, "traits", ShipDefinitionFormat.MAX_VARIANT_TRAITS, true);
                for (ResourceLocation trait : traits) {
                    if (!ShipBehaviorRegistries.isSelectableNamespace(trait) || !traitExists.test(trait)) {
                        throw new IllegalArgumentException("unknown or unselectable trait '" + trait + "'");
                    }
                }
                Optional<ResourceLocation> visual = Optional.empty();
                if (variant.has("visual_variant")) {
                    String texture = string(variant.get("visual_variant"), "variant.visual_variant");
                    if (texture.length() > ShipDefinitionFormat.MAX_VARIANT_TEXTURE_LENGTH
                            || !texture.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                        throw new IllegalArgumentException("variant.visual_variant must be an explicit texture ID of at most 128 characters");
                    }
                    visual = Optional.of(new ResourceLocation(texture));
                }
                boolean redAura = true;
                if (variant.has("red_aura")) {
                    JsonElement value = variant.get("red_aura");
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        throw new IllegalArgumentException("variant.red_aura must be boolean");
                    }
                    redAura = value.getAsBoolean();
                }
                variants.add(new HostileVariants.Variant(variantId, name, rarity, weight, traits,
                        hostileModifiers(variant, warning), visual, redAura));
            }
        }
        return new HostileVariants(normalWeight, variants);
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
        boolean enabled = enabled(settings, field);
        int weight = settings.has("weight") ? integer(settings.get("weight"), field + ".weight", Integer.MAX_VALUE) : 0;
        return new ShipDefinition.Acquisition(enabled, weight);
    }

    private static ShipDefinition.Construction construction(JsonObject json, Consumer<String> warning) {
        JsonObject settings = optionalObject(json, "construction");
        known(settings, Set.of("enabled", "weight", "mode", "tier", "material_mean", "material_bias"), "construction", warning);
        boolean enabled = enabled(settings, "construction");
        ShipDefinition.ConstructionMode mode = settings.has("mode")
                ? choice(settings, "mode", ShipDefinition.ConstructionMode.class) : ShipDefinition.ConstructionMode.MATERIAL;
        Optional<ShipDefinition.ConstructionTier> tier = settings.has("tier")
                ? Optional.of(choice(settings, "tier", ShipDefinition.ConstructionTier.class)) : Optional.empty();
        OptionalInt mean = settings.has("material_mean")
                ? OptionalInt.of(integer(settings.get("material_mean"), "construction.material_mean", Integer.MAX_VALUE))
                : OptionalInt.empty();
        Optional<ShipDefinition.MaterialBias> bias = settings.has("material_bias")
                ? Optional.of(choice(settings, "material_bias", ShipDefinition.MaterialBias.class)) : Optional.empty();
        if (enabled && mode == ShipDefinition.ConstructionMode.MATERIAL && (tier.isEmpty() || mean.isEmpty())) {
            throw new IllegalArgumentException("enabled material construction requires tier and material_mean");
        }
        int fallback = mode == ShipDefinition.ConstructionMode.MATERIAL ? 100 : 0;
        int weight = settings.has("weight") ? integer(settings.get("weight"), "construction.weight", Integer.MAX_VALUE) : fallback;
        return new ShipDefinition.Construction(enabled, weight, mode, tier, mean, bias);
    }

    private static <E extends Enum<E>> E choice(JsonObject settings, String key, Class<E> type) {
        String value = string(settings.get(key), "construction." + key);
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(value)) return candidate;
        }
        throw new IllegalArgumentException("unknown construction." + key + " '" + value + "'");
    }

    private static boolean enabled(JsonObject settings, String field) {
        boolean enabled = false;
        if (settings.has("enabled")) {
            JsonElement value = settings.get("enabled");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException(field + ".enabled must be boolean");
            }
            enabled = value.getAsBoolean();
        }
        return enabled;
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
