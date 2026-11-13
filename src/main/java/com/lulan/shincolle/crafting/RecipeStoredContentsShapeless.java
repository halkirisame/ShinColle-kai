package com.lulan.shincolle.crafting;

import com.google.gson.JsonObject;
import com.lulan.shincolle.init.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/** Ordinary shapeless layouts that do not consume a core's stored contents. */
public final class RecipeStoredContentsShapeless extends ShapelessRecipe {
    private RecipeStoredContentsShapeless(ShapelessRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return super.matches(grid, level) && !assemble(grid, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        return StoredContentsCrafting.assemble(grid, super.assemble(grid, registries));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.STORED_CONTENTS_SHAPELESS.get();
    }

    public static final class Serializer implements RecipeSerializer<RecipeStoredContentsShapeless> {
        @Override
        public RecipeStoredContentsShapeless fromJson(ResourceLocation id, JsonObject json) {
            return new RecipeStoredContentsShapeless(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json));
        }

        @Override
        public RecipeStoredContentsShapeless fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new RecipeStoredContentsShapeless(RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RecipeStoredContentsShapeless recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
