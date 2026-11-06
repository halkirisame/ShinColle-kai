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
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
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

class ShipDefinitionTest {
    private static final ShipDefinitionId ID = ShipDefinitionId.parse("addon:ship");
    private static final ResourceLocation AI = new ResourceLocation("shincolle_kai", "standard");
    private static final ResourceLocation ATTACK = new ResourceLocation("shincolle_kai", "light_cannon");
    private static final ResourceLocation EQUIP = new ResourceLocation("addon", "equipment");
    private static final ShipDefinitionValidator VALIDATOR = new ShipDefinitionValidator(
            AI::equals, ATTACK::equals, EQUIP::equals);

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        if (bootstrapped.getBoolean(null)) return;
        bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
    }

    static JsonObject minimum() {
        return JsonParser.parseString("""
                {"schema":1,"ship_type":"shincolle_kai:destroyer","visual":"addon:visual","base_stats":{}}
                """).getAsJsonObject();
    }

    private static ShipDefinition validate(JsonElement json) {
        return VALIDATOR.validate(ID, json, ignored -> { });
    }

    @Test
    void minimumAndDefaultsAreImmutableAndFileDerived() {
        JsonObject json = minimum();
        ShipDefinition definition = validate(json);
        assertEquals(ID, definition.id());
        assertEquals(ID.toString(), definition.displayName());
        assertEquals(6, definition.baseStats().size());
        assertTrue(definition.baseStats().values().stream().allMatch(value -> value == 0));
        assertTrue(definition.growth().values().stream().allMatch(value -> value == 0));
        assertEquals(new ShipDefinition.Equipment(0, List.of()), definition.equipment());
        assertFalse(definition.construction().enabled());
        assertEquals(100, definition.construction().weight());
        assertEquals(ShipDefinition.ConstructionMode.MATERIAL, definition.construction().mode());
        assertEquals(new ShipDefinition.Acquisition(false, 0), definition.spawn());
        assertTrue(definition.behavior().ai().isEmpty());
        assertTrue(definition.behavior().attacks().isEmpty());
        assertEquals(ShipDefinitionFormat.DEFAULT_DIMENSIONS, definition.dimensions());
        assertEquals(0.9, definition.mountOffset());
        assertEquals(1, definition.equipType());
        assertEquals(ShipDefinitionFormat.DEFAULT_ATTACK_PROFILE, definition.attackProfile());
        assertTrue(definition.hostileModifiers().values().stream().allMatch(value -> value == 1));
        json.getAsJsonObject("base_stats").addProperty("hp", 700);
        assertEquals(0, definition.baseStats().get("hp"));
        assertThrows(UnsupportedOperationException.class, () -> definition.baseStats().put("hp", 1.0));
        assertThrows(UnsupportedOperationException.class, () -> definition.behavior().attacks().add(ATTACK));
    }

    @Test
    void validOptionalFieldsAndReferenceChecks() {
        JsonObject json = minimum();
        json.add("growth", JsonParser.parseString("{\"hp\":1,\"atk\":0.5}"));
        json.add("equipment", JsonParser.parseString("{\"slots\":2,\"initial\":[\"addon:equipment\",\"addon:equipment\"]}"));
        json.add("construction", JsonParser.parseString("{\"enabled\":true,\"weight\":10,\"tier\":\"small\",\"material_mean\":80}"));
        json.add("spawn", JsonParser.parseString("{\"enabled\":false,\"weight\":2}"));
        json.add("behavior", JsonParser.parseString("""
                {"ai":"shincolle_kai:standard","attacks":["shincolle_kai:light_cannon"]}
                """));
        ShipDefinition definition = validate(json);
        assertEquals(AI, definition.behavior().ai().orElseThrow());
        assertEquals(List.of(ATTACK), definition.behavior().attacks());
        assertEquals(List.of(EQUIP, EQUIP), definition.equipment().initial());
        assertTrue(definition.construction().enabled());
        assertEquals(10, definition.construction().weight());
        json.getAsJsonObject("equipment").getAsJsonArray("initial").set(0, new JsonPrimitive("addon:missing"));
        assertThrows(IllegalArgumentException.class, () -> validate(json));
    }

    @Test
    void constructionModesDefaultsAndStrictOptionalKeys() {
        JsonObject json = minimum();
        json.add("construction", JsonParser.parseString("{\"enabled\":true,\"tier\":\"small\",\"material_mean\":100}"));
        ShipDefinition.Construction material = validate(json).construction();
        assertEquals(100, material.weight());
        assertEquals(ShipDefinition.ConstructionMode.MATERIAL, material.mode());
        assertTrue(material.matches(0));
        assertFalse(material.matches(1));
        assertEquals(100, material.materialMean().orElseThrow());
        assertTrue(material.materialBias().isEmpty());
        for (String bias : List.of("grudge", "abyssium", "ammo", "polymetal")) {
            json.getAsJsonObject("construction").addProperty("material_bias", bias);
            assertEquals(bias, validate(json).construction().materialBias().orElseThrow().name().toLowerCase(java.util.Locale.ROOT));
        }
        json.add("construction", JsonParser.parseString("{\"enabled\":true,\"mode\":\"weighted\"}"));
        ShipDefinition.Construction weighted = validate(json).construction();
        assertEquals(0, weighted.weight());
        assertTrue(weighted.matches(0) && weighted.matches(1));
        assertTrue(weighted.tier().isEmpty());
        for (String settings : List.of("{\"enabled\":true}", "{\"enabled\":true,\"tier\":\"small\"}",
                "{\"enabled\":true,\"material_mean\":100}", "{\"mode\":\"other\"}", "{\"mode\":\"MATERIAL\"}",
                "{\"tier\":\"both\"}", "{\"material_mean\":1.5}", "{\"material_mean\":-1}",
                "{\"material_mean\":2147483648}", "{\"material_mean\":\"100\"}", "{\"material_bias\":\"fuel\"}",
                "{\"material_bias\":null}", "{\"tier\":1}", "{\"enabled\":\"true\"}", "{\"weight\":-1}")) {
            json.add("construction", JsonParser.parseString(settings));
            assertThrows(IllegalArgumentException.class, () -> validate(json), settings);
        }
        json.add("construction", JsonParser.parseString("{\"enabled\":false,\"weight\":2147483647}"));
        assertFalse(validate(json).construction().enabled());
    }

    @Test
    void publishedConstructionSchemaAgreesWithValidator() throws IOException {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                Files.readString(Path.of("docs/schemas/shincolle_kai-ship_definition.schema.json")));
        ObjectMapper mapper = new ObjectMapper();
        for (String settings : List.of("{}", "{\"enabled\":false}", "{\"enabled\":true,\"mode\":\"weighted\"}",
                "{\"enabled\":true,\"tier\":\"large\",\"material_mean\":2147483647,\"material_bias\":\"ammo\"}",
                "{\"enabled\":true,\"mode\":\"weighted\",\"tier\":\"small\",\"weight\":0}",
                "{\"enabled\":true,\"mode\":\"material\",\"tier\":\"small\",\"material_mean\":0}")) {
            JsonObject json = minimum();
            json.add("construction", JsonParser.parseString(settings));
            assertTrue(schema.validate(mapper.readTree(json.toString())).isEmpty(), settings);
            validate(json);
        }
        for (String settings : List.of("{\"enabled\":true}", "{\"enabled\":true,\"mode\":\"material\"}",
                "{\"enabled\":true,\"tier\":\"small\"}", "{\"enabled\":true,\"material_mean\":100}",
                "{\"mode\":null}", "{\"mode\":\"MATERIAL\"}", "{\"tier\":\"both\"}", "{\"material_mean\":-1}",
                "{\"material_mean\":1.5}", "{\"material_mean\":2147483648}", "{\"material_bias\":\"fuel\"}")) {
            JsonObject json = minimum();
            json.add("construction", JsonParser.parseString(settings));
            assertFalse(schema.validate(mapper.readTree(json.toString())).isEmpty(), settings);
            assertThrows(IllegalArgumentException.class, () -> validate(json), settings);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void positiveMaterialWeightRemainsReachableBelowLegacyFloatGuard() throws ReflectiveOperationException {
        JsonObject json = minimum();
        json.add("construction", JsonParser.parseString(
                "{\"enabled\":true,\"tier\":\"small\",\"material_mean\":106,\"weight\":1}"));
        ShipDefinition ship = validate(json);
        int[] mats = {16, 20, 30, 40};
        var method = com.lulan.shincolle.crafting.ShipCalc.class.getDeclaredMethod(
                "materialTable", int.class, int[].class, List.class);
        method.setAccessible(true);
        Map<ShipChoice, Float> table = (Map<ShipChoice, Float>) method.invoke(null, 0, mats, List.of(ship));
        ShipChoice choice = new ShipChoice.Defined(ID);
        float total = 0;
        float before = 0;
        boolean found = false;
        for (var entry : table.entrySet()) {
            total += entry.getValue();
            if (entry.getKey().equals(choice)) found = true;
            else if (!found) before += entry.getValue();
        }
        float probability = table.get(choice);
        assertTrue(probability > 0 && probability < 0.0125F, "fixture must be below the inherited float guard");
        float roll = (before + 0.0125F + probability / 2) / total;
        assertTrue(roll >= 0 && roll < 1, "positive material candidate has no reachable random interval");
        ShipDefinitionRegistry registry = ShipDefinitionRegistry.server();
        Map<ShipDefinitionId, ShipDefinition> previous = registry.all();
        registry.reload(() -> Map.of(ID, ship));
        try {
            var random = new net.minecraft.world.level.levelgen.LegacyRandomSource(731) {
                @Override
                public float nextFloat() {
                    return roll;
                }
            };
            assertEquals(choice, com.lulan.shincolle.crafting.ShipCalc.rollShipType(0, mats, random));
        } finally {
            registry.reload(() -> previous);
        }
    }

    @Test
    void entityFieldsAndHostileModifiersAreStrictBoundedAndImmutable() {
        JsonObject json = minimum();
        json.add("dimensions", JsonParser.parseString("{\"width\":1.6,\"height\":4.4}"));
        json.addProperty("mount_offset", 4.4);
        json.addProperty("equip_type", 3);
        json.addProperty("attack_profile", "shincolle_kai:default");
        json.add("hostile_modifiers", JsonParser.parseString("{\"hp\":2.4,\"atk\":0.4}"));
        List<String> warnings = new ArrayList<>();
        ShipDefinition definition = VALIDATOR.validate(ID, json, warnings::add);
        assertTrue(warnings.isEmpty());
        assertEquals(1.6F, definition.dimensions().width());
        assertEquals(4.4F, definition.dimensions().height());
        assertEquals(2.4, definition.hostileModifiers().get("hp"));
        assertEquals(1, definition.hostileModifiers().get("hit"));
        assertThrows(UnsupportedOperationException.class, () -> definition.hostileModifiers().put("hp", 1.0));
        for (String key : ShipDefinitionFormat.BASE_MAX.keySet()) {
            for (JsonElement bad : List.of(new JsonPrimitive(0), new JsonPrimitive(-1), new JsonPrimitive(2.40001),
                    new JsonPrimitive("1"), new JsonPrimitive(true), new JsonPrimitive(Double.NaN),
                    new JsonPrimitive(Double.POSITIVE_INFINITY), JsonParser.parseString("-1e-400"))) {
                JsonObject candidate = minimum();
                JsonObject modifiers = new JsonObject();
                modifiers.add(key, bad);
                candidate.add("hostile_modifiers", modifiers);
                assertThrows(IllegalArgumentException.class, () -> validate(candidate), key + ": " + bad);
            }
        }
        for (String bad : List.of("{\"dimensions\":{\"width\":0}}", "{\"dimensions\":{\"height\":4.40001}}",
                "{\"dimensions\":{\"width\":\"1\"}}", "{\"mount_offset\":-1}", "{\"mount_offset\":4.40001}",
                "{\"equip_type\":4}", "{\"equip_type\":1.5}", "{\"attack_profile\":\"addon:other\"}")) {
            JsonObject candidate = minimum();
            JsonParser.parseString(bad).getAsJsonObject().entrySet().forEach(e -> candidate.add(e.getKey(), e.getValue()));
            assertThrows(IllegalArgumentException.class, () -> validate(candidate), bad);
        }
    }

    @Test
    void unknownKeysWarnAndReservedRecruitmentIsDiscarded() {
        JsonObject json = minimum();
        json.addProperty("future", true);
        json.addProperty("recruitment", "reserved, not interpreted");
        json.getAsJsonObject("base_stats").addProperty("future_stat", -100);
        json.add("behavior", JsonParser.parseString("{\"future\":3}"));
        List<String> warnings = new ArrayList<>();
        ShipDefinition definition = VALIDATOR.validate(ID, json, warnings::add);
        assertNotNull(definition);
        assertEquals(3, warnings.size());
        assertTrue(warnings.stream().noneMatch(line -> line.contains("recruitment")));
    }

    @Test
    void missingRequiredFieldsWrongTypesAndSchemaVersionsAreRejected() {
        for (String field : List.of("schema", "ship_type", "visual", "base_stats")) {
            JsonObject json = minimum();
            json.remove(field);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> validate(json), field);
            if (field.equals("schema")) {
                assertTrue(exception.getMessage().contains("schema is required"));
            }
        }
        for (String field : ShipDefinitionFormat.FIELDS) {
            if (field.equals("recruitment")) {
                continue;
            }
            JsonObject json = minimum();
            json.add(field, com.google.gson.JsonNull.INSTANCE);
            assertThrows(IllegalArgumentException.class, () -> validate(json), field);
        }
        for (JsonElement version : List.of(new JsonPrimitive(0), new JsonPrimitive(2),
                new JsonPrimitive("1"), new JsonPrimitive(1.5), new JsonPrimitive(true))) {
            JsonObject json = minimum();
            json.add("schema", version);
            assertThrows(IllegalArgumentException.class, () -> validate(json), version.toString());
        }
        JsonObject future = minimum();
        future.addProperty("schema", 2);
        String message = assertThrows(IllegalArgumentException.class, () -> validate(future)).getMessage();
        assertTrue(message.contains("schema 2") && message.contains("schema 1"), message);
    }

    @Test
    void allStatBoundsAreInclusiveAndNonFiniteNegativeAndOversizedValuesAreRejected() {
        for (String field : List.of("base_stats", "growth")) {
            Map<String, Double> limits = field.equals("base_stats") ? ShipDefinitionFormat.BASE_MAX : ShipDefinitionFormat.GROWTH_MAX;
            for (var limit : limits.entrySet()) {
                JsonObject json = minimum();
                JsonObject stats = new JsonObject();
                json.add(field, stats);
                stats.addProperty(limit.getKey(), limit.getValue());
                validate(json);
                for (double bad : List.of(-0.01, limit.getValue() + 0.001,
                        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                    stats.addProperty(limit.getKey(), bad);
                    assertThrows(IllegalArgumentException.class, () -> validate(json), field + "." + limit.getKey() + "=" + bad);
                }
                stats.add(limit.getKey(), JsonParser.parseString("1e400"));
                assertThrows(IllegalArgumentException.class, () -> validate(json));
                stats.add(limit.getKey(), JsonParser.parseString("-1e-400"));
                assertThrows(IllegalArgumentException.class, () -> validate(json));
                stats.addProperty(limit.getKey(), "1");
                assertThrows(IllegalArgumentException.class, () -> validate(json));
            }
        }
    }

    @Test
    void explicitIdsLengthsAndBehaviorNamespacesAreChecked() {
        for (String bad : List.of("ship", "Addon:ship", "a:", ":ship", "a:ship name", "a:" + "s".repeat(63))) {
            assertThrows(IllegalArgumentException.class, () -> ShipDefinitionId.parse(bad), bad);
        }
        assertEquals(64, ShipDefinitionId.parse("a:" + "s".repeat(62)).toString().length());
        JsonObject json = minimum();
        json.addProperty("display_name", "s".repeat(64));
        validate(json);
        json.addProperty("display_name", "s".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> validate(json));
        json.addProperty("display_name", "😀".repeat(64));
        validate(json);
        for (String bad : List.of("addon:test_ai", "shincolle:test_ai", "shincolle_kai:missing", "Some.Java.Class")) {
            json.add("behavior", JsonParser.parseString("{\"ai\":\"" + bad + "\"}"));
            assertThrows(IllegalArgumentException.class, () -> validate(json), bad);
        }
    }

    @Test
    void duplicateAttackAndArrayBoundsAreRejected() {
        JsonObject json = minimum();
        json.add("behavior", JsonParser.parseString("{\"attacks\":[\"shincolle_kai:light_cannon\",\"shincolle_kai:light_cannon\"]}"));
        assertThrows(IllegalArgumentException.class, () -> validate(json));
        json.remove("behavior");
        json.add("equipment", JsonParser.parseString("{\"slots\":17}"));
        assertThrows(IllegalArgumentException.class, () -> validate(json));
        json.add("equipment", JsonParser.parseString("{\"slots\":0,\"initial\":[\"addon:equipment\"]}"));
        assertThrows(IllegalArgumentException.class, () -> validate(json));
        json.add("equipment", JsonParser.parseString("{\"slots\":16}"));
        validate(json);
        JsonArray attacks = new JsonArray();
        for (int i = 0; i < 17; i++) {
            attacks.add("shincolle_kai:attack_" + i);
        }
        JsonObject behavior = new JsonObject();
        behavior.add("attacks", attacks);
        json.add("behavior", behavior);
        ShipDefinitionValidator accepting = new ShipDefinitionValidator(id -> true, id -> true, id -> true);
        assertThrows(IllegalArgumentException.class, () -> accepting.validate(ID, json, ignored -> { }));
        attacks.remove(16);
        assertEquals(16, accepting.validate(ID, json, ignored -> { }).behavior().attacks().size());
    }

    @Test
    void byteDepthSyntaxUtf8AndDuplicateMemberGuardsRunBeforeValidation() throws IOException {
        assertTrue(ShipDefinitionJson.read(minimum().toString().getBytes(StandardCharsets.UTF_8)).isJsonObject());
        assertThrows(IllegalArgumentException.class, () -> ShipDefinitionJson.read(new byte[65537]));
        assertThrows(IllegalArgumentException.class, () -> raw("{\"schema\":1,\"schema\":1}"));
        assertThrows(IllegalArgumentException.class, () -> raw("{\"nested\":{\"hp\":1,\"hp\":2}}"));
        assertThrows(IllegalArgumentException.class, () -> raw("[".repeat(34) + "0" + "]".repeat(34)));
        assertThrows(IOException.class, () -> raw("{\"value\":NaN}"));
        assertThrows(IOException.class, () -> raw("{\"value\":Infinity}"));
        assertThrows(IOException.class, () -> raw("{bad}"));
        assertThrows(IOException.class, () -> ShipDefinitionJson.read(new byte[]{(byte) 0xff}));
        assertThrows(IOException.class, () -> raw("{} {}"));
        String padded = minimum() + " ".repeat(65536 - minimum().toString().length());
        assertTrue(raw(padded).isJsonObject());
    }

    private static JsonElement raw(String json) throws IOException {
        return ShipDefinitionJson.read(json.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 9, 10, 13, 31})
    void rawControlCharactersInStringValuesAreRejected(int control) {
        String invalid = "{\"display_name\":\"a" + (char) control + "b\"}";
        assertThrows(IOException.class, () -> raw(invalid));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\'", "\\x", "\\a", "\\0", "\\u12G4", "\\u12", "\\u", "\\u123g"})
    void invalidEscapesAreRejected(String escape) {
        assertThrows(IOException.class, () -> raw("{\"display_name\":\"a" + escape + "b\"}"));
        assertThrows(IOException.class, () -> raw("{\"a" + escape + "b\":1}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\\"", "\\\\", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t", "\\u00e9", "\\u00E9", "\\u0000"})
    void validEscapesAreAccepted(String escape) throws IOException {
        assertTrue(raw("{\"display_name\":\"a" + escape + "b\"}").isJsonObject());
        assertTrue(raw("{\"a" + escape + "b\":1}").isJsonObject());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 9, 10, 13, 31})
    void rawControlCharactersInPropertyNamesAreRejected(int control) {
        assertThrows(IOException.class, () -> raw("{\"a" + (char) control + "b\":1}"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 9, 10, 13, 31})
    void realPrepareAndApplyRejectRawControlsWithoutRejectingGoodDefinition(int control) {
        String directory = ShipDefinitionFormat.DIRECTORY + "/";
        String json = minimum().toString();
        String invalid = json.substring(0, json.length() - 1) + ",\"display_name\":\"a" + (char) control + "b\"}";
        ResourceManager manager = resources(Map.of(
                new ResourceLocation("addon", directory + "good.json"), minimum().toString(),
                new ResourceLocation("addon", directory + "bad.json"), invalid));
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(new ShipDefinitionValidator(), registry);
        loader.apply(loader.prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
        assertEquals(1, registry.generation());
        assertEquals(Set.of(ShipDefinitionId.parse("addon:good")), registry.all().keySet());
        assertNotNull(registry.get(ShipDefinitionId.parse("addon:good")));
        assertNull(registry.get(ShipDefinitionId.parse("addon:bad")));
    }

    @Test
    void escapedControlsQuotesAndBackslashesAreDecodedAndPreserved() throws IOException {
        Map<String, String> escaped = new HashMap<>(Map.of(
                "\\n", "\n", "\\t", "\t", "\\r", "\r", "\\b", "\b", "\\f", "\f",
                "\\\"", "\"", "\\\\", "\\", "\\\\\\\"", "\\\"", "\\\\n", "\\n"));
        for (int control = 0; control <= 31; control++) {
            escaped.put(String.format("\\u%04x", control), String.valueOf((char) control));
        }
        for (var entry : escaped.entrySet()) {
            String json = minimum().toString();
            json = json.substring(0, json.length() - 1) + ",\"display_name\":\"a" + entry.getKey() + "b\"}";
            assertEquals("a" + entry.getValue() + "b", validate(raw(json)).displayName(), entry.getKey());
            assertTrue(raw("{\"a" + entry.getKey() + "b\":1}").getAsJsonObject()
                    .has("a" + entry.getValue() + "b"), entry.getKey());
        }
    }

    @Test
    void tabsAndNewlinesBetweenTokensRemainValid() throws IOException {
        String formatted = "{\r\n\t\"schema\"\t: 1,\n\t\"ship_type\": \"shincolle_kai:destroyer\","
                + "\n\t\"visual\": \"addon:visual\",\n\t\"base_stats\": {\n\t}\n}\t\n";
        assertEquals(validate(minimum()), validate(raw(formatted)));
    }

    @Test
    void entireRawControlRangeIsRejectedInStringsAndPropertyNames() {
        for (int control = 0; control <= 31; control++) {
            String value = "{\"value\":\"a" + (char) control + "b\"}";
            String key = "{\"a" + (char) control + "b\":1}";
            assertThrows(IOException.class, () -> raw(value), "value control=" + control);
            assertThrows(IOException.class, () -> raw(key), "key control=" + control);
        }
    }

    private static ResourceManager resources(Map<ResourceLocation, String> files) {
        Map<ResourceLocation, Resource> resources = new HashMap<>();
        files.forEach((id, value) -> resources.put(id, new Resource(null,
                () -> new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)))));
        return (ResourceManager) Proxy.newProxyInstance(ShipDefinitionTest.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) {
                        return resources;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void realPrepareAndApplyIsolateInvalidFilesAndPreserveThirdPartyFileNamespaces() {
        Map<ResourceLocation, String> files = new HashMap<>();
        String directory = ShipDefinitionFormat.DIRECTORY + "/";
        ResourceLocation good = new ResourceLocation("addon", directory + "nested/good.json");
        files.put(good, minimum().toString());
        List<JsonObject> rejected = new ArrayList<>();
        JsonObject badAi = minimum();
        badAi.add("behavior", JsonParser.parseString("{\"ai\":\"shincolle_kai:missing\"}"));
        rejected.add(badAi);
        JsonObject foreign = minimum();
        foreign.addProperty("ship_type", "addon:destroyer");
        rejected.add(foreign);
        JsonObject overflow = minimum();
        overflow.getAsJsonObject("base_stats").addProperty("hp", 701);
        rejected.add(overflow);
        JsonObject infinite = minimum();
        infinite.getAsJsonObject("base_stats").add("hp", JsonParser.parseString("1e400"));
        rejected.add(infinite);
        for (int i = 0; i < rejected.size(); i++) {
            files.put(new ResourceLocation("addon", directory + "bad_" + i + ".json"), rejected.get(i).toString());
        }
        files.put(new ResourceLocation("addon", directory + "duplicate.json"),
                minimum().toString().replace("\"schema\":1", "\"schema\":1,\"schema\":1"));
        files.put(new ResourceLocation("addon", directory + "oversized.json"), " ".repeat(65537));
        files.put(new ResourceLocation("addon", directory + "syntax.json"), "{bad}");
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(VALIDATOR, registry);
        ResourceManager manager = resources(files);
        loader.apply(loader.prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
        assertEquals(1, registry.generation());
        assertEquals(Set.of(ShipDefinitionId.parse("addon:nested/good")), registry.all().keySet());
        FileToIdConverter converter = FileToIdConverter.json(ShipDefinitionFormat.DIRECTORY);
        assertEquals(new ResourceLocation("addon", "nested/good"), converter.fileToId(good));
    }

    @Test
    void fatalApplyAndPreparationFailureRetainOldSnapshotAndGeneration() {
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(VALIDATOR, registry);
        loader.apply(Map.of(ID.location(), minimum()), null, InactiveProfiler.INSTANCE);
        ShipDefinitionSnapshot old = registry.snapshot();
        Map<ResourceLocation, JsonElement> broken = new AbstractMap<>() {
            @Override
            public Set<Entry<ResourceLocation, JsonElement>> entrySet() {
                throw new IllegalStateException("simulated resource traversal failure");
            }
        };
        loader.apply(broken, null, InactiveProfiler.INSTANCE);
        assertSame(old, registry.snapshot());
        ResourceManager unavailable = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("simulated resource manager failure");
                });
        assertThrows(IllegalStateException.class, () -> loader.prepare(unavailable, InactiveProfiler.INSTANCE));
        assertSame(old, registry.snapshot());
        assertEquals(1, registry.generation());
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
        loader.apply(Map.of(), null, InactiveProfiler.INSTANCE);
        assertEquals(2, registry.generation());
        assertNull(registry.get(ID));
        assertNotNull(old.get(ID));
    }

    @Test
    void candidateConstructionAndKeyMismatchCannotPublishPartialData() {
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        registry.reload(() -> Map.of(ID, validate(minimum())));
        ShipDefinitionSnapshot old = registry.snapshot();
        assertThrows(IllegalStateException.class, () -> registry.reload(() -> {
            Map<ShipDefinitionId, ShipDefinition> partial = new HashMap<>();
            partial.put(ID, validate(minimum()));
            throw new IllegalStateException("candidate failed after first definition");
        }));
        assertSame(old, registry.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> registry.reload(() -> Map.of(ShipDefinitionId.parse("other:ship"), validate(minimum()))));
        assertSame(old, registry.snapshot());
    }

    @Test
    void publishedDraft202012SchemaAndValidatorAgreeOnRepresentativeInputs() throws IOException {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                Files.readString(Path.of("docs/schemas/shincolle_kai-ship_definition.schema.json")));
        ObjectMapper mapper = new ObjectMapper();
        JsonObject sample = JsonParser.parseString(Files.readString(Path.of(
                "src/gametestData/resources/data/shincolle_test/shincolle_kai/ships/sample_ship.json"))).getAsJsonObject();
        assertTrue(schema.validate(mapper.readTree(sample.toString())).isEmpty());
        validate(sample);
        JsonObject all = minimum();
        all.add("behavior", JsonParser.parseString("{\"ai\":\"shincolle_kai:standard\",\"attacks\":[\"shincolle_kai:light_cannon\"]}"));
        all.add("equipment", JsonParser.parseString("{\"slots\":1,\"initial\":[\"addon:equipment\"]}"));
        all.addProperty("unknown", "ignored");
        all.addProperty("recruitment", 123);
        all.add("construction", JsonParser.parseString("{\"enabled\":true,\"weight\":2147483647,\"mode\":\"weighted\"}"));
        all.add("spawn", JsonParser.parseString("{\"enabled\":false,\"weight\":0}"));
        all.add("dimensions", JsonParser.parseString("{\"width\":1.6,\"height\":4.4}"));
        all.addProperty("mount_offset", 4.4);
        all.addProperty("equip_type", 3);
        all.addProperty("attack_profile", "shincolle_kai:default");
        all.add("hostile_modifiers", JsonParser.parseString("{\"hp\":2.4,\"atk\":0.5}"));
        assertTrue(schema.validate(mapper.readTree(all.toString())).isEmpty());
        validate(all);
        for (String fragment : List.of(
                "\"dimensions\":{\"width\":1.401298464324817e-45,\"height\":1.401298464324817e-45}",
                "\"hostile_modifiers\":{\"hp\":1.401298464324817e-45}",
                "\"mount_offset\":1e-100", "\"base_stats\":{\"hp\":1e-100}", "\"growth\":{\"hp\":1e-100}")) {
            JsonObject json = minimum();
            JsonParser.parseString("{" + fragment + "}").getAsJsonObject().entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
            assertTrue(schema.validate(mapper.readTree(json.toString())).isEmpty(), json.toString());
            validate(json);
        }
        List<JsonObject> invalid = new ArrayList<>();
        for (String key : List.of("schema", "ship_type", "visual", "base_stats")) {
            JsonObject json = minimum();
            json.remove(key);
            invalid.add(json);
        }
        for (String key : List.of("schema", "visual", "base_stats", "growth", "equipment", "spawn", "construction", "behavior")) {
            JsonObject json = minimum();
            json.add(key, com.google.gson.JsonNull.INSTANCE);
            invalid.add(json);
        }
        for (String field : List.of("base_stats", "growth")) {
            Map<String, Double> limits = field.equals("base_stats") ? ShipDefinitionFormat.BASE_MAX : ShipDefinitionFormat.GROWTH_MAX;
            for (var limit : limits.entrySet()) {
                for (double bad : List.of(-1.0, limit.getValue() + 0.01)) {
                    JsonObject json = minimum();
                    JsonObject stats = new JsonObject();
                    stats.addProperty(limit.getKey(), bad);
                    json.add(field, stats);
                    invalid.add(json);
                }
                JsonObject max = minimum();
                JsonObject stats = new JsonObject();
                stats.addProperty(limit.getKey(), limit.getValue());
                max.add(field, stats);
                assertTrue(schema.validate(mapper.readTree(max.toString())).isEmpty());
                validate(max);
            }
        }
        for (String fragment : List.of(
                "\"schema\":2", "\"schema\":\"1\"", "\"visual\":\"MissingNamespace\"", "\"visual\":\"addon:visual\\n\"",
                "\"dimensions\":{\"width\":0}", "\"dimensions\":{\"height\":4.401}",
                "\"dimensions\":{\"width\":1e-100}", "\"dimensions\":{\"height\":1e-100}",
                "\"dimensions\":{\"width\":9e-46}",
                "\"hostile_modifiers\":{\"hp\":1e-100}", "\"hostile_modifiers\":{\"hp\":9e-46}",
                "\"dimensions\":{\"width\":1.601}", "\"dimensions\":false", "\"mount_offset\":-0.01",
                "\"mount_offset\":4.401", "\"equip_type\":4", "\"equip_type\":1.5", "\"equip_type\":\"1\"",
                "\"attack_profile\":\"addon:other\"", "\"hostile_modifiers\":{\"hp\":0}",
                "\"hostile_modifiers\":{\"spd\":2.401}", "\"hostile_modifiers\":{\"hit\":\"1\"}",
                "\"ship_type\":\"addon:destroyer\"", "\"equipment\":{\"slots\":17}",
                "\"equipment\":{\"slots\":0,\"initial\":[\"addon:equipment\"]}",
                "\"equipment\":{\"initial\":[\"addon:equipment\"]}",
                "\"behavior\":{\"ai\":\"addon:test_ai\"}",
                "\"behavior\":{\"attacks\":[\"shincolle_kai:light_cannon\",\"shincolle_kai:light_cannon\"]}",
                "\"behavior\":{\"attacks\":[]}",
                "\"behavior\":{\"attacks\":[\"shincolle_kai:nagato_skill\"]}",
                "\"behavior\":{\"ai\":\"shincolle_kai:unknown\"}",
                "\"spawn\":{\"enabled\":\"true\"}", "\"construction\":{\"weight\":-1}",
                "\"display_name\":\"" + "x".repeat(65) + "\"")) {
            JsonObject json = minimum();
            JsonParser.parseString("{" + fragment + "}").getAsJsonObject().entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
            invalid.add(json);
        }
        for (JsonObject json : invalid) {
            assertFalse(schema.validate(mapper.readTree(json.toString())).isEmpty(), json.toString());
            assertThrows(IllegalArgumentException.class, () -> validate(json), json.toString());
        }
    }
}
