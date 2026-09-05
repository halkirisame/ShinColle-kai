package com.lulan.shincolle.equipdata;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.network.EquipmentSyncV2Codec;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/<domain>/equipment/*.json} into {@link EquipDefinition}s,
 * keyed by their file-path {@link ResourceLocation}. Any datapack can add, override, or (with
 * {@code "replace"}-style datapack layering) shadow entries here - the point
 * of moving off the old hardcoded {@code Values.EquipAttrsMain}/{@code
 * EquipAttrsMisc} maps.
 *
 * <p>Read {@link EquipDataRegistry} to look up loaded definitions; this class
 * only owns the reload/parse step.
 */
public class EquipDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "equipment";
    static final int MAX_STATS_PER_DEFINITION = EquipmentJsonFormat.MAX_STATS;

    private static volatile EquipDataSnapshot serverSnapshot = EquipDataSnapshot.EMPTY;

    public EquipDataLoader() {
        super(GSON, DIRECTORY);
    }

    static EquipDataSnapshot currentServerSnapshot() {
        return serverSnapshot;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        EquipDataSnapshot candidate = parseDefinitions(object);
        if (!publishCandidate(candidate)) {
            return;
        }
        ShinColle.LOGGER.info("Loaded {} ship equipment definitions", serverSnapshot.byId().size());
    }

    static EquipDataSnapshot parseDefinitions(Map<ResourceLocation, JsonElement> object) {
        Map<ResourceLocation, EquipDefinition> definitions = new HashMap<>();
        Map<ResourceLocation, Map<Integer, EquipDefinition>> itemVariants = new HashMap<>();
        Map<Integer, EquipDefinition> legacyDefinitions = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            try {
                EquipDefinition def = parse(entry.getKey(), entry.getValue().getAsJsonObject());
                definitions.put(def.id(), def);

                Map<Integer, EquipDefinition> variants = itemVariants.computeIfAbsent(
                        def.item(), unused -> new HashMap<>());
                EquipDefinition previousVariant = variants.put(def.variant(), def);
                if (previousVariant != null) {
                    ShinColle.LOGGER.warn("Ship equipment item/variant collision: {} and {} both use item {} "
                                    + "variant {}; last one loaded wins", previousVariant.id(), def.id(),
                            def.item(), def.variant());
                }

                if (def.legacyEquipId() != null) {
                    EquipDefinition previousLegacy = legacyDefinitions.put(def.legacyEquipId(), def);
                    if (previousLegacy != null) {
                        ShinColle.LOGGER.warn("Ship equipment legacy ID collision: {} and {} both use equip_id {}; "
                                        + "last one loaded wins", previousLegacy.id(), def.id(),
                                def.legacyEquipId());
                    }
                }
            } catch (RuntimeException e) {
                ShinColle.LOGGER.error("Failed to parse ship equipment {}: {}", entry.getKey(), e.toString());
            }
        }

        return new EquipDataSnapshot(definitions, itemVariants, legacyDefinitions);
    }

    static boolean publishCandidate(EquipDataSnapshot candidate) {
        try {
            EquipmentSyncV2Codec.validateSnapshotForServer(candidate);
        } catch (IllegalArgumentException exception) {
            ShinColle.LOGGER.error("Rejected ship equipment reload generation; keeping previous snapshot: {}",
                    exception.toString());
            return false;
        }
        serverSnapshot = candidate;
        return true;
    }

    static EquipDefinition parse(ResourceLocation id, JsonObject json) {
        validateKnownFields(json, EquipmentJsonFormat.TOP_LEVEL_FIELDS, "equipment definition");
        for (String field : EquipmentJsonFormat.REQUIRED_FIELDS) {
            if (!json.has(field)) {
                throw new IllegalArgumentException("missing required equipment definition field '" + field + "'");
            }
        }
        if (json.has("$schema")) {
            requireString(json, "$schema");
        }

        ResourceLocation item = ResourceLocation.tryParse(requireString(json, "item"));
        if (item == null) {
            throw new IllegalArgumentException("invalid item ResourceLocation");
        }
        int variant = requireNonNegative(requireInt(json, "variant"), "variant");
        String equipTypeName = requireString(json, "equip_type");
        Integer equipType = EquipmentJsonFormat.EQUIP_TYPES.get(equipTypeName);
        if (equipType == null) {
            throw new IllegalArgumentException("unknown equip_type '" + equipTypeName + "'");
        }
        Integer legacyEquipId = json.has("equip_id")
                ? requireNonNegative(requireInt(json, "equip_id"), "equip_id") : null;

        ShipAttributeValues stats = ShipAttributeValues.zero(ShipAttributeLayout.current());
        if (json.has("stats")) {
            JsonElement statsElement = json.get("stats");
            if (statsElement == null || !statsElement.isJsonObject()) {
                throw new IllegalArgumentException("stats must be a JSON object");
            }
            stats = parseStats(statsElement.getAsJsonObject(), ShipAttributeLayout.current());
        }

        Map<ResourceLocation, ShipAttackEffect> attackEffects = parseAttackEffects(json);

        List<String> compatible = new ArrayList<>();
        if (json.has("compatible")) {
            JsonElement element = json.get("compatible");
            if (!element.isJsonArray()) {
                throw new IllegalArgumentException("compatible must be a JSON array");
            }
            if (element.getAsJsonArray().size() > EquipmentJsonFormat.MAX_COMPATIBLE) {
                throw new IllegalArgumentException("compatible count exceeds "
                        + EquipmentJsonFormat.MAX_COMPATIBLE);
            }
            Set<String> seen = new HashSet<>();
            element.getAsJsonArray().forEach(value -> {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("compatible values must be strings");
                }
                String name = value.getAsString();
                if (!EquipmentJsonFormat.COMPATIBILITY.contains(name)) {
                    throw new IllegalArgumentException("unknown compatible value '" + name + "'");
                }
                if (!seen.add(name)) {
                    throw new IllegalArgumentException("duplicate compatible value '" + name + "'");
                }
                compatible.add(name);
            });
        }

        String enchantTypeName = json.has("enchant_type") ? requireString(json, "enchant_type") : "none";
        if (!EquipmentJsonFormat.ENCHANT_TYPES.contains(enchantTypeName)) {
            throw new IllegalArgumentException("unknown enchant_type '" + enchantTypeName + "'");
        }
        int enchantType = enchantTypeFromString(enchantTypeName);

        JsonObject develop = optionalObject(json, "develop");
        validateKnownFields(develop, EquipmentJsonFormat.DEVELOP_FIELDS, "develop");
        String material = develop.has("material") ? requireString(develop, "material") : "grudge";
        if (!EquipmentJsonFormat.DEVELOP_MATERIALS.contains(material)) {
            throw new IllegalArgumentException("unknown develop material '" + material + "'");
        }
        int amount = develop.has("amount")
                ? requireNonNegative(requireInt(develop, "amount"), "develop.amount") : 0;
        int rareMean = develop.has("rare_mean")
                ? requireNonNegative(requireInt(develop, "rare_mean"), "develop.rare_mean") : 0;

        int rollType = json.has("roll_type")
                ? requireNonNegative(requireInt(json, "roll_type"), "roll_type") : equipType;
        EquipAvailability availability = json.has("availability")
                ? EquipAvailability.fromJsonName(requireString(json, "availability"))
                : EquipAvailability.ANY;

        return new EquipDefinition(id, item, variant, equipType, legacyEquipId, stats, attackEffects, compatible,
                enchantType, material, amount, rareMean, rollType, availability);
    }

    private static Map<ResourceLocation, ShipAttackEffect> parseAttackEffects(JsonObject json) {
        if (!json.has("attack_effects")) {
            return Map.of();
        }
        JsonElement element = json.get("attack_effects");
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("attack_effects must be a JSON array");
        }
        if (element.getAsJsonArray().size() > EquipmentJsonFormat.MAX_ATTACK_EFFECTS) {
            throw new IllegalArgumentException("attack_effects count exceeds "
                    + EquipmentJsonFormat.MAX_ATTACK_EFFECTS);
        }
        Map<ResourceLocation, ShipAttackEffect> effects = new java.util.LinkedHashMap<>();
        element.getAsJsonArray().forEach(value -> {
            if (value == null || !value.isJsonObject()) {
                throw new IllegalArgumentException("attack_effects values must be objects");
            }
            JsonObject effectJson = value.getAsJsonObject();
            validateKnownFields(effectJson, EquipmentJsonFormat.ATTACK_EFFECT_FIELDS, "attack effect");
            for (String field : EquipmentJsonFormat.ATTACK_EFFECT_FIELDS) {
                if (!effectJson.has(field)) {
                    throw new IllegalArgumentException("missing required attack effect field '" + field + "'");
                }
            }
            ResourceLocation effectId = ResourceLocation.tryParse(requireString(effectJson, "effect"));
            if (effectId == null || !ForgeRegistries.MOB_EFFECTS.containsKey(effectId)) {
                throw new IllegalArgumentException("unknown MobEffect '" + effectJson.get("effect") + "'");
            }
            ShipAttackEffect effect = new ShipAttackEffect(effectId,
                    requireInt(effectJson, "amplifier"), requireInt(effectJson, "duration"),
                    requireInt(effectJson, "chance"));
            if (effects.put(effectId, effect) != null) {
                throw new IllegalArgumentException("duplicate attack effect '" + effectId + "'");
            }
        });
        return Map.copyOf(effects);
    }

    /** Parses one stats object against an explicit layout so addon IDs can be tested without registry mutation. */
    static ShipAttributeValues parseStats(JsonObject stats, ShipAttributeLayout layout) {
        if (stats.size() > EquipmentJsonFormat.MAX_STATS) {
            throw new IllegalArgumentException("stats count exceeds " + MAX_STATS_PER_DEFINITION);
        }
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(layout);
        Set<ResourceLocation> seen = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : stats.entrySet()) {
            String key = entry.getKey();
            ResourceLocation id = parseStatId(key);
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate stat '" + id + "' from key '" + key + "'");
            }
            if (layout.indexOf(id) < 0) {
                throw new IllegalArgumentException("unknown stat key '" + key + "'");
            }
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("stat '" + key + "' must be a number");
            }
            float value = element.getAsFloat();
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("stat '" + key + "' must be finite");
            }
            result.set(id, value);
        }
        return result.build();
    }

    private static ResourceLocation parseStatId(String key) {
        if (key == null || key.isEmpty() || key.length() > EquipmentJsonFormat.MAX_STAT_ID_LENGTH) {
            throw new IllegalArgumentException("invalid stat key length");
        }
        String normalized = key.indexOf(':') >= 0 ? key : Reference.MOD_ID + ':' + key;
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null) {
            throw new IllegalArgumentException("invalid stat ResourceLocation '" + key + "'");
        }
        return id;
    }

    private static int enchantTypeFromString(String value) {
        return switch (value) {
            case "weapon" -> 1;
            case "armor" -> 2;
            case "misc" -> 3;
            default -> 0;
        };
    }

    private static void validateKnownFields(JsonObject json, Set<String> known, String context) {
        for (String field : json.keySet()) {
            if (!known.contains(field)) {
                throw new IllegalArgumentException("unknown " + context + " field '" + field + "'");
            }
        }
    }

    private static JsonObject optionalObject(JsonObject json, String field) {
        if (!json.has(field)) {
            return new JsonObject();
        }
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static String requireString(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        String value = element.getAsString();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }

    private static int requireInt(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            BigDecimal value = element.getAsBigDecimal();
            return value.intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a 32-bit integer", exception);
        }
    }

    private static int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    /** Inverse of {@link #enchantTypeFromString}, used by the Values.java -> JSON conversion tool. */
    static String enchantTypeToString(int value) {
        return switch (value) {
            case 1 -> "weapon";
            case 2 -> "armor";
            case 3 -> "misc";
            default -> "none";
        };
    }

    /** Maps the old numeric DEVELOP_MAT to the JSON material key. Used by the conversion tool. */
    static String developMaterialToString(int value) {
        return switch (value) {
            case 1 -> "abyss_metal";
            case 2 -> "ammo";
            case 3 -> "abyss_metal_1";
            default -> "grudge";
        };
    }

    /** Inverse of {@link #developMaterialToString}. */
    private static int developMaterialFromString(String value) {
        return switch (value) {
            case "abyss_metal" -> 1;
            case "ammo" -> 2;
            case "abyss_metal_1" -> 3;
            default -> 0;
        };
    }
}
