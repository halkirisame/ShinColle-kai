package com.lulan.shincolle.shipdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistries;
import com.lulan.shincolle.ai.registry.ShipTraitRegistries;
import com.lulan.shincolle.init.ModShipBehaviors;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPublicationGameTests {
    private static final Path PUBLICATION_ROOT = Path.of(
            System.getProperty("shincolle.publicationRoot", ".")).toAbsolutePath().normalize();
    private static final Path SCHEMA = PUBLICATION_ROOT.resolve("docs/schemas/shincolle_kai-ship_definition.schema.json");

    private ShipPublicationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void publishedSchemaListsSelectableBehaviorIds(GameTestHelper helper) {
        JsonElement schema;
        try {
            schema = JsonParser.parseString(Files.readString(SCHEMA));
        } catch (IOException | JsonParseException exception) {
            helper.fail("Cannot read published ship schema " + SCHEMA + ": " + exception.getMessage());
            return;
        }
        helper.assertTrue(schema.isJsonObject(), "Published ship schema must be an object: " + SCHEMA);
        JsonObject definitions = requiredObject(helper, schema.getAsJsonObject(), "$defs");
        JsonObject ai = requiredObject(helper, definitions, "shipAiId");
        JsonObject attack = requiredObject(helper, definitions, "shipAttackId");

        var aiRegistry = ModShipBehaviors.AI_REGISTRY.get();
        var attackRegistry = ModShipBehaviors.ATTACK_REGISTRY.get();
        helper.assertTrue(aiRegistry != null && attackRegistry != null, "Behavior registries must exist before publication checks");
        Set<String> selectableAi = new TreeSet<>();
        Set<String> selectableAttacks = new TreeSet<>();
        Set<String> reservedAttacks = new TreeSet<>();
        for (var id : aiRegistry.getKeys()) {
            if (Reference.MOD_ID.equals(id.getNamespace()) && ShipBehaviorRegistries.isSelectableAi(id)) {
                selectableAi.add(id.toString());
            }
        }
        for (var id : attackRegistry.getKeys()) {
            if (!Reference.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            if (ShipBehaviorRegistries.isSelectableAttack(id)) {
                selectableAttacks.add(id.toString());
            } else {
                reservedAttacks.add(id.toString());
            }
        }
        assertSameIds(helper, selectableAi, enumIds(helper, ai, "shipAiId"), "shipAiId");
        assertSameIds(helper, selectableAttacks, enumIds(helper, attack, "shipAttackId"), "shipAttackId");
        JsonElement description = attack.get("description");
        helper.assertTrue(description != null && description.isJsonPrimitive() && description.getAsJsonPrimitive().isString(),
                "shipAttackId.description must be a string");
        for (String id : reservedAttacks) {
            helper.assertTrue(description.getAsString().contains(id), "shipAttackId.description is missing reserved ID " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void publishedSchemaListsSelectableTraitIds(GameTestHelper helper) {
        JsonObject schema;
        try {
            schema = JsonParser.parseString(Files.readString(SCHEMA)).getAsJsonObject();
        } catch (IOException | JsonParseException exception) {
            helper.fail("Cannot read published ship schema: " + exception.getMessage());
            return;
        }
        var registry = ModShipBehaviors.TRAIT_REGISTRY.get();
        helper.assertTrue(registry != null, "Trait registry must exist before publication checks");
        Set<String> selectable = new TreeSet<>();
        for (var id : registry.getKeys()) {
            if (ShipTraitRegistries.isSelectableTrait(id)) selectable.add(id.toString());
        }
        JsonObject traits = requiredObject(helper, requiredObject(helper, schema, "$defs"), "shipTraitId");
        assertSameIds(helper, selectable, enumIds(helper, traits, "shipTraitId"), "shipTraitId");
        helper.succeed();
    }

    private static JsonObject requiredObject(GameTestHelper helper, JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        helper.assertTrue(value != null && value.isJsonObject(), "Published ship schema is missing object " + key);
        return value.getAsJsonObject();
    }

    private static Set<String> enumIds(GameTestHelper helper, JsonObject definition, String name) {
        JsonElement value = definition.get("enum");
        helper.assertTrue(value != null && value.isJsonArray(), name + ".enum must be an array");
        JsonArray ids = value.getAsJsonArray();
        Set<String> result = new TreeSet<>();
        for (JsonElement id : ids) {
            helper.assertTrue(id.isJsonPrimitive() && id.getAsJsonPrimitive().isString(), name + ".enum has a non-string ID: " + id);
            result.add(id.getAsString());
        }
        return result;
    }

    private static void assertSameIds(GameTestHelper helper, Set<String> expected, Set<String> actual, String name) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> unexpected = new TreeSet<>(actual);
        unexpected.removeAll(expected);
        helper.assertTrue(expected.equals(actual), name + ".enum differs from selectable registry IDs: missing="
                + missing + ", unexpected=" + unexpected);
    }
}
