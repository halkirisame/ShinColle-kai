package com.lulan.shincolle.compat.jei;

import com.lulan.shincolle.crafting.RecipeEggDismantle;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Displays safe egg recipes without changing their crafting predicates. */
public final class EggDismantleCraftingExtension implements ICraftingCategoryExtension {

    private final RecipeEggDismantle recipe;

    public EggDismantleCraftingExtension(RecipeEggDismantle recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper,
                          IFocusGroup focuses) {
        ShipSpawnEgg eggItem = (ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get();
        List<ItemStack> eggs = switch (recipe.size()) {
            case SMALL -> List.of(eggItem.createConstructionStack(0));
            case LARGE -> List.of(eggItem.createConstructionStack(1));
            case PLAIN -> List.of(eggItem.createStack(ID.ShipClass.BBRE),
                    eggItem.createStack(ID.ShipClass.DDHibiki),
                    eggItem.createStack(ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.DDShimakaze));
        };
        craftingGridHelper.createAndSetInputs(builder,
                List.of(eggs, List.of(new ItemStack(ModItems.KAITAI_HAMMER.get()))), 0, 0);
        craftingGridHelper.createAndSetOutputs(builder, List.of(recipe.getResultItem(RegistryAccess.EMPTY)));
    }

    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getId();
    }
}
