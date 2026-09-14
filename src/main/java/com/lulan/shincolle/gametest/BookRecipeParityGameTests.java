package com.lulan.shincolle.gametest;

import java.util.ArrayList;
import java.util.List;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BookRecipeParityGameTests {

    private BookRecipeParityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "book_recipe_parity")
    public static void bookRecipesMatchLegacyLayouts(GameTestHelper helper) {
        assertShaped(helper, "pointer", ModItems.POINTER.get(), 1,
                null, null, ModItems.GRUDGE_BLOCK_ITEM.get(),
                null, ModItems.POLYMETAL_NODULE.get(), null,
                ModItems.POLYMETAL_NODULE.get(), null, null);
        assertShaped(helper, "repair_goddess", ModItems.REPAIR_GODDESS.get(), 1,
                ModItems.GRUDGE_BLOCK_ITEM.get(), ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(),
                ModItems.GRUDGE_BLOCK_ITEM.get(), ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(),
                Items.DIAMOND_BLOCK, ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(),
                ModItems.GRUDGE_BLOCK_ITEM.get(), ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(),
                ModItems.GRUDGE_BLOCK_ITEM.get());
        assertShaped(helper, "repair_goddess_alt", ModItems.REPAIR_GODDESS.get(), 1,
                ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(), ModItems.GRUDGE_BLOCK_ITEM.get(),
                ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(), ModItems.GRUDGE_BLOCK_ITEM.get(),
                Items.DIAMOND_BLOCK, ModItems.GRUDGE_BLOCK_ITEM.get(),
                ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(), ModItems.GRUDGE_BLOCK_ITEM.get(),
                ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get());
        assertShapeless(helper, "modern_kit", ModItems.MODERN_KIT.get(), 1,
                ModItems.KAITAI_HAMMER.get(), ModItems.TARGET_WRENCH.get(),
                ModItems.GRUDGE_XP_BLOCK_ITEM.get(), ModItems.GRUDGE_XP_BLOCK_ITEM.get(),
                ModItems.GRUDGE_XP_BLOCK_ITEM.get(), ModItems.GRUDGE_XP_BLOCK_ITEM.get());
        assertShapeless(helper, "training_book", ModItems.TRAINING_BOOK.get(), 1,
                ModItems.KAITAI_HAMMER.get(), ModItems.MODERN_KIT.get(), Items.WRITABLE_BOOK,
                ModItems.GRUDGE_XP_BLOCK_ITEM.get(), ModItems.GRUDGE_XP_BLOCK_ITEM.get(),
                ModItems.GRUDGE_XP_BLOCK_ITEM.get(), ModItems.GRUDGE_XP_BLOCK_ITEM.get());
        assertShaped(helper, "grudge_1", ModItems.GRUDGE_1.get(), 1,
                Items.EXPERIENCE_BOTTLE, Items.EXPERIENCE_BOTTLE, Items.EXPERIENCE_BOTTLE,
                Items.EXPERIENCE_BOTTLE, ModItems.GRUDGE.get(), Items.EXPERIENCE_BOTTLE,
                Items.EXPERIENCE_BOTTLE, Items.EXPERIENCE_BOTTLE, Items.EXPERIENCE_BOTTLE);
        assertShaped(helper, "grudge_xp_from_grudge_1", ModItems.GRUDGE_XP_BLOCK_ITEM.get(), 1,
                ModItems.GRUDGE_1.get(), ModItems.GRUDGE_1.get(), ModItems.GRUDGE_1.get(),
                ModItems.GRUDGE_1.get(), ModItems.GRUDGE_1.get(), ModItems.GRUDGE_1.get(),
                ModItems.GRUDGE_1.get(), ModItems.GRUDGE_1.get(), ModItems.GRUDGE_1.get());
        assertShapeless(helper, "polymetal_nodule_from_toy_airplane",
                ModItems.POLYMETAL_NODULE.get(), 5, ModItems.TOY_AIRPLANE.get());
        assertMissing(helper, "grudge_xp");
        assertMissing(helper, "abyss_metal_from_toy_airplane");
        helper.succeed();
    }

    private static void assertShaped(GameTestHelper helper, String path, Item resultItem,
            int resultCount, Item... expectedIngredients) {
        Recipe<?> recipe = getRecipe(helper, path);
        if (!(recipe instanceof ShapedRecipe shaped)) {
            throw new AssertionError(recipe.getId() + " expected shaped recipe but got " + recipe.getClass());
        }
        if (shaped.getWidth() != 3 || shaped.getHeight() != 3 || expectedIngredients.length != 9) {
            throw new AssertionError(recipe.getId() + " expected a 3x3 recipe");
        }
        List<Ingredient> actualIngredients = shaped.getIngredients();
        for (int index = 0; index < expectedIngredients.length; index++) {
            assertIngredient(recipe.getId(), index, actualIngredients.get(index), expectedIngredients[index]);
        }
        assertResult(helper, recipe, resultItem, resultCount);
    }

    private static void assertShapeless(GameTestHelper helper, String path, Item resultItem,
            int resultCount, Item... expectedIngredients) {
        Recipe<?> recipe = getRecipe(helper, path);
        if (!(recipe instanceof ShapelessRecipe shapeless)) {
            throw new AssertionError(recipe.getId() + " expected shapeless recipe but got " + recipe.getClass());
        }
        List<Ingredient> unmatched = new ArrayList<>(shapeless.getIngredients());
        for (Item expected : expectedIngredients) {
            int match = findExactIngredient(unmatched, expected);
            if (match < 0) {
                throw new AssertionError(recipe.getId() + " missing ingredient " + expected);
            }
            unmatched.remove(match);
        }
        if (!unmatched.isEmpty()) {
            throw new AssertionError(recipe.getId() + " has " + unmatched.size() + " unexpected ingredients");
        }
        assertResult(helper, recipe, resultItem, resultCount);
    }

    private static Recipe<?> getRecipe(GameTestHelper helper, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
        return helper.getLevel().getRecipeManager().byKey(id)
                .orElseThrow(() -> new AssertionError("Missing recipe " + id));
    }

    private static void assertIngredient(ResourceLocation recipeId, int index,
            Ingredient actual, Item expected) {
        if (expected == null) {
            if (actual.getItems().length != 0) {
                throw new AssertionError(recipeId + " expected empty ingredient at slot " + index);
            }
        } else if (!isExactIngredient(actual, expected)) {
            throw new AssertionError(recipeId + " has unexpected ingredient at slot " + index);
        }
    }

    private static int findExactIngredient(List<Ingredient> ingredients, Item expected) {
        for (int index = 0; index < ingredients.size(); index++) {
            if (isExactIngredient(ingredients.get(index), expected)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isExactIngredient(Ingredient ingredient, Item expected) {
        ItemStack[] stacks = ingredient.getItems();
        return stacks.length == 1 && stacks[0].is(expected);
    }

    private static void assertResult(GameTestHelper helper, Recipe<?> recipe,
            Item expectedItem, int expectedCount) {
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        if (!result.is(expectedItem) || result.getCount() != expectedCount) {
            throw new AssertionError(recipe.getId() + " expected result x" + expectedCount + " but got " + result);
        }
    }

    private static void assertMissing(GameTestHelper helper, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
        if (helper.getLevel().getRecipeManager().byKey(id).isPresent()) {
            throw new AssertionError("Unexpected recipe " + id);
        }
    }
}
