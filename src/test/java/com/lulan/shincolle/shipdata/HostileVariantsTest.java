package com.lulan.shincolle.shipdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostileVariantsTest {
    private static final ShipDefinitionId ID = ShipDefinitionId.parse("addon:variants");
    private static final Set<String> TRAITS = Set.of("sturdy", "heavy_hitter", "armored", "quick_loader", "swift", "keen_eye");
    private static final ShipDefinitionValidator VALIDATOR = new ShipDefinitionValidator(
            id -> false, id -> false, id -> false, id -> TRAITS.contains(id.getPath()));
    private static JsonSchema schema;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void bootstrapAndLoadSchema() throws ReflectiveOperationException, IOException {
        ShipDefinitionTest.bootstrapMinecraft();
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                Files.readString(Path.of("docs/schemas/shincolle_kai-ship_definition.schema.json")));
    }

    private static JsonObject definition(String settings) {
        JsonObject result = ShipDefinitionTest.minimum();
        result.add("hostile_variants", JsonParser.parseString(settings));
        return result;
    }

    private static ShipDefinition validate(JsonObject json) {
        return VALIDATOR.validate(ID, json, ignored -> { });
    }

    private static String variants(String variant) {
        return "{\"variants\":[" + variant + "]}";
    }

    @Test
    void omissionAndEmptyObjectPreserveExistingDefaults() throws IOException {
        ShipDefinition omitted = validate(ShipDefinitionTest.minimum());
        assertEquals(HostileVariants.NONE, omitted.hostileVariants());
        assertEquals(omitted, validate(definition("{}")));
        assertEquals(omitted, validate(definition("{\"variants\":[]}")));
        assertNull(new ShipDefinitionSnapshot(1, Map.of(ID, omitted)).getVariant(ID, "missing"));
        assertTrue(schema.validate(MAPPER.readTree(definition("{}").toString())).isEmpty());
    }

    @Test
    void fullMetadataDefaultsAndNestedCollectionsAreImmutable() throws IOException {
        JsonObject json = definition("""
                {"normal_weight":90,"variants":[{"id":"flagship","name":"variant.addon.flagship","rarity":"legendary",
                "weight":10,"traits":["shincolle_kai:sturdy","shincolle_kai:swift"],"hostile_modifiers":{"hp":1.5},
                "visual_variant":"addon:textures/entity/flagship.png","red_aura":false},{"id":"normal","weight":0}]}
                """);
        assertTrue(schema.validate(MAPPER.readTree(json.toString())).isEmpty());
        ShipDefinition result = validate(json);
        HostileVariants metadata = result.hostileVariants();
        var variant = metadata.get("flagship");
        assertEquals(90, metadata.normalWeight());
        assertEquals(100, metadata.totalWeight());
        assertEquals("variant.addon.flagship", variant.name().orElseThrow());
        assertEquals(HostileVariants.Rarity.LEGENDARY, variant.rarity());
        assertEquals(10, variant.weight());
        assertEquals(List.of(new ResourceLocation("shincolle_kai:sturdy"), new ResourceLocation("shincolle_kai:swift")), variant.traits());
        assertEquals(1.5, variant.hostileModifiers().get("hp"));
        assertEquals(1, variant.hostileModifiers().get("hit"));
        assertEquals(new ResourceLocation("addon:textures/entity/flagship.png"), variant.visualVariant().orElseThrow());
        assertFalse(variant.redAura());
        var defaults = metadata.get("normal");
        assertEquals(HostileVariants.Rarity.RARE, defaults.rarity());
        assertTrue(defaults.name().isEmpty() && defaults.visualVariant().isEmpty() && defaults.traits().isEmpty());
        assertTrue(defaults.redAura());
        assertTrue(defaults.hostileModifiers().values().stream().allMatch(value -> value == 1));
        assertThrows(UnsupportedOperationException.class, () -> metadata.variants().clear());
        assertThrows(UnsupportedOperationException.class, () -> variant.traits().clear());
        assertThrows(UnsupportedOperationException.class, () -> variant.hostileModifiers().clear());
        json.getAsJsonObject("hostile_variants").getAsJsonArray("variants").remove(0);
        assertSame(variant, metadata.get("flagship"));
    }

    @Test
    void inclusiveLimitsAndAllZeroWeightsAreValid() throws IOException {
        JsonObject settings = new JsonObject();
        settings.addProperty("normal_weight", 10000);
        JsonArray array = new JsonArray();
        settings.add("variants", array);
        for (int i = 0; i < 16; i++) {
            JsonObject variant = JsonParser.parseString("""
                    {"id":"v","weight":10000,"traits":["shincolle_kai:sturdy","shincolle_kai:heavy_hitter",
                    "shincolle_kai:armored","shincolle_kai:quick_loader"]}
                    """).getAsJsonObject();
            variant.addProperty("id", "v".repeat(30) + String.format("%02d", i));
            variant.addProperty("name", "😀".repeat(128));
            variant.addProperty("rarity", List.of("common", "rare", "epic", "legendary").get(i % 4));
            array.add(variant);
        }
        JsonObject json = definition(settings.toString());
        assertEquals(170000, validate(json).hostileVariants().totalWeight());
        assertTrue(schema.validate(MAPPER.readTree(json.toString())).isEmpty());
        settings.addProperty("normal_weight", 0);
        array.forEach(element -> element.getAsJsonObject().addProperty("weight", 0));
        assertEquals(0, validate(definition(settings.toString())).hostileVariants().totalWeight());
    }

    static List<String> invalidSettings() {
        List<String> invalid = new ArrayList<>(List.of("null", "[]", "true", "1", "\"text\"",
                "{\"normal_weight\":-1}", "{\"normal_weight\":10001}", "{\"normal_weight\":1.5}",
                "{\"normal_weight\":2147483648}", "{\"normal_weight\":1e400}", "{\"normal_weight\":\"1\"}",
                "{\"normal_weight\":null}", "{\"variants\":null}", "{\"variants\":{}}", "{\"variants\":[null]}",
                "{\"variants\":[true]}", "{\"variants\":[[]]}"));
        for (String fragment : List.of(
                "\"weight\":0", "\"id\":\"v\"", "\"id\":null,\"weight\":0", "\"id\":1,\"weight\":0",
                "\"id\":\"\",\"weight\":0", "\"id\":\"UPPER\",\"weight\":0", "\"id\":\"addon:v\",\"weight\":0",
                "\"id\":\"v-v\",\"weight\":0", "\"id\":\"v/v\",\"weight\":0", "\"id\":\"v\\n\",\"weight\":0",
                "\"id\":\"" + "v".repeat(33) + "\",\"weight\":0")) {
            invalid.add(variants("{" + fragment + "}"));
        }
        for (String field : List.of("weight", "name", "rarity", "traits", "hostile_modifiers", "visual_variant", "red_aura")) {
            JsonObject variant = JsonParser.parseString("{\"id\":\"v\",\"weight\":0}").getAsJsonObject();
            variant.add(field, com.google.gson.JsonNull.INSTANCE);
            invalid.add(variants(variant.toString()));
        }
        for (String fragment : List.of(
                "\"weight\":-1", "\"weight\":10001", "\"weight\":1.5", "\"weight\":\"1\"", "\"weight\":true",
                "\"weight\":1e400", "\"weight\":2147483648", "\"name\":\"\"", "\"name\":false",
                "\"name\":\"" + "a".repeat(129) + "\"", "\"rarity\":\"RARE\"", "\"rarity\":\"unknown\"",
                "\"rarity\":1", "\"traits\":{}", "\"traits\":[1]", "\"traits\":[null]",
                "\"traits\":[\"shincolle_kai:missing\"]", "\"traits\":[\"addon:sturdy\"]", "\"traits\":[\"sturdy\"]",
                "\"traits\":[\"shincolle_kai:sturdy\",\"shincolle_kai:sturdy\"]",
                "\"traits\":[\"shincolle_kai:sturdy\",\"shincolle_kai:swift\",\"shincolle_kai:armored\","
                        + "\"shincolle_kai:keen_eye\",\"shincolle_kai:heavy_hitter\"]",
                "\"hostile_modifiers\":[]", "\"visual_variant\":1", "\"visual_variant\":\"texture.png\"",
                "\"visual_variant\":\"addon:Upper.png\"", "\"visual_variant\":\"a:" + "v".repeat(127) + "\"",
                "\"red_aura\":\"true\"", "\"red_aura\":1")) {
            JsonObject variant = JsonParser.parseString("{\"id\":\"v\",\"weight\":0}").getAsJsonObject();
            JsonParser.parseString("{" + fragment + "}").getAsJsonObject().entrySet()
                    .forEach(entry -> variant.add(entry.getKey(), entry.getValue()));
            invalid.add(variants(variant.toString()));
        }
        JsonArray tooMany = new JsonArray();
        for (int i = 0; i < 17; i++) tooMany.add(JsonParser.parseString("{\"id\":\"v" + i + "\",\"weight\":1}"));
        invalid.add("{\"variants\":" + tooMany + "}");
        return invalid;
    }

    @Test
    void individualTextureAccepts128CharactersWithoutWideningOtherIds() throws IOException {
        for (int length : List.of(64, 65, 128)) {
            String texture = "a:" + "v".repeat(length - 2);
            JsonObject json = definition(variants("{\"id\":\"v\",\"weight\":1,\"visual_variant\":\"" + texture + "\"}"));
            assertEquals(texture, validate(json).hostileVariants().get("v").visualVariant().orElseThrow().toString());
            assertTrue(schema.validate(MAPPER.readTree(json.toString())).isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> ShipDefinitionId.parse("a:" + "v".repeat(63)));
        JsonObject json = ShipDefinitionTest.minimum();
        json.addProperty("visual", "a:" + "v".repeat(63));
        assertThrows(IllegalArgumentException.class, () -> validate(json));
        assertFalse(schema.validate(MAPPER.readTree(json.toString())).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void invalidMetadataRejectsOnlyItsDefinitionAndAgreesWithSchema(String settings) throws IOException {
        JsonObject bad = definition(settings);
        assertThrows(IllegalArgumentException.class, () -> validate(bad), settings);
        assertFalse(schema.validate(MAPPER.readTree(bad.toString())).isEmpty(), settings);
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(VALIDATOR, registry);
        ShipDefinitionId good = ShipDefinitionId.parse("addon:good");
        loader.apply(Map.of(ID.location(), bad, good.location(), ShipDefinitionTest.minimum()), null, InactiveProfiler.INSTANCE);
        assertEquals(Set.of(good), registry.all().keySet());
    }

    @Test
    void duplicatesAreRejectedByStableIdNotWholeObjectEquality() {
        JsonObject bad = definition("{\"variants\":[{\"id\":\"same\",\"weight\":1},{\"id\":\"same\",\"weight\":2}]}");
        assertThrows(IllegalArgumentException.class, () -> validate(bad));
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        new ShipDefinitionLoader(VALIDATOR, registry).apply(Map.of(ID.location(), bad), null, InactiveProfiler.INSTANCE);
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void modifiersShareAllSixBoundsFiniteChecksAndFloatMinimum() throws IOException {
        for (String stat : ShipDefinitionFormat.BASE_MAX.keySet()) {
            for (JsonElement bad : List.of(new JsonPrimitive(0), new JsonPrimitive(-1), new JsonPrimitive(2.40001),
                    new JsonPrimitive("1"), new JsonPrimitive(true), new JsonPrimitive(Double.NaN),
                    new JsonPrimitive(Double.POSITIVE_INFINITY), new JsonPrimitive(Double.NEGATIVE_INFINITY),
                    JsonParser.parseString("1e400"), JsonParser.parseString("-1e-400"), JsonParser.parseString("9e-46"))) {
                JsonObject json = definition(variants("{\"id\":\"v\",\"weight\":1,\"hostile_modifiers\":{}}"));
                json.getAsJsonObject("hostile_variants").getAsJsonArray("variants").get(0).getAsJsonObject()
                        .getAsJsonObject("hostile_modifiers").add(stat, bad);
                assertThrows(IllegalArgumentException.class, () -> validate(json), stat + ":" + bad);
            }
            for (double valid : List.of((double) Float.MIN_VALUE, 2.4)) {
                JsonObject json = definition(variants("{\"id\":\"v\",\"weight\":1,\"hostile_modifiers\":{\"" + stat + "\":" + valid + "}}"));
                assertEquals(valid, validate(json).hostileVariants().get("v").hostileModifiers().get(stat));
                assertTrue(schema.validate(MAPPER.readTree(json.toString())).isEmpty());
            }
        }
    }

    @Test
    void unknownKeysWarnWithoutDiscardingMetadata() {
        JsonObject json = definition("""
                {"future":1,"variants":[{"id":"v","weight":1,"future":2,"hostile_modifiers":{"future":3}}]}
                """);
        List<String> warnings = new ArrayList<>();
        assertNotNull(VALIDATOR.validate(ID, json, warnings::add).hostileVariants().get("v"));
        assertEquals(3, warnings.size());
    }

    @Test
    void reloadingAddRemoveReorderAndSameIdsAcrossShipsNeverMixGenerations() {
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(VALIDATOR, registry);
        ShipDefinitionId other = ShipDefinitionId.parse("addon:other");
        JsonObject first = definition("{\"variants\":[{\"id\":\"a\",\"weight\":1},{\"id\":\"b\",\"weight\":2}]}");
        loader.apply(Map.of(ID.location(), first, other.location(), first), null, InactiveProfiler.INSTANCE);
        ShipDefinitionSnapshot old = registry.snapshot();
        assertEquals(old.getVariant(ID, "a"), old.getVariant(other, "a"));
        JsonObject second = definition("{\"variants\":[{\"id\":\"b\",\"weight\":2},{\"id\":\"a\",\"weight\":1},{\"id\":\"c\",\"weight\":3}]}");
        loader.apply(Map.of(ID.location(), second), null, InactiveProfiler.INSTANCE);
        ShipDefinitionSnapshot added = registry.snapshot();
        assertEquals(old.getVariant(ID, "a"), added.getVariant(ID, "a"));
        assertNotNull(added.getVariant(ID, "c"));
        assertNull(old.getVariant(ID, "c"));
        assertNull(added.getVariant(other, "a"));
        loader.apply(Map.of(ID.location(), ShipDefinitionTest.minimum()), null, InactiveProfiler.INSTANCE);
        assertEquals(3, registry.snapshot().generation());
        assertNull(registry.snapshot().getVariant(ID, "a"));
        assertNotNull(old.getVariant(ID, "a"));
        assertNotNull(added.getVariant(ID, "c"));
    }
}
