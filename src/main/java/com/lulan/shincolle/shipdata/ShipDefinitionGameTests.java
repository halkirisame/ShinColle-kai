package com.lulan.shincolle.shipdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
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

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void selectableBehaviorIdsAreAccepted(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation("shincolle_test", "selected_behavior");
        List<ResourceLocation> attacks = List.of(new ResourceLocation(Reference.MOD_ID, "light_cannon"),
                new ResourceLocation(Reference.MOD_ID, "heavy_cannon"), new ResourceLocation(Reference.MOD_ID, "melee"));
        JsonObject json = withAttacks(attacks.stream().map(ResourceLocation::toString).toArray(String[]::new));
        json.getAsJsonObject("behavior").addProperty("ai", "shincolle_kai:standard");
        Map<ShipDefinitionId, ShipDefinition> parsed = new ShipDefinitionLoader().parseDefinitions(Map.of(id, json));
        ShipDefinition definition = parsed.get(new ShipDefinitionId(id));
        helper.assertTrue(definition != null, "A definition selecting built-in behavior IDs was rejected");
        helper.assertTrue(definition.behavior().ai().orElseThrow().equals(new ResourceLocation(Reference.MOD_ID, "standard")),
                "The selected AI did not round-trip");
        helper.assertTrue(definition.behavior().attacks().equals(attacks), "Selected attacks did not round-trip");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unselectableSkillRejectsOnlyItsDefinition(GameTestHelper helper) {
        ResourceLocation good = new ResourceLocation("shincolle_test", "good");
        ResourceLocation tenryuu = new ResourceLocation("shincolle_test", "tenryuu");
        ResourceLocation tatsuta = new ResourceLocation("shincolle_test", "tatsuta");
        ResourceLocation nagato = new ResourceLocation("shincolle_test", "nagato");
        ResourceLocation aircraft = new ResourceLocation("shincolle_test", "aircraft");
        Map<ShipDefinitionId, ShipDefinition> parsed = new ShipDefinitionLoader().parseDefinitions(Map.of(
                good, minimum(), tenryuu, withAttacks("shincolle_kai:tenryuu_skill"),
                tatsuta, withAttacks("shincolle_kai:tatsuta_skill"), nagato, withAttacks("shincolle_kai:nagato_skill"),
                aircraft, withAttacks("shincolle_kai:light_cannon", "shincolle_kai:aircraft")));
        helper.assertTrue(parsed.size() == 1 && parsed.containsKey(new ShipDefinitionId(good)),
                "An unselectable attack was accepted or rejected a good neighbor");
        ShipDefinitionValidator validator = new ShipDefinitionValidator();
        for (String attack : List.of("tenryuu_skill", "tatsuta_skill", "nagato_skill", "aircraft")) {
            try {
                validator.validate(new ShipDefinitionId(nagato), withAttacks("shincolle_kai:" + attack), ignored -> { });
                helper.fail("An unselectable attack was accepted: " + attack);
            } catch (IllegalArgumentException exception) {
                helper.assertTrue(exception.getMessage().equals(
                                "attack 'shincolle_kai:" + attack + "' is registered but cannot be selected in 1.0"),
                        "The rejection did not distinguish a registered but unselectable attack: " + exception.getMessage());
            }
        }
        try {
            validator.validate(new ShipDefinitionId(good), withAttacks("shincolle_kai:missing"), ignored -> { });
            helper.fail("An unknown attack was accepted");
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage().equals("unknown or unselectable attack 'shincolle_kai:missing'"),
                    "An unknown attack was reported as registered: " + exception.getMessage());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptyAttacksRejectsOnlyItsDefinition(GameTestHelper helper) {
        ResourceLocation good = new ResourceLocation("shincolle_test", "good");
        ResourceLocation emptyAttacks = new ResourceLocation("shincolle_test", "empty_attacks");
        ResourceLocation emptyEquipment = new ResourceLocation("shincolle_test", "empty_equipment");
        JsonObject equipment = minimum();
        JsonObject settings = new JsonObject();
        settings.add("initial", new JsonArray());
        equipment.add("equipment", settings);
        Map<ShipDefinitionId, ShipDefinition> parsed = new ShipDefinitionLoader().parseDefinitions(Map.of(
                good, minimum(), emptyAttacks, withAttacks(), emptyEquipment, equipment));
        helper.assertTrue(parsed.size() == 2 && parsed.containsKey(new ShipDefinitionId(good))
                        && parsed.containsKey(new ShipDefinitionId(emptyEquipment)),
                "Empty attacks were accepted or rejected a good neighbor or empty equipment");
        helper.assertTrue(parsed.get(new ShipDefinitionId(emptyEquipment)).equipment().initial().isEmpty(),
                "Empty equipment did not round-trip");
        try {
            new ShipDefinitionValidator().validate(new ShipDefinitionId(emptyAttacks), withAttacks(), ignored -> { });
            helper.fail("Empty attacks were accepted");
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage().equals(
                            "behavior.attacks must not be empty; omit it for the default attacks"),
                    "Empty attacks were rejected for an unexpected reason: " + exception.getMessage());
        }
        helper.succeed();
    }

    private static JsonObject withAttacks(String... ids) {
        JsonObject json = minimum();
        JsonObject behavior = new JsonObject();
        JsonArray attacks = new JsonArray();
        for (String id : ids) {
            attacks.add(id);
        }
        behavior.add("attacks", attacks);
        json.add("behavior", behavior);
        return json;
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
