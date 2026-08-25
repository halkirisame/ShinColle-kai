package com.lulan.shincolle.equipdata;

import com.google.gson.JsonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.network.EquipmentSyncV2Codec;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Focused validation coverage for the dynamic equipment JSON stats object. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipDataLoaderGameTests {

    private static final ResourceLocation CUSTOM_ATTRIBUTE = id("loader_test", "sonar_precision");
    private static final ResourceLocation GLOWING = id("minecraft", "glowing");

    private EquipDataLoaderGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void parseStatsAcceptsAliasesCanonicalIdsAndDetachedCustomIds(GameTestHelper helper) {
        JsonObject stats = new JsonObject();
        stats.addProperty("atk_l", 5.5F);
        stats.addProperty("shincolle_kai:hp", 12F);
        stats.addProperty(CUSTOM_ATTRIBUTE.toString(), 0.25F);

        ShipAttributeValues parsed = EquipDataLoader.parseStats(stats, layoutWithCustom());
        assertFloatEquals(5.5F, parsed.get(CoreShipAttributes.ATK_L), "short alias value");
        assertFloatEquals(12F, parsed.get(CoreShipAttributes.HP), "canonical core value");
        assertFloatEquals(0.25F, parsed.get(CUSTOM_ATTRIBUTE), "detached custom value");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void parseStatsRejectsNormalizedDuplicateAndUnknownOrInvalidIds(GameTestHelper helper) {
        JsonObject normalizedDuplicate = new JsonObject();
        normalizedDuplicate.addProperty("atk_l", 1F);
        normalizedDuplicate.addProperty("shincolle_kai:atk_l", 2F);
        assertRejected(normalizedDuplicate, ShipAttributeLayout.current(), "normalized duplicate");

        JsonObject unknown = new JsonObject();
        unknown.addProperty("loader_test:unregistered", 1F);
        assertRejected(unknown, ShipAttributeLayout.current(), "unknown ID");

        JsonObject invalid = new JsonObject();
        invalid.addProperty("bad namespace:attribute", 1F);
        assertRejected(invalid, ShipAttributeLayout.current(), "invalid ResourceLocation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void parseStatsRejectsNonNumbersAndNonFiniteValues(GameTestHelper helper) {
        JsonObject text = new JsonObject();
        text.addProperty("atk_l", "five");
        assertRejected(text, ShipAttributeLayout.current(), "string stat value");

        JsonObject nullValue = new JsonObject();
        nullValue.add("atk_l", JsonNull.INSTANCE);
        assertRejected(nullValue, ShipAttributeLayout.current(), "null stat value");

        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            JsonObject nonFinite = new JsonObject();
            nonFinite.addProperty("atk_l", value);
            assertRejected(nonFinite, ShipAttributeLayout.current(), "non-finite stat value " + value);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void parseStatsRejectsMoreThanMaximumEntriesAndAcceptsEmptyObject(GameTestHelper helper) {
        JsonObject overflow = new JsonObject();
        for (int index = 0; index <= EquipDataLoader.MAX_STATS_PER_DEFINITION; index++) {
            overflow.addProperty("loader_test:attribute_" + index, 1F);
        }
        assertRejected(overflow, ShipAttributeLayout.current(), "stats count overflow");

        ShipAttributeValues empty = EquipDataLoader.parseStats(new JsonObject(), ShipAttributeLayout.current());
        for (float value : empty.asMap().values()) {
            assertFloatEquals(0F, value, "empty stats value");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void definitionAllowsMissingStatsButRejectsNullStats(GameTestHelper helper) {
        JsonObject missing = minimalDefinition();
        EquipDefinition parsed = EquipDataLoader.parse(id("loader_test", "missing_stats"), missing);
        for (float value : parsed.stats().asMap().values()) {
            assertFloatEquals(0F, value, "missing stats value");
        }

        JsonObject nullStats = minimalDefinition();
        nullStats.add("stats", JsonNull.INSTANCE);
        try {
            EquipDataLoader.parse(id("loader_test", "null_stats"), nullStats);
            throw new AssertionError("Expected a null stats field to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void definitionValidatesDeclarativeAttackEffects(GameTestHelper helper) {
        JsonObject valid = minimalDefinition();
        valid.add("attack_effects", attackEffects(effect("minecraft:glowing", 1, 80, 45)));
        ShipAttackEffect parsed = EquipDataLoader.parse(id("loader_test", "attack_effect"), valid)
                .attackEffects().get(GLOWING);
        if (parsed == null || parsed.amplifier() != 1 || parsed.durationTicks() != 80
                || parsed.chancePercent() != 45) {
            throw new AssertionError("Valid attack effect was not parsed exactly");
        }

        JsonObject duplicate = minimalDefinition();
        duplicate.add("attack_effects", attackEffects(
                effect("minecraft:glowing", 0, 20, 100),
                effect("minecraft:glowing", 1, 40, 50)));
        assertDefinitionRejected(duplicate, "duplicate attack effect");

        JsonObject unknown = minimalDefinition();
        unknown.add("attack_effects", attackEffects(effect("loader_test:not_registered", 0, 20, 100)));
        assertDefinitionRejected(unknown, "unregistered MobEffect");

        JsonObject invalidChance = minimalDefinition();
        invalidChance.add("attack_effects", attackEffects(effect("minecraft:glowing", 0, 20, 101)));
        assertDefinitionRejected(invalidChance, "attack effect chance outside range");

        JsonObject unknownField = minimalDefinition();
        JsonObject unknownFieldEffect = effect("minecraft:glowing", 0, 20, 100);
        unknownFieldEffect.addProperty("duration_seconds", 1);
        unknownField.add("attack_effects", attackEffects(unknownFieldEffect));
        assertDefinitionRejected(unknownField, "unknown attack effect field");

        JsonObject fractional = minimalDefinition();
        JsonObject fractionalEffect = effect("minecraft:glowing", 0, 20, 100);
        fractionalEffect.addProperty("amplifier", 0.5D);
        fractional.add("attack_effects", attackEffects(fractionalEffect));
        assertDefinitionRejected(fractional, "fractional attack effect integer");

        JsonObject overflow = minimalDefinition();
        JsonArray tooMany = new JsonArray();
        for (int index = 0; index <= EquipmentJsonFormat.MAX_ATTACK_EFFECTS; index++) {
            tooMany.add(effect("minecraft:glowing", 0, 20, 100));
        }
        overflow.add("attack_effects", tooMany);
        assertDefinitionRejected(overflow, "attack effect count overflow");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void aggregateOverflowRetainsThePublishedServerSnapshot(GameTestHelper helper) {
        EquipDataSnapshot original = EquipDataLoader.currentServerSnapshot();
        Map<ResourceLocation, EquipDefinition> definitions = new HashMap<>();
        for (int index = 0; index <= EquipmentSyncV2Codec.MAX_DEFINITIONS; index++) {
            ResourceLocation id = id("loader_overflow", "definition_" + index);
            definitions.put(id, new EquipDefinition(id, id("minecraft", "stick"), index, 0, null,
                    ShipAttributeValues.zero(ShipAttributeLayout.current()), java.util.List.of(), 0,
                    "grudge", 0, 0, 0));
        }
        EquipDataSnapshot overflow = new EquipDataSnapshot(definitions, Map.of(), Map.of());
        if (EquipDataLoader.publishCandidate(overflow)) {
            throw new AssertionError("Aggregate definition overflow was published");
        }
        if (EquipDataLoader.currentServerSnapshot() != original) {
            throw new AssertionError("Rejected aggregate overflow replaced the previous server snapshot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void aggregateAttributeAndPayloadOverflowRetainTheServerSnapshot(GameTestHelper helper) {
        EquipDataSnapshot original = EquipDataLoader.currentServerSnapshot();

        EquipDataSnapshot distinctOverflow = new EquipDataSnapshot(
                definitionsById(definitionsWithDistinctAttributes(
                        EquipmentSyncV2Codec.MAX_DISTINCT_ATTRIBUTE_IDS + 1)), Map.of(), Map.of());
        assertRejectedGenerationRetains(original, distinctOverflow, "distinct attribute overflow");

        String longCompatible = "x".repeat(128);
        List<String> compatible = Collections.nCopies(32, longCompatible);
        List<EquipDefinition> oversizedDefinitions = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            ResourceLocation id = id("loader_payload", "definition_" + index);
            oversizedDefinitions.add(new EquipDefinition(id, id("minecraft", "stick"), index, 0, null,
                    ShipAttributeValues.zero(ShipAttributeLayout.current()), compatible, 0,
                    longCompatible, 0, 0, 0));
        }
        EquipDataSnapshot payloadOverflow = new EquipDataSnapshot(
                definitionsById(oversizedDefinitions), Map.of(), Map.of());
        assertRejectedGenerationRetains(original, payloadOverflow, "payload overflow");
        helper.succeed();
    }

    private static ShipAttributeLayout layoutWithCustom() {
        Map<ResourceLocation, ShipAttributeType> entries = canonicalEntries();
        entries.put(CUSTOM_ATTRIBUTE, ShipAttributeType.builder().build());
        return ShipAttributeLayout.detached(entries);
    }

    private static JsonObject minimalDefinition() {
        JsonObject json = new JsonObject();
        json.addProperty("item", "minecraft:stick");
        json.addProperty("variant", 0);
        json.addProperty("equip_type", "cannon_si");
        return json;
    }

    private static JsonObject effect(String id, int amplifier, int duration, int chance) {
        JsonObject json = new JsonObject();
        json.addProperty("effect", id);
        json.addProperty("amplifier", amplifier);
        json.addProperty("duration", duration);
        json.addProperty("chance", chance);
        return json;
    }

    private static JsonArray attackEffects(JsonObject... effects) {
        JsonArray values = new JsonArray();
        for (JsonObject effect : effects) {
            values.add(effect);
        }
        return values;
    }

    private static Map<ResourceLocation, ShipAttributeType> canonicalEntries() {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        Map<ResourceLocation, ShipAttributeType> entries = new HashMap<>();
        for (ResourceLocation id : canonical.ids()) {
            entries.put(id, canonical.type(id));
        }
        return entries;
    }

    private static List<EquipDefinition> definitionsWithDistinctAttributes(int count) {
        List<EquipDefinition> definitions = new ArrayList<>();
        int written = 0;
        int definitionIndex = 0;
        while (written < count) {
            Map<ResourceLocation, ShipAttributeType> types = canonicalEntries();
            Map<ResourceLocation, Float> values = new HashMap<>();
            int pairCount = Math.min(EquipDataLoader.MAX_STATS_PER_DEFINITION, count - written);
            for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
                ResourceLocation attribute = id("loader_aggregate", "attribute_" + written++);
                types.put(attribute, ShipAttributeType.builder().build());
                values.put(attribute, 1F);
            }
            ShipAttributeLayout layout = ShipAttributeLayout.detached(types);
            ShipAttributeValues.Builder stats = ShipAttributeValues.builder(layout);
            values.forEach(stats::set);
            ResourceLocation definitionId = id("loader_aggregate", "definition_" + definitionIndex);
            definitions.add(new EquipDefinition(definitionId, id("minecraft", "stick"), definitionIndex,
                    0, null, stats.build(), List.of(), 0, "grudge", 0, 0, 0));
            definitionIndex++;
        }
        return definitions;
    }

    private static Map<ResourceLocation, EquipDefinition> definitionsById(List<EquipDefinition> definitions) {
        Map<ResourceLocation, EquipDefinition> byId = new HashMap<>();
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        return byId;
    }

    private static void assertRejectedGenerationRetains(EquipDataSnapshot original,
                                                        EquipDataSnapshot candidate, String name) {
        if (EquipDataLoader.publishCandidate(candidate)) {
            throw new AssertionError("Expected loader generation rejection for " + name);
        }
        if (EquipDataLoader.currentServerSnapshot() != original) {
            throw new AssertionError("Rejected " + name + " replaced the previous server snapshot");
        }
    }

    private static void assertRejected(JsonObject stats, ShipAttributeLayout layout, String name) {
        try {
            EquipDataLoader.parseStats(stats, layout);
            throw new AssertionError("Expected parseStats to reject " + name);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void assertDefinitionRejected(JsonObject definition, String name) {
        try {
            EquipDataLoader.parse(id("loader_test", "invalid_attack_effect"), definition);
            throw new AssertionError("Expected definition parser to reject " + name);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void assertFloatEquals(float expected, float actual, String name) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
