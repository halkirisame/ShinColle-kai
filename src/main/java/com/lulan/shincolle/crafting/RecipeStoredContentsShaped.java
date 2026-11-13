package com.lulan.shincolle.crafting;

import com.google.gson.JsonObject;
import com.lulan.shincolle.init.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Ordinary shaped layouts with stored-core protection and tank fluid inheritance. */
public final class RecipeStoredContentsShaped extends ShapedRecipe {
    private RecipeStoredContentsShaped(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
                recipe.getIngredients(), recipe.getResultItem(RegistryAccess.EMPTY), recipe.showNotification());
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
        return ModRecipes.STORED_CONTENTS_SHAPED.get();
    }

    public static final class Serializer implements RecipeSerializer<RecipeStoredContentsShaped> {
        @Override
        public RecipeStoredContentsShaped fromJson(ResourceLocation id, JsonObject json) {
            return new RecipeStoredContentsShaped(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public RecipeStoredContentsShaped fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new RecipeStoredContentsShaped(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RecipeStoredContentsShaped recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
