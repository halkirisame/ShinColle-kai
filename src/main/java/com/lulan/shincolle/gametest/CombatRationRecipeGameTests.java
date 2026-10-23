package com.lulan.shincolle.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.CombatRation;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.InteractHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The combat ration recipes: the six-wheat layout, the cooked-meat type, the optional cooked rice and
 * flour routes, the upgraded rations and the ice cream. Rice and flour have no ingredient of their own
 * in the game: their recipes read a tag of this mod and load only while that tag has members.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatRationRecipeGameTests {

    private static final String[] MEATS = {"beef", "chicken", "cod", "mutton", "porkchop", "rabbit"};
    private static final String RICE_TAG = Reference.MOD_ID + ":cooked_rice";
    private static final String FLOUR_TAG = Reference.MOD_ID + ":flour";

    private CombatRationRecipeGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void wheatRationsRequireSixWheatAndOriginalShape(GameTestHelper helper) {
        Item wheat = Items.WHEAT;
        Item carrot = Items.GOLDEN_CARROT;
        Item grudge = ModItems.GRUDGE.get();
        List<String[]> routes = new ArrayList<>();
        routes.add(new String[]{"bread", "combat_ration"});
        for (String meat : MEATS) {
            routes.add(new String[]{"cooked_" + meat, "combat_ration_from_" + meat});
        }

        for (String[] route : routes) {
            Item base = vanilla(route[0]);
            ResourceLocation recipeId = id(route[1]);
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> registered = helper.getLevel().getRecipeManager().byKey(recipeId);
            helper.assertTrue(registered.isPresent() && registered.get() instanceof ShapedRecipe,
                    route[1] + " must be a shaped recipe but was " + registered);

            CraftingContainer grid = grid(3, 3, wheat, wheat, wheat, base, grudge, carrot, wheat, wheat, wheat);
            Optional<CraftingRecipe> found = lookup(helper, grid);
            helper.assertTrue(found.isPresent() && found.get().getId().equals(recipeId),
                    route[1] + " must craft from six wheat in the original layout but matched " + found);
            ItemStack made = found.get().assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(made.getCount() == 1 && !made.hasTag(), route[1] + " must give one plain ration but gave " + made);

            // The mirrored layout is the same recipe.
            CraftingContainer mirror = grid(3, 3, wheat, wheat, wheat, carrot, grudge, base, wheat, wheat, wheat);
            Optional<CraftingRecipe> mirrored = lookup(helper, mirror);
            helper.assertTrue(mirrored.isPresent() && mirrored.get().getId().equals(recipeId),
                    route[1] + " must also craft mirrored but matched " + mirrored);

            // The cheap one-wheat shapeless recipe is gone, and so is every short or scrambled layout.
            assertNoRation(helper, grid(3, 3, base, carrot, grudge, wheat), route[0] + " + 1 wheat");
            assertNoRation(helper, grid(3, 3, wheat, wheat, wheat, base, grudge, carrot, wheat, wheat, null),
                    route[0] + " with five wheat");
            assertNoRation(helper, grid(3, 3, grudge, wheat, wheat, base, wheat, carrot, wheat, wheat, wheat),
                    route[0] + " scrambled");
            assertNoRation(helper, grid(3, 3, wheat, wheat, wheat, wheat, grudge, carrot, base, wheat, wheat),
                    route[0] + " with the base item in a wheat cell");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void cookedMeatRationsProduceMeatType(GameTestHelper helper) {
        Item wheat = Items.WHEAT;
        Item carrot = Items.GOLDEN_CARROT;
        Item grudge = ModItems.GRUDGE.get();
        for (String meat : MEATS) {
            CraftingContainer grid = grid(3, 3, wheat, wheat, wheat, vanilla("cooked_" + meat), grudge, carrot, wheat, wheat, wheat);
            Optional<CraftingRecipe> found = lookup(helper, grid);
            helper.assertTrue(found.isPresent(), "cooked " + meat + " must make a ration");
            ItemStack made = found.get().assemble(grid, helper.getLevel().registryAccess());
            assertRation(helper, made, ModItems.COMBAT_RATION_2.get(), 2, 1200.0F, 1600, "cooked " + meat);
        }

        // Bread keeps the plain ration.
        CraftingContainer bread = grid(3, 3, wheat, wheat, wheat, Items.BREAD, grudge, carrot, wheat, wheat, wheat);
        Optional<CraftingRecipe> plain = lookup(helper, bread);
        helper.assertTrue(plain.isPresent(), "bread must make a ration");
        assertRation(helper, plain.get().assemble(bread, helper.getLevel().registryAccess()),
                ModItems.COMBAT_RATION.get(), 0, 900.0F, 1400, "bread");

        // Raw meat and the other cooked fish are not rations.
        for (Item rejected : new Item[]{Items.BEEF, Items.CHICKEN, Items.COD, Items.MUTTON, Items.PORKCHOP,
                Items.RABBIT, Items.COOKED_SALMON, Items.SALMON}) {
            assertNoRation(helper, grid(3, 3, wheat, wheat, wheat, rejected, grudge, carrot, wheat, wheat, wheat),
                    rejected + " as the base");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void riceAndFlourRoutesRespectResolvedTags(GameTestHelper helper) {
        List<Item> riceMembers = tagItems(tag(RICE_TAG));
        List<Item> flourMembers = tagItems(tag(FLOUR_TAG));

        List<String[]> routes = new ArrayList<>();
        routes.add(new String[]{"bread", ""});
        for (String meat : MEATS) {
            routes.add(new String[]{"cooked_" + meat, "_" + meat});
        }

        for (String[] route : routes) {
            String riceId = "combat_ration_from" + (route[1].isEmpty() ? "_rice" : route[1] + "_rice");
            String flourId = "combat_ration_from" + (route[1].isEmpty() ? "_flour" : route[1] + "_flour");
            String resultName = route[0].equals("bread") ? "combat_ration" : "combat_ration_2";

            // Rice: shapeless, the base item, a golden carrot, grudge and the tag.
            JsonObject rice = readRecipe(helper, riceId);
            helper.assertTrue("minecraft:crafting_shapeless".equals(rice.get("type").getAsString()),
                    riceId + " must be shapeless");
            helper.assertTrue(ingredientsAre(rice, "minecraft:" + route[0], "minecraft:golden_carrot",
                    Reference.MOD_ID + ":grudge", "#" + RICE_TAG), riceId + " must take the base item, a golden carrot, grudge and the rice tag");
            assertResult(helper, rice, resultName, riceId);
            assertTagCondition(helper, rice, RICE_TAG, riceId);
            assertLoadedExactlyWhenSupplied(helper, riceId, riceMembers);

            // Flour: the six-wheat layout with every wheat cell reading the flour tag.
            JsonObject flour = readRecipe(helper, flourId);
            helper.assertTrue("minecraft:crafting_shaped".equals(flour.get("type").getAsString()),
                    flourId + " must be shaped");
            helper.assertTrue(patternIs(flour, "WWW", "BGC", "WWW"), flourId + " must use the original layout");
            JsonObject key = flour.getAsJsonObject("key");
            helper.assertTrue(FLOUR_TAG.equals(optString(key.getAsJsonObject("W"), "tag")), flourId + " must read " + FLOUR_TAG);
            helper.assertTrue(("minecraft:" + route[0]).equals(optString(key.getAsJsonObject("B"), "item")),
                    flourId + " must take " + route[0] + " in the left middle cell");
            helper.assertTrue("minecraft:golden_carrot".equals(optString(key.getAsJsonObject("C"), "item"))
                    && (Reference.MOD_ID + ":grudge").equals(optString(key.getAsJsonObject("G"), "item")),
                    flourId + " must take a golden carrot and grudge");
            assertResult(helper, flour, resultName, flourId);
            assertTagCondition(helper, flour, FLOUR_TAG, flourId);
            assertLoadedExactlyWhenSupplied(helper, flourId, flourMembers);

            // Supplied tags: a member of the tag stands in, and an unrelated item does not.
            if (!riceMembers.isEmpty()) {
                CraftingContainer grid = grid(3, 3, vanilla(route[0]), Items.GOLDEN_CARROT, ModItems.GRUDGE.get(),
                        riceMembers.get(0));
                Optional<CraftingRecipe> found = lookup(helper, grid);
                helper.assertTrue(found.isPresent() && found.get().getId().equals(id(riceId)),
                        riceId + " must craft with " + riceMembers.get(0) + " but matched " + found);
                assertNoRation(helper, grid(3, 3, vanilla(route[0]), Items.GOLDEN_CARROT, ModItems.GRUDGE.get(),
                        Items.WHEAT_SEEDS), riceId + " with seeds in place of cooked rice");
            }
            if (!flourMembers.isEmpty()) {
                // Different members mixed across the six flour cells still count as flour.
                Item[] cells = new Item[9];
                int next = 0;
                for (int i = 0; i < 9; i++) {
                    cells[i] = flourMembers.get(next++ % flourMembers.size());
                }
                cells[3] = vanilla(route[0]);
                cells[4] = ModItems.GRUDGE.get();
                cells[5] = Items.GOLDEN_CARROT;
                CraftingContainer grid = grid(3, 3, cells);
                Optional<CraftingRecipe> found = lookup(helper, grid);
                helper.assertTrue(found.isPresent() && found.get().getId().equals(id(flourId)),
                        flourId + " must craft from flour but matched " + found);
            }
        }

        // Loader side, isolated from the world's tags: each condition follows its own tag only.
        for (String[] recipe : new String[][]{{"combat_ration_from_rice", RICE_TAG}, {"combat_ration_from_flour", FLOUR_TAG}}) {
            JsonObject json = readRecipe(helper, recipe[0]);
            ResourceLocation tagId = ResourceLocation.parse(recipe[1]);
            helper.assertTrue(!CraftingHelper.processConditions(json, "conditions", tagContext(tagId, false)),
                    recipe[0] + ": an empty tag must disable the recipe");
            helper.assertTrue(CraftingHelper.processConditions(json, "conditions", tagContext(tagId, true)),
                    recipe[0] + ": a supplied tag must enable the recipe");
            helper.assertTrue(!CraftingHelper.processConditions(json, "conditions",
                    tagContext(ResourceLocation.parse("forge:grains/wheat"), true)),
                    recipe[0] + ": another tag must not enable the recipe");
        }

        // Whatever the tags hold, the wheat routes never depend on them.
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("combat_ration")).isPresent(),
                "the wheat ration must not depend on a supplied tag");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void upgradedRationsRequireExactBaseType(GameTestHelper helper) {
        Item block = ModItems.GRUDGE_BLOCK_ITEM.get();
        Item gravel = ModItems.POLYMETAL_GRAVEL_BLOCK_ITEM.get();
        Item[] rations = {ModItems.COMBAT_RATION.get(), ModItems.COMBAT_RATION_1.get(), ModItems.COMBAT_RATION_2.get(),
                ModItems.COMBAT_RATION_3.get(), ModItems.COMBAT_RATION_4.get(), ModItems.COMBAT_RATION_5.get()};

        assertUpgrade(helper, "combat_ration_1", grid(3, 3, rations[0], block, gravel), rations[1], 1);
        assertUpgrade(helper, "combat_ration_3", grid(3, 3, rations[2], block, gravel), rations[3], 3);
        assertUpgrade(helper, "combat_ration_5", grid(3, 3, rations[4], block), rations[5], 5);

        // Only the exact base type upgrades, and only with its own materials.
        for (int i = 0; i < rations.length; i++) {
            if (i != 0 && i != 2) {
                assertNoRation(helper, grid(3, 3, rations[i], block, gravel), "ration " + i + " + block + gravel");
            }
            if (i != 4) {
                assertNoRation(helper, grid(3, 3, rations[i], block), "ration " + i + " + block");
            }
        }
        assertNoRation(helper, grid(3, 3, rations[0], block), "ration 0 without gravel");
        assertNoRation(helper, grid(3, 3, rations[0], gravel), "ration 0 without the block");
        assertNoRation(helper, grid(3, 3, rations[2], block, ModItems.GRUDGE.get()), "ration 2 with grudge in place of gravel");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craftedIceCreamKeepsTypeAndClearsDebuffs(GameTestHelper helper) {
        Item block = ModItems.GRUDGE_BLOCK_ITEM.get();
        CraftingContainer cream = grid(3, 3, Items.SNOWBALL, Items.SNOWBALL, Items.SNOWBALL, Items.SNOWBALL,
                Items.MILK_BUCKET, ModItems.GRUDGE.get());
        Optional<CraftingRecipe> found = lookup(helper, cream);
        helper.assertTrue(found.isPresent() && found.get().getId().equals(id("combat_ration_ice_cream")),
                "snowballs, milk and grudge must make the ice cream but matched " + found);
        ItemStack iceCream = found.get().assemble(cream, helper.getLevel().registryAccess());
        assertRation(helper, iceCream, ModItems.COMBAT_RATION_4.get(), 4, 100.0F, 3000, "ice cream");

        CraftingContainer upgrade = grid(3, 3, iceCream.getItem(), block);
        Optional<CraftingRecipe> upgraded = lookup(helper, upgrade);
        helper.assertTrue(upgraded.isPresent(), "the ice cream must upgrade with a grudge block");
        ItemStack strong = upgraded.get().assemble(upgrade, helper.getLevel().registryAccess());
        assertRation(helper, strong, ModItems.COMBAT_RATION_5.get(), 5, 900.0F, 4000, "upgraded ice cream");

        // The plain ration, made from wheat, is fed the same way and leaves debuffs alone.
        CraftingContainer wheat = grid(3, 3, Items.WHEAT, Items.WHEAT, Items.WHEAT, Items.BREAD,
                ModItems.GRUDGE.get(), Items.GOLDEN_CARROT, Items.WHEAT, Items.WHEAT, Items.WHEAT);
        Optional<CraftingRecipe> plainRecipe = lookup(helper, wheat);
        helper.assertTrue(plainRecipe.isPresent(), "plain ration recipe");
        ItemStack plain = plainRecipe.get().assemble(wheat, helper.getLevel().registryAccess());

        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            FoodShip ship = entities.add(new FoodShip(ModEntities.BB_KONGOU.get(), helper.getLevel()));
            ship.setFoodSaturationMax(100);
            ship.setFoodSaturation(0);
            ship.setStateMinor(ID.M.NumGrudge, 100);
            ship.setStateFlag(ID.F.NoFuel, false);
            helper.assertTrue(ship.getGrudge() == 100 && !ship.getStateFlag(ID.F.NoFuel),
                    "the fixture ship must hold grudge and fuel");
            FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                    UUID.fromString("00000000-0000-0000-0000-000000000296"), "ration_recipe_test"));

            Object[][] cases = {{plain, false, 900.0F}, {iceCream, true, 100.0F}, {strong, true, 900.0F}};
            for (Object[] c : cases) {
                ItemStack food = ((ItemStack) c[0]).copy();
                food.setCount(2);
                boolean clears = (Boolean) c[1];
                float base = (Float) c[2];
                ship.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
                int before = ship.getGrudge();
                helper.assertTrue(InteractHelper.interactFeed(ship, player, food) && food.getCount() == 1,
                        food.getItem() + " must be fed and consumed once");
                helper.assertTrue(ship.hasEffect(MobEffects.POISON) != clears, food.getItem()
                        + (clears ? " must clear debuffs" : " must leave debuffs"));
                float multiplier = ship.getAttrs().getAttrsBuffed(ID.Attrs.GRUDGE);
                int gain = ship.getGrudge() - before;
                helper.assertTrue(gain >= (int) (base * multiplier) && gain <= (int) (base * 2 * multiplier),
                        food.getItem() + " grudge gain " + gain + " must lie in " + (int) (base * multiplier)
                                + ".." + (int) (base * 2 * multiplier));
                ship.removeEffect(MobEffects.POISON);
            }
        }
        helper.succeed();
    }

    // ===== helpers =====

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
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

    private static void assertNoRation(GameTestHelper helper, CraftingContainer grid, String what) {
        Optional<CraftingRecipe> found = lookup(helper, grid);
        helper.assertTrue(found.isEmpty()
                        || !(found.get().getResultItem(helper.getLevel().registryAccess()).getItem() instanceof CombatRation),
                what + " must not make a ration but matched " + found);
    }

    private static void assertRation(GameTestHelper helper, ItemStack stack, Item expected, int type, float food,
                                     int morale, String what) {
        helper.assertTrue(stack.getItem() == expected && stack.getCount() == 1 && !stack.hasTag(),
                what + " must give one plain " + expected + " but gave " + stack);
        CombatRation ration = (CombatRation) stack.getItem();
        helper.assertTrue(ration.getType() == type && ration.getFoodValue(0) == food && ration.getMoraleValue(0) == morale,
                what + " must be ration type " + type + " (" + food + "/" + morale + ") but was type " + ration.getType()
                        + " (" + ration.getFoodValue(0) + "/" + ration.getMoraleValue(0) + ")");
    }

    private static void assertUpgrade(GameTestHelper helper, String recipe, CraftingContainer grid, Item result, int type) {
        Optional<CraftingRecipe> found = lookup(helper, grid);
        helper.assertTrue(found.isPresent() && found.get().getId().equals(id(recipe))
                        && found.get() instanceof ShapelessRecipe,
                recipe + " must craft shapeless but matched " + found);
        ItemStack made = found.get().assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(made.getItem() == result && made.getCount() == 1 && !made.hasTag()
                        && ((CombatRation) made.getItem()).getType() == type,
                recipe + " must give one plain ration type " + type + " but gave " + made);
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
                Holder<Item> member = BuiltInRegistries.ITEM.wrapAsHolder(Items.WHEAT);
                Collection<Holder<T>> members = supplied ? (Collection<Holder<T>>) (Collection<?>) List.of(member) : List.of();
                return Map.of(tagId, members);
            }
        };
    }

    private static void assertTagCondition(GameTestHelper helper, JsonObject json, String tagId, String recipe) {
        JsonArray conditions = json.getAsJsonArray("conditions");
        helper.assertTrue(conditions != null && conditions.size() == 1, recipe + " carries one load condition");
        JsonObject not = conditions.get(0).getAsJsonObject();
        helper.assertTrue("forge:not".equals(not.get("type").getAsString()), recipe + " condition is forge:not");
        JsonObject empty = not.getAsJsonObject("value");
        helper.assertTrue("forge:tag_empty".equals(empty.get("type").getAsString())
                && tagId.equals(empty.get("tag").getAsString()), recipe + " condition must test the tag " + tagId);
    }

    private static void assertLoadedExactlyWhenSupplied(GameTestHelper helper, String recipe, List<Item> members) {
        boolean loaded = helper.getLevel().getRecipeManager().byKey(id(recipe)).isPresent();
        helper.assertTrue(loaded == !members.isEmpty(), recipe + " must be "
                + (members.isEmpty() ? "absent while its tag is empty" : "loaded while its tag has members"));
    }

    private static void assertResult(GameTestHelper helper, JsonObject json, String result, String recipe) {
        JsonObject out = json.getAsJsonObject("result");
        helper.assertTrue(out != null && (Reference.MOD_ID + ":" + result).equals(optString(out, "item"))
                        && out.get("count").getAsInt() == 1 && !out.has("nbt"),
                recipe + " must give one plain " + result);
    }

    /** The ingredient list names exactly these entries, in any order; "#" marks a tag. */
    private static boolean ingredientsAre(JsonObject json, String... expected) {
        List<String> remaining = new ArrayList<>(List.of(expected));
        for (JsonElement entry : json.getAsJsonArray("ingredients")) {
            JsonObject ingredient = entry.getAsJsonObject();
            String name = ingredient.has("tag") ? "#" + ingredient.get("tag").getAsString() : optString(ingredient, "item");
            if (!remaining.remove(name)) {
                return false;
            }
        }
        return remaining.isEmpty();
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

    /** A battleship whose minor sync is dropped: feeding is checked on its state, not its packets. */
    private static final class FoodShip extends EntityBBKongou {
        private FoodShip(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public void sendSyncPacketMinor() {
        }
    }
}
