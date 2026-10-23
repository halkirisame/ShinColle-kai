package com.lulan.shincolle.shipdata;

import com.google.gson.JsonObject;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipDefinitionGameTests {
    private ShipDefinitionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void sampleShipLoadsFromTestResourcesIntoServerSnapshot(GameTestHelper helper) {
        ShipDefinitionRegistry registry = ShipDefinitionRegistry.server();
        ShipDefinitionId id = ShipDefinitionId.parse("shincolle_test:sample_ship");
        helper.assertTrue(registry.generation() > 0, "ship reload listener did not publish a generation");
        ShipDefinition definition = registry.get(id);
        helper.assertTrue(definition != null, "test-resource ship is missing from server snapshot");
        helper.assertTrue(definition.id().equals(id), "file namespace was not retained");
        helper.assertTrue(definition.visual().equals(id.location()), "visual ID did not round-trip");
        helper.assertTrue(definition.behavior().ai().isEmpty() && definition.behavior().attacks().isEmpty(),
                "omitted behavior must leave explicit selections empty");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unknownAndForeignBehaviorRejectOnlyTheirOwnDefinition(GameTestHelper helper) {
        ResourceLocation good = new ResourceLocation("shincolle_test", "good");
        ResourceLocation badAi = new ResourceLocation("shincolle_test", "bad_ai");
        ResourceLocation badAttack = new ResourceLocation("shincolle_test", "bad_attack");
        JsonObject ai = minimum();
        JsonObject aiBehavior = new JsonObject();
        aiBehavior.addProperty("ai", "shincolle_kai:missing");
        ai.add("behavior", aiBehavior);
        JsonObject attack = minimum();
        JsonObject attackBehavior = new JsonObject();
        com.google.gson.JsonArray attacks = new com.google.gson.JsonArray();
        attacks.add("some_addon:attack");
        attackBehavior.add("attacks", attacks);
        attack.add("behavior", attackBehavior);
        Map<ShipDefinitionId, ShipDefinition> parsed = new ShipDefinitionLoader().parseDefinitions(
                Map.of(good, minimum(), badAi, ai, badAttack, attack));
        helper.assertTrue(parsed.size() == 1 && parsed.containsKey(new ShipDefinitionId(good)),
                "invalid behavior rejected a good neighbor or was accepted");
        helper.succeed();
    }

    private static JsonObject minimum() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", ShipDefinitionFormat.SCHEMA);
        json.addProperty("ship_type", "shincolle_kai:destroyer");
        json.addProperty("visual", "shincolle_test:sample_ship");
        json.add("base_stats", new JsonObject());
        return json;
    }
}
