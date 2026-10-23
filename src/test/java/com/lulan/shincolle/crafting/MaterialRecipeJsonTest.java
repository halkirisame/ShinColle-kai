package com.lulan.shincolle.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The material recipe files against expectations written out here, not derived from the files.
 */
class MaterialRecipeJsonTest {

    private static final String MOD = "shincolle_kai:";

    /** metal, light count, heavy count. */
    private static final Object[][] EXTRA_METALS = {
            {"tin", 8, 1}, {"lead", 16, 2}, {"bronze", 8, 1},
            {"steel", 32, 4}, {"silver", 32, 4}, {"uranium", 64, 8}};

    /** recipe, ingredient tag, result item, result count. The ring recipes are shaped; the paper and metal are shapeless. */
    private static final Object[][] RETAGGED_RING = {
            {"ammo_from_copper", "forge:ingots/copper", "ammo", 8},
            {"ammo_from_iron", "forge:ingots/iron", "ammo", 16},
            {"ammo_from_gold", "forge:ingots/gold", "ammo", 32},
            {"ammo_from_diamond", "forge:gems/diamond", "ammo", 64},
            {"heavy_ammo_from_copper", "forge:ingots/copper", "ammo_2", 1},
            {"heavy_ammo_from_iron", "forge:ingots/iron", "ammo_2", 2},
            {"heavy_ammo_from_gold", "forge:ingots/gold", "ammo_2", 4},
            {"heavy_ammo_from_diamond", "forge:gems/diamond", "ammo_2", 8}};

    private static JsonObject read(String name) throws IOException {
        try (InputStream in = MaterialRecipeJsonTest.class.getResourceAsStream("/data/shincolle_kai/recipes/" + name + ".json")) {
            assertNotNull(in, "missing recipe " + name);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static void assertResult(JsonObject json, String item, int count) {
        assertEquals(MOD + item, json.getAsJsonObject("result").get("item").getAsString());
        assertEquals(count, json.getAsJsonObject("result").get("count").getAsInt());
    }

    private static void assertRing(JsonObject json, String powder) {
        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        JsonArray pattern = json.getAsJsonArray("pattern");
        assertEquals(List.of("III", "IGI", "IPI"),
                List.of(pattern.get(0).getAsString(), pattern.get(1).getAsString(), pattern.get(2).getAsString()));
        JsonObject key = json.getAsJsonObject("key");
        assertEquals(3, key.size());
        assertEquals(MOD + "grudge", key.getAsJsonObject("G").get("item").getAsString());
        assertEquals("minecraft:" + powder, key.getAsJsonObject("P").get("item").getAsString());
    }

    @Test
    void nuggetRoundTripIsNineForOne() throws IOException {
        JsonObject split = read("polymetal_nuggets_from_nodule");
        assertEquals("minecraft:crafting_shapeless", split.get("type").getAsString());
        JsonArray ingredients = split.getAsJsonArray("ingredients");
        assertEquals(1, ingredients.size());
        assertEquals(MOD + "polymetal_nodule", ingredients.get(0).getAsJsonObject().get("item").getAsString());
        assertResult(split, "abyss_nugget_1", 9);

        JsonObject join = read("polymetal_nodule_from_nuggets");
        assertEquals("minecraft:crafting_shaped", join.get("type").getAsString());
        JsonArray pattern = join.getAsJsonArray("pattern");
        assertEquals(3, pattern.size());
        for (JsonElement row : pattern) {
            assertEquals("NNN", row.getAsString());
        }
        assertEquals(MOD + "abyss_nugget_1", join.getAsJsonObject("key").getAsJsonObject("N").get("item").getAsString());
        assertResult(join, "polymetal_nodule", 1);
    }

    @Test
    void extraMetalAmmunitionReadsItsOwnTagWithAMatchingCondition() throws IOException {
        for (Object[] metal : EXTRA_METALS) {
            String tag = "forge:ingots/" + metal[0];
            for (boolean heavy : new boolean[]{false, true}) {
                JsonObject json = read((heavy ? "heavy_ammo_from_" : "ammo_from_") + metal[0]);
                assertRing(json, heavy ? "blaze_powder" : "gunpowder");
                assertEquals(tag, json.getAsJsonObject("key").getAsJsonObject("I").get("tag").getAsString());
                assertNull(json.getAsJsonObject("key").getAsJsonObject("I").get("item"));
                assertResult(json, heavy ? "ammo_2" : "ammo", (Integer) metal[heavy ? 2 : 1]);
                JsonArray conditions = json.getAsJsonArray("conditions");
                assertEquals(1, conditions.size());
                JsonObject not = conditions.get(0).getAsJsonObject();
                assertEquals("forge:not", not.get("type").getAsString());
                assertEquals("forge:tag_empty", not.getAsJsonObject("value").get("type").getAsString());
                assertEquals(tag, not.getAsJsonObject("value").get("tag").getAsString());
            }
        }
    }

    @Test
    void existingRingRecipesReadTagsAndKeepTheirCounts() throws IOException {
        for (Object[] recipe : RETAGGED_RING) {
            JsonObject json = read((String) recipe[0]);
            assertRing(json, ((String) recipe[0]).startsWith("heavy") ? "blaze_powder" : "gunpowder");
            JsonObject ingot = json.getAsJsonObject("key").getAsJsonObject("I");
            assertEquals(recipe[1], ingot.get("tag").getAsString());
            assertNull(ingot.get("item"));
            assertResult(json, (String) recipe[2], (Integer) recipe[3]);
            // A tag that already exists in the game needs no load condition.
            assertFalse(json.has("conditions"));
        }
    }

    @Test
    void existingShapelessRecipesReadTagsAndKeepTheirResults() throws IOException {
        JsonObject metal = read("abyssium_from_iron");
        JsonArray metalIngredients = metal.getAsJsonArray("ingredients");
        assertEquals(2, metalIngredients.size());
        assertEquals("forge:ingots/iron", metalIngredients.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals(MOD + "grudge", metalIngredients.get(1).getAsJsonObject().get("item").getAsString());
        assertResult(metal, "abyss_metal", 1);

        JsonObject paper = read("recipe_paper");
        JsonArray paperIngredients = paper.getAsJsonArray("ingredients");
        assertEquals(3, paperIngredients.size());
        assertEquals(MOD + "grudge", paperIngredients.get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:paper", paperIngredients.get(1).getAsJsonObject().get("item").getAsString());
        assertEquals("forge:gems/lapis", paperIngredients.get(2).getAsJsonObject().get("tag").getAsString());
        assertResult(paper, "recipe_paper", 1);
    }

    @Test
    void noTagFileIsShippedForTheSuppliedMetals() {
        for (Object[] metal : EXTRA_METALS) {
            for (String namespace : new String[]{"forge", "shincolle_kai"}) {
                assertFalse(Files.exists(Path.of("src/main/resources/data/" + namespace + "/tags/items/ingots/" + metal[0] + ".json")));
                assertFalse(Files.exists(Path.of("src/generated/resources/data/" + namespace + "/tags/items/ingots/" + metal[0] + ".json")));
            }
        }
    }
}
