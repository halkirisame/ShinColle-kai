package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyAmmoRecipeGameTests {

    private HeavyAmmoRecipeGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "heavy_ammo_recipe")
    public static void heavyAmmoRecipesLoadWithLegacyCounts(GameTestHelper helper) {
        assertRecipe(helper, "heavy_ammo_from_copper", 1);
        assertRecipe(helper, "heavy_ammo_from_iron", 2);
        assertRecipe(helper, "heavy_ammo_from_abyss_metal", 4);
        assertRecipe(helper, "heavy_ammo_from_gold", 4);
        assertRecipe(helper, "heavy_ammo_from_diamond", 8);
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, String path, int expectedCount) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id)
                .orElseThrow(() -> new AssertionError("Missing recipe " + id));
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        if (!result.is(ModItems.AMMO_2.get()) || result.getCount() != expectedCount) {
            throw new AssertionError(id + " expected ammo_2 x" + expectedCount + " but got " + result);
        }
    }
}
