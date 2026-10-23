package com.lulan.shincolle.visualdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualDefinitionTest {
    private static JsonObject minimum() {
        return JsonParser.parseString("""
                {"schema":1,"model":"addon:geo/ship.geo.json","texture":"addon:textures/entity/ship.png",
                 "animation":"addon:animations/ship.animation.json"}
                """).getAsJsonObject();
    }

    private static ShipVisualDefinition validate(JsonElement json) {
        return new ShipVisualValidator().validate(json, ignored -> { });
    }

    @Test
    void defaultsAndOverridesAreImmutableAndRoleBased() {
        var visual = validate(minimum());
        assertEquals(1, visual.scale());
        assertEquals(0.35F, visual.shadowRadius());
        assertEquals("head", visual.bone("head"));
        assertEquals("heavy_attack", visual.clip(ShipVisualEvent.HEAVY_ATTACK));
        var json = minimum();
        json.add("rig", JsonParser.parseString("{\"head\":\"Head\"}"));
        json.add("animations", JsonParser.parseString("{\"idle\":\"animation.custom.idle\"}"));
        var changed = validate(json);
        assertEquals("Head", changed.bone("head"));
        assertEquals("root", changed.bone("root"));
        assertEquals("animation.custom.idle", changed.clip(ShipVisualEvent.IDLE));
        assertThrows(UnsupportedOperationException.class, () -> changed.rig().put("head", "bad"));
        assertThrows(UnsupportedOperationException.class, () -> changed.animations().clear());
    }

    @Test
    void unknownKeysWarnAndDoNotChangeOptionalFeatureDefaults() {
        var json = minimum();
        json.addProperty("future", true);
        json.add("rig", JsonParser.parseString("{\"future\":null}"));
        json.add("animations", JsonParser.parseString("{\"future\":null}"));
        json.add("held_item", JsonParser.parseString("{\"future\":null}"));
        json.add("lod", JsonParser.parseString("{\"future\":null}"));
        json.add("expressions", JsonParser.parseString("{\"future\":null}"));
        var warnings = new ArrayList<String>();
        assertNotNull(new ShipVisualValidator().validate(json, warnings::add));
        assertEquals(6, warnings.size());
        assertEquals(List.of(), validate(json).glowBones());
        assertEquals(ShipVisualDefinition.HeldItem.DEFAULT, validate(json).heldItem());
        assertEquals(ShipVisualDefinition.Lod.DEFAULT, validate(json).lod());
    }

    private static List<JsonObject> candidates() {
        var all = new ArrayList<JsonObject>();
        all.add(minimum());
        for (String key : List.of("schema", "model", "texture", "animation", "scale", "shadow_radius",
                "rig", "animations", "expressions", "glow_bones", "held_item", "lod", "$schema")) {
            var bad = minimum();
            bad.add(key, JsonNull.INSTANCE);
            all.add(bad);
        }
        for (String key : List.of("schema", "model", "texture", "animation")) {
            var bad = minimum();
            bad.remove(key);
            all.add(bad);
        }
        for (String key : List.of("scale", "shadow_radius")) {
            for (JsonElement value : List.of(new JsonPrimitive("1"), new JsonPrimitive(true),
                    new JsonPrimitive(-1), new JsonPrimitive(16.00001), JsonParser.parseString("-1e-400"),
                    JsonParser.parseString("1e400"), new JsonPrimitive(16), new JsonPrimitive(0), JsonParser.parseString("1e-100"))) {
                var json = minimum();
                json.add(key, value);
                all.add(json);
            }
        }
        for (JsonElement version : List.of(new JsonPrimitive("1"), new JsonPrimitive(2), new JsonPrimitive(1.5))) {
            var json = minimum();
            json.add("schema", version);
            all.add(json);
        }
        for (String value : List.of("", "default", "Addon:geo/a.json", "addon:" + "x".repeat(251), "addon:valid/file.json")) {
            var json = minimum();
            json.addProperty("model", value);
            all.add(json);
        }
        for (String key : List.of("rig", "animations")) {
            for (String value : List.of("", "a".repeat(129), "a\nb", "a\u007fb", "Head", "😀".repeat(128))) {
                var json = minimum();
                JsonObject nested = new JsonObject();
                nested.addProperty(key.equals("rig") ? "head" : "idle", value);
                nested.add("future", JsonNull.INSTANCE);
                json.add(key, nested);
                all.add(json);
            }
        }
        var boundary = minimum();
        for (String slot : ShipVisualPresentation.SLOT_SIZES.keySet()) {
            for (String value : List.of("null", "true", "\"Face0\"", "[]", "[123]", "[\"\"]", "[\"a\\nb\"]",
                    "[\"Face0\",\"Face0\"]", "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]", "[\"Face0\"]")) {
                var json = minimum();
                json.add("expressions", JsonParser.parseString("{\"" + slot + "\":" + value + ",\"future\":null}"));
                all.add(json);
            }
            for (String value : List.of("a".repeat(129), "😀".repeat(128), "a\u007fb")) {
                var json = minimum();
                var array = new com.google.gson.JsonArray();
                array.add(value);
                var slots = new JsonObject();
                slots.add(slot, array);
                json.add("expressions", slots);
                all.add(json);
            }
        }
        boundary.add("scale", JsonParser.parseString("1.401298464324817e-45"));
        all.add(boundary);
        for (String key : List.of("glow_bones", "lod.hide_bones")) {
            for (String value : List.of("null", "true", "{}", "\"Face0\"", "[]", "[123]", "[\"\"]", "[\"a\\nb\"]",
                    "[\"Face0\",\"Face0\"]", "[\"Face0\"]")) {
                all.add(extension(key, value));
            }
            var many = new com.google.gson.JsonArray();
            for (int i = 0; i < 129; i++) many.add("bone_" + i);
            all.add(extension(key, many.toString()));
            many.remove(128);
            all.add(extension(key, many.toString()));
        }
        for (String key : List.of("held_item.offset", "held_item.rotate")) {
            for (String value : List.of("null", "true", "{}", "[]", "[0,0]", "[0,0,0,0]", "[0,\"0\",0]", "[0,null,0]",
                    "[0,1e400,0]", "[0,-1e400,0]", "[0,0,0]", "[-0.5,45,2]", "[1.7976931348623157e308,0,0]")) {
                all.add(extension(key, value));
            }
        }
        for (String value : List.of("null", "true", "\"48\"", "0", "-1e-400", "0.999999", "1", "48", "255.9", "256", "256.0001",
                "1e400")) all.add(extension("lod.distance", value));
        for (String key : List.of("held_item", "lod")) {
            for (String value : List.of("true", "[]", "{}", "{\"future\":null}")) all.add(extension(key, value));
        }
        all.add(extension("rig.held_item_left", "\"LeftLocator\""));
        all.add(extension("rig.held_item_left", "123"));
        for (var event : ShipVisualEvent.values()) {
            for (String value : List.of("null", "true", "123", "[]", "{}", "\"\"", "\"a\\nb\"", "\"valid.clip\"")) {
                all.add(extension("animations." + event.key(), value));
            }
        }
        return all;
    }

    private static JsonObject extension(String path, String value) {
        var json = minimum();
        int separator = path.indexOf('.');
        if (separator < 0) json.add(path, JsonParser.parseString(value));
        else {
            var nested = new JsonObject();
            nested.add(path.substring(separator + 1), JsonParser.parseString(value));
            json.add(path.substring(0, separator), nested);
        }
        return json;
    }

    @Test
    void heldItemVectorsAndOptionalGroupsAreParsedAndImmutable() {
        var json = extension("held_item.offset", "[0.0625,0.625,0.125]");
        json.getAsJsonObject("held_item").add("rotate", JsonParser.parseString("[-90,180,12.5]"));
        json.add("glow_bones", JsonParser.parseString("[\"Face0\"]"));
        json.add("lod", JsonParser.parseString("{\"hide_bones\":[\"Hair\"]}"));
        var visual = validate(json);
        assertEquals(new ShipVisualDefinition.Vector(0.0625, 0.625, 0.125), visual.heldItem().offset());
        assertEquals(new ShipVisualDefinition.Vector(-90, 180, 12.5), visual.heldItem().rotate());
        assertEquals(48, visual.lod().distance());
        assertThrows(UnsupportedOperationException.class, () -> visual.glowBones().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.lod().hideBones().clear());
        var missing = new ArrayList<String>();
        assertEquals(Set.of(), ShipVisualPolicy.boneRoots(List.of("Face0", "absent"), Set.of("Face0"), missing::add));
        assertEquals(List.of("absent"), missing);
        assertEquals(Set.of("Face0"), ShipVisualPolicy.boneRoots(List.of("Face0"), Set.of("Face0"), missing::add));
    }

    @Test
    void schemaAndRuntimeValidatorAgreeInBothDirections() throws IOException {
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                Files.readString(Path.of("docs/schemas/shincolle_kai-ship_visual.schema.json")));
        var mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        for (var json : candidates()) {
            boolean accepted;
            try {
                validate(json);
                accepted = true;
            } catch (IllegalArgumentException exception) {
                accepted = false;
            }
            assertEquals(schema.validate(mapper.readTree(json.toString())).isEmpty(), accepted, json.toString());
        }
        var official = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/shincolle_kai/shincolle_kai/visuals/kashima.json")));
        assertTrue(schema.validate(mapper.readTree(official.toString())).isEmpty());
        assertEquals("Head", validate(official).bone("head"));
        var definition = validate(official);
        assertEquals(5, definition.expressions().get("face").size());
        assertThrows(UnsupportedOperationException.class, () -> definition.expressions().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.expressions().get("face").clear());
    }

    @Test
    void nonfiniteNumbersAndWrongKnownNestedTypesAreRejected() {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var json = minimum();
            json.addProperty("scale", bad);
            assertThrows(IllegalArgumentException.class, () -> validate(json));
            JsonObject badVector = extension("held_item.offset", "[0,0,0]");
            badVector.getAsJsonObject("held_item").getAsJsonArray("offset").set(1, new JsonPrimitive(bad));
            assertThrows(IllegalArgumentException.class, () -> validate(badVector));
        }
        for (String key : List.of("rig", "animations")) {
            var json = minimum();
            var object = new JsonObject();
            object.addProperty(key.equals("rig") ? "head" : "idle", 123);
            json.add(key, object);
            assertThrows(IllegalArgumentException.class, () -> validate(json));
        }
    }

    @Test
    void roleResolutionDisablesOnlyMissingRolesAndHonoursAliases() {
        var json = minimum();
        json.add("rig", JsonParser.parseString("{\"head\":\"Head\"}"));
        var missing = new ArrayList<String>();
        var resolved = ShipVisualPolicy.resolveRig(validate(json), Set.of("Head", "root", "head"), missing::add);
        assertEquals(Map.of("head", "Head", "root", "root"), resolved);
        assertEquals(13, missing.size());
        assertFalse(missing.contains("head"));
    }

    @Test
    void boneBudgetBoundariesAndPlaceholderSelection() {
        for (int count : new int[]{0, 1, 64}) assertEquals(ShipVisualPolicy.BoneBudget.RECOMMENDED, ShipVisualPolicy.boneBudget(count));
        for (int count : new int[]{65, 93, 128}) assertEquals(ShipVisualPolicy.BoneBudget.WARNING, ShipVisualPolicy.boneBudget(count));
        for (int count : new int[]{129, 1000}) assertEquals(ShipVisualPolicy.BoneBudget.REJECTED, ShipVisualPolicy.boneBudget(count));
        Map<String, String> accepted = new HashMap<>(Map.of("good", "model"));
        var warnings = new ArrayList<String>();
        assertEquals("model", ShipVisualPolicy.select(accepted, "good", "pink", warnings::add));
        assertEquals("pink", ShipVisualPolicy.select(accepted, "missing", "pink", warnings::add));
        assertEquals(List.of("missing"), warnings);
        assertEquals(ShipVisualEvent.SIT, ShipVisualEvent.locomotion(true, true));
        assertEquals(ShipVisualEvent.MOVE, ShipVisualEvent.locomotion(false, true));
        assertEquals(ShipVisualEvent.IDLE, ShipVisualEvent.locomotion(false, false));
        assertEquals(ShipVisualEvent.LIGHT_ATTACK, ShipVisualEvent.attack(1));
        assertEquals(ShipVisualEvent.HEAVY_ATTACK, ShipVisualEvent.attack(2));
        assertEquals(ShipVisualEvent.LIGHT_ATTACK, ShipVisualEvent.attack(0));
    }
}
