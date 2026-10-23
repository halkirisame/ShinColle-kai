package com.lulan.shincolle.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.lulan.shincolle.crafting.EggDismantleRules.EggDismantleKind;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.init.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Dismantles a construction egg with a kaitai hammer into instant construction material.
 * <p>
 * Exactly one egg and one hammer, nothing else, anywhere in the grid. The egg must be a plain
 * construction egg of this recipe's size (see {@link EggDismantleRules}); a saved ship egg, an egg for
 * a specific ship or anything malformed never matches. {@link #matches} and {@link #assemble} only read
 * the inputs. The hammer wears down through its own crafting remainder.
 */
public class RecipeEggDismantle implements CraftingRecipe {

    /** Sizes this recipe type can be declared for. */
    public enum Size {
        SMALL("small", EggDismantleKind.SMALL),
        LARGE("large", EggDismantleKind.LARGE);

        private final String jsonName;
        private final EggDismantleKind kind;

        Size(String jsonName, EggDismantleKind kind) {
            this.jsonName = jsonName;
            this.kind = kind;
        }

        public EggDismantleKind kind() {
            return this.kind;
        }

        static Size byJsonName(String name) {
            for (Size size : values()) {
                if (size.jsonName.equals(name)) {
                    return size;
                }
            }
            return null;
        }
    }

    private final ResourceLocation id;
    private final CraftingBookCategory category;
    private final Size size;

    public RecipeEggDismantle(ResourceLocation id, CraftingBookCategory category, Size size) {
        this.id = id;
        this.category = category;
        this.size = size;
    }

    public Size size() {
        return this.size;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return resultFor(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = resultFor(container);
        return result == null ? ItemStack.EMPTY : result;
    }

    /** The material this grid yields, or null when the grid is not exactly one acceptable egg and one hammer. */
    private ItemStack resultFor(CraftingContainer container) {
        ItemStack egg = ItemStack.EMPTY;
        int hammers = 0;
        int eggs = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() != 1) {
                return null;
            }
            if (stack.getItem() == ModItems.KAITAI_HAMMER.get()) {
                hammers++;
            } else if (EggDismantleNbtAdapter.isShipEgg(stack)) {
                eggs++;
                egg = stack;
            } else {
                return null;
            }
        }
        if (eggs != 1 || hammers != 1) {
            return null;
        }
        EggDismantleKind kind = EggDismantleRules.classify(EggDismantleNbtAdapter.read(egg));
        if (kind != this.size.kind()) {
            return null;
        }
        return new ItemStack(ModItems.INSTANT_CON_MAT.get(), kind.yield());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(ModItems.INSTANT_CON_MAT.get(), this.size.kind().yield());
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.EGG_DISMANTLE.get();
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    // ========== Serializer ==========

    public static class Serializer implements RecipeSerializer<RecipeEggDismantle> {

        @Override
        public RecipeEggDismantle fromJson(ResourceLocation id, JsonObject json) {
            CraftingBookCategory category = CraftingBookCategory.CODEC.byName(
                    GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
            String name = GsonHelper.getAsString(json, "build_type");
            Size size = Size.byJsonName(name);
            if (size == null) {
                throw new JsonSyntaxException("Unknown build_type '" + name + "', expected small or large");
            }
            return new RecipeEggDismantle(id, category, size);
        }

        @Override
        public RecipeEggDismantle fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
            Size size = buf.readEnum(Size.class);
            return new RecipeEggDismantle(id, category, size);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, RecipeEggDismantle recipe) {
            buf.writeEnum(recipe.category());
            buf.writeEnum(recipe.size());
        }
    }
}
