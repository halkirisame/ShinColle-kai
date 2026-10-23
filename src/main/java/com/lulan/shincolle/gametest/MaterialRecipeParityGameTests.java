package com.lulan.shincolle.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The polymetal nugget round trip and the metal ammunition recipes. The six extra metals have no
 * ingredient of their own: their recipes read a shared ingot tag and load only while that tag has
 * members, so a pack without such a metal simply never sees them.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialRecipeParityGameTests {

    /** Metal, light ammunition count, heavy ammunition count. */
    private static final Object[][] EXTRA_METALS = {
            {"tin", 8, 1}, {"lead", 16, 2}, {"bronze", 8, 1},
            {"steel", 32, 4}, {"silver", 32, 4}, {"uranium", 64, 8}};

    /** Recipe, tag the ingredient must read, ingredient item before the tag. */
    private static final String[][] RETAGGED = {
            {"abyssium_from_iron", "forge:ingots/iron"},
            {"recipe_paper", "forge:gems/lapis"},
            {"ammo_from_copper", "forge:ingots/copper"},
            {"ammo_from_iron", "forge:ingots/iron"},
            {"ammo_from_gold", "forge:ingots/gold"},
            {"ammo_from_diamond", "forge:gems/diamond"},
            {"heavy_ammo_from_copper", "forge:ingots/copper"},
            {"heavy_ammo_from_iron", "forge:ingots/iron"},
            {"heavy_ammo_from_gold", "forge:ingots/gold"},
            {"heavy_ammo_from_diamond", "forge:gems/diamond"}};

    private MaterialRecipeParityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void polymetalNuggetsRoundTripWithoutGain(GameTestHelper helper) {
        Item nodule = item("polymetal_nodule");
        Item nugget = item("abyss_nugget_1");

        CraftingContainer one = grid(3, 3, nodule);
        Optional<CraftingRecipe> split = lookup(helper, one);
        helper.assertTrue(split.isPresent() && split.get().getId().equals(id("polymetal_nuggets_from_nodule")),
                "one nodule must split into nuggets but matched " + split);
        ItemStack nuggets = split.get().assemble(one, helper.getLevel().registryAccess());
        helper.assertTrue(nuggets.getItem() == nugget && nuggets.getCount() == 9,
                "one nodule must give nine polymetal nuggets but gave " + nuggets);

        Item[] nine = new Item[9];
        java.util.Arrays.fill(nine, nugget);
        CraftingContainer nineGrid = grid(3, 3, nine);
        Optional<CraftingRecipe> join = lookup(helper, nineGrid);
        helper.assertTrue(join.isPresent() && join.get().getId().equals(id("polymetal_nodule_from_nuggets")),
                "nine nuggets must join into a nodule but matched " + join);
        ItemStack back = join.get().assemble(nineGrid, helper.getLevel().registryAccess());
        helper.assertTrue(back.getItem() == nodule && back.getCount() == 1,
                "nine nuggets must give one nodule but gave " + back);

        // Eight nuggets are not enough, and the other nugget does not stand in for them.
        Item[] eight = new Item[9];
        java.util.Arrays.fill(eight, 0, 8, nugget);
        helper.assertTrue(lookup(helper, grid(3, 3, eight)).isEmpty(), "eight nuggets must not craft anything");
        Item[] other = new Item[9];
        java.util.Arrays.fill(other, item("abyss_nugget"));
        Optional<CraftingRecipe> wrong = lookup(helper, grid(3, 3, other));
        helper.assertTrue(wrong.isEmpty() || wrong.get().getResultItem(helper.getLevel().registryAccess()).getItem() != nodule,
                "the abyss nugget must not make a nodule");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void additionalMetalRecipesUseSevenIngotsAndExpectedCounts(GameTestHelper helper) {
        for (Object[] metal : EXTRA_METALS) {
            String name = (String) metal[0];
            String tag = "forge:ingots/" + name;
            assertMetalRecipe(helper, "ammo_from_" + name, tag, "gunpowder", "ammo", (Integer) metal[1]);
            assertMetalRecipe(helper, "heavy_ammo_from_" + name, tag, "blaze_powder", "ammo_2", (Integer) metal[2]);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptyMaterialTagsDisableOnlyTheirRecipes(GameTestHelper helper) {
        // Recipes that do not depend on a supplied tag are always there.
        for (String always : new String[]{"polymetal_nuggets_from_nodule", "polymetal_nodule_from_nuggets",
                "ammo_from_abyss_metal", "heavy_ammo_from_abyss_metal", "instant_con_mat"}) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id(always)).isPresent(),
                    always + " must not depend on a material tag");
        }

        // The six extra metals: present exactly when their own tag has members.
        for (Object[] metal : EXTRA_METALS) {
            String name = (String) metal[0];
            boolean supplied = !tagItems(tag("forge:ingots/" + name)).isEmpty();
            for (String prefix : new String[]{"ammo_from_", "heavy_ammo_from_"}) {
                boolean present = helper.getLevel().getRecipeManager().byKey(id(prefix + name)).isPresent();
                helper.assertTrue(present == supplied, prefix + name + " must be "
                        + (supplied ? "loaded" : "absent") + " while its tag is " + (supplied ? "supplied" : "empty"));
            }
        }

        // Loader side, isolated from the world's tags: the condition follows the tag it names.
        for (Object[] metal : EXTRA_METALS) {
            String name = (String) metal[0];
            JsonObject json = readRecipe(helper, "ammo_from_" + name);
            ResourceLocation tagId = ResourceLocation.parse("forge:ingots/" + name);
            helper.assertTrue(!CraftingHelper.processConditions(json, "conditions", tagContext(tagId, false)),
                    name + ": an empty tag must disable the recipe");
            helper.assertTrue(CraftingHelper.processConditions(json, "conditions", tagContext(tagId, true)),
                    name + ": a supplied tag must enable the recipe");
            ResourceLocation unrelated = ResourceLocation.parse("forge:ingots/not_" + name);
            helper.assertTrue(!CraftingHelper.processConditions(json, "conditions", tagContext(unrelated, true)),
                    name + ": another tag must not enable the recipe");
        }

        // The ten existing recipes read a tag instead of one vanilla item, and every member of that
        // tag takes the vanilla item's place.
        for (String[] recipe : RETAGGED) {
            assertReadsTagOnly(helper, recipe[0], recipe[1]);
            for (Item member : tagItems(tag(recipe[1]))) {
                assertRetaggedCrafts(helper, recipe[0], member);
            }
        }
        helper.succeed();
    }

    // ===== helpers =====

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
    }

    private static Item item(String path) {
        Item found = BuiltInRegistries.ITEM.get(id(path));
        if (found == Items.AIR) {
            throw new IllegalStateException("no such item " + path);
        }
        return found;
    }

    private static Item vanilla(String path) {
        Item found = BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + path));
        if (found == Items.AIR) {
            throw new IllegalStateException("no such item " + path);
        }
        return found;
    }

    private static TagKey<Item> tag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    private static List<Item> tagItems(TagKey<Item> key) {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(key).ifPresent(named -> named.forEach(holder -> items.add(holder.value())));
        return items;
    }

    private static CraftingContainer grid(int width, int height, Item... items) {
        InventoryCraftingFake grid = new InventoryCraftingFake(width, height);
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                grid.setItem(i, new ItemStack(items[i]));
            }
        }
        return grid;
    }

    private static Optional<CraftingRecipe> lookup(GameTestHelper helper, CraftingContainer grid) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
    }

    private static JsonObject readRecipe(GameTestHelper helper, String path) {
        ResourceLocation file = id("recipes/" + path + ".json");
        try (BufferedReader reader = helper.getLevel().getServer().getResourceManager().openAsReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }

    /** A context in which exactly one item tag exists, and it holds one item or nothing. */
    private static ICondition.IContext tagContext(ResourceLocation tagId, boolean supplied) {
        return new ICondition.IContext() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry) {
                if (!registry.equals(Registries.ITEM)) {
                    return Map.of();
                }
                Holder<Item> member = BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT);
                Collection<Holder<T>> members = supplied ? (Collection<Holder<T>>) (Collection<?>) List.of(member) : List.of();
                return Map.of(tagId, members);
            }
        };
    }

    private static void assertMetalRecipe(GameTestHelper helper, String recipe, String tagId, String powder,
                                          String result, int count) {
        JsonObject json = readRecipe(helper, recipe);
        helper.assertTrue("minecraft:crafting_shaped".equals(json.get("type").getAsString()), recipe + " is shaped");
        helper.assertTrue(patternIs(json, "III", "IGI", "IPI"), recipe + " must use the 3x3 ring layout");
        JsonObject key = json.getAsJsonObject("key");
        helper.assertTrue(tagId.equals(optString(key.getAsJsonObject("I"), "tag")), recipe + " must read the tag " + tagId);
        helper.assertTrue((Reference.MOD_ID + ":grudge").equals(optString(key.getAsJsonObject("G"), "item")),
                recipe + " takes one grudge in the middle");
        helper.assertTrue(("minecraft:" + powder).equals(optString(key.getAsJsonObject("P"), "item")),
                recipe + " takes " + powder + " at the bottom");
        helper.assertTrue(Reference.MOD_ID.concat(":").concat(result).equals(json.getAsJsonObject("result").get("item").getAsString())
                && json.getAsJsonObject("result").get("count").getAsInt() == count, recipe + " must make " + count + " " + result);
        JsonArray conditions = json.getAsJsonArray("conditions");
        helper.assertTrue(conditions != null && conditions.size() == 1, recipe + " carries one load condition");
        JsonObject not = conditions.get(0).getAsJsonObject();
        helper.assertTrue("forge:not".equals(not.get("type").getAsString()), recipe + " condition is forge:not");
        JsonObject empty = not.getAsJsonObject("value");
        helper.assertTrue("forge:tag_empty".equals(empty.get("type").getAsString())
                && tagId.equals(empty.get("tag").getAsString()), recipe + " condition must test the tag " + tagId);

        List<Item> members = tagItems(tag(tagId));
        boolean loaded = helper.getLevel().getRecipeManager().byKey(id(recipe)).isPresent();
        if (members.isEmpty()) {
            helper.assertTrue(!loaded, recipe + " must not load while " + tagId + " is empty");
            return;
        }
        helper.assertTrue(loaded, recipe + " must load while " + tagId + " has members");
        // Seven ingot cells, mixing the members of the tag where there are several.
        Item[] cells = new Item[9];
        int next = 0;
        for (int i = 0; i < 9; i++) {
            cells[i] = members.get(next++ % members.size());
        }
        cells[4] = item("grudge");
        cells[7] = vanilla(powder);
        CraftingContainer grid = grid(3, 3, cells);
        Optional<CraftingRecipe> found = lookup(helper, grid);
        helper.assertTrue(found.isPresent() && found.get().getId().equals(id(recipe)),
                recipe + " must craft from the ring but matched " + found);
        ItemStack out = found.get().assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(out.getItem() == item(result) && out.getCount() == count,
                recipe + " must give " + count + " " + result + " but gave " + out);
        // Six ingots are one short.
        cells[0] = null;
        helper.assertTrue(lookup(helper, grid(3, 3, cells)).isEmpty(), recipe + " must not craft with six ingots");
    }

    private static boolean patternIs(JsonObject json, String... rows) {
        JsonArray pattern = json.getAsJsonArray("pattern");
        if (pattern == null || pattern.size() != rows.length) {
            return false;
        }
        for (int i = 0; i < rows.length; i++) {
            if (!rows[i].equals(pattern.get(i).getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static String optString(JsonObject object, String member) {
        JsonElement element = object == null ? null : object.get(member);
        return element == null ? null : element.getAsString();
    }

    /** Every ingredient of the recipe that the tag replaces names the tag, and none names the vanilla item. */
    private static void assertReadsTagOnly(GameTestHelper helper, String recipe, String tagId) {
        JsonObject json = readRecipe(helper, recipe);
        List<JsonObject> ingredients = new ArrayList<>();
        if (json.has("key")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("key").entrySet()) {
                ingredients.add(entry.getValue().getAsJsonObject());
            }
        } else {
            for (JsonElement entry : json.getAsJsonArray("ingredients")) {
                ingredients.add(entry.getAsJsonObject());
            }
        }
        boolean found = false;
        for (JsonObject ingredient : ingredients) {
            if (tagId.equals(optString(ingredient, "tag"))) {
                found = true;
            }
            String named = optString(ingredient, "item");
            helper.assertTrue(named == null || !named.startsWith("minecraft:") || !isReplacedItem(named),
                    recipe + " must read " + tagId + " instead of " + named);
        }
        helper.assertTrue(found, recipe + " must read the tag " + tagId);
    }

    private static boolean isReplacedItem(String name) {
        return name.equals("minecraft:copper_ingot") || name.equals("minecraft:iron_ingot")
                || name.equals("minecraft:gold_ingot") || name.equals("minecraft:diamond")
                || name.equals("minecraft:lapis_lazuli");
    }

    /** Crafts the recipe with one member of its tag in the tag's place. */
    private static void assertRetaggedCrafts(GameTestHelper helper, String recipe, Item member) {
        JsonObject json = readRecipe(helper, recipe);
        ResourceLocation expected = id(recipe);
        if (json.has("key")) {
            JsonObject key = json.getAsJsonObject("key");
            Item[] cells = new Item[9];
            JsonArray pattern = json.getAsJsonArray("pattern");
            for (int y = 0; y < pattern.size(); y++) {
                String row = pattern.get(y).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c == ' ') {
                        continue;
                    }
                    JsonObject ingredient = key.getAsJsonObject(String.valueOf(c));
                    cells[y * 3 + x] = ingredient.has("tag") ? member : resolve(ingredient.get("item").getAsString());
                }
            }
            CraftingContainer grid = grid(3, 3, cells);
            Optional<CraftingRecipe> found = lookup(helper, grid);
            helper.assertTrue(found.isPresent() && found.get().getId().equals(expected),
                    recipe + " must craft with " + member + " but matched " + found);
        } else {
            List<Item> cells = new ArrayList<>();
            for (JsonElement entry : json.getAsJsonArray("ingredients")) {
                JsonObject ingredient = entry.getAsJsonObject();
                cells.add(ingredient.has("tag") ? member : resolve(ingredient.get("item").getAsString()));
            }
            CraftingContainer grid = grid(3, 3, cells.toArray(new Item[0]));
            Optional<CraftingRecipe> found = lookup(helper, grid);
            helper.assertTrue(found.isPresent() && found.get().getId().equals(expected),
                    recipe + " must craft with " + member + " but matched " + found);
        }
    }

    private static Item resolve(String name) {
        Item found = BuiltInRegistries.ITEM.get(ResourceLocation.parse(name));
        if (found == Items.AIR) {
            throw new IllegalStateException("no such item " + name);
        }
        return found;
    }
}
