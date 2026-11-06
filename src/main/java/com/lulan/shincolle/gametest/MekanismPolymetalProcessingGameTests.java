package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.List;

/**
 * Polymetal ore in Mekanism's ore processing. The intermediates and their tags exist with or
 * without Mekanism; the Mekanism recipes and slurries only while Mekanism is loaded. No Mekanism
 * class is referenced here, so this holder also runs in the minimal runtime.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MekanismPolymetalProcessingGameTests {

    private static final String RECIPE_PREFIX = "mekanism/processing/polymetal/";
    private static final List<String> MEKANISM_RECIPES = List.of(
            "dust/from_ore", "dust/from_dirty_dust",
            "clump/from_ore", "clump/from_shard",
            "dirty_dust/from_clump",
            "shard/from_ore", "shard/from_crystal",
            "slurry/dirty/from_ore", "slurry/clean",
            "crystal/from_slurry");

    private MekanismPolymetalProcessingGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void polymetalProcessingItemsRegisteredWithoutMekanism(GameTestHelper helper) {
        helper.assertTrue(ModBlocks.POLYMETAL_ORE.get().defaultBlockState()
                .is(BlockTags.create(new ResourceLocation("forge", "ores/polymetal"))),
                "polymetal ore block must be in #forge:ores/polymetal");
        assertTagged(helper, ModItems.POLYMETAL_ORE_BLOCK_ITEM.get(), "forge", "ores/polymetal");
        assertTagged(helper, ModItems.POLYMETAL_ORE_BLOCK_ITEM.get(), "forge", "ores");
        assertTagged(helper, ModItems.POLYMETAL_DUST.get(), "forge", "dusts/polymetal");
        assertTagged(helper, ModItems.DIRTY_POLYMETAL_DUST.get(), "mekanism", "dirty_dusts/polymetal");
        assertTagged(helper, ModItems.POLYMETAL_CLUMP.get(), "mekanism", "clumps/polymetal");
        assertTagged(helper, ModItems.POLYMETAL_SHARD.get(), "mekanism", "shards/polymetal");
        assertTagged(helper, ModItems.POLYMETAL_CRYSTAL.get(), "mekanism", "crystals/polymetal");
        LogHelper.info("polymetalProcessingItemsRegisteredWithoutMekanism: 5 intermediates and ore tags verified");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void polymetalDustSmeltsToNodule(GameTestHelper helper) {
        assertCooksToNodule(helper, RecipeType.SMELTING, "smelting");
        assertCooksToNodule(helper, RecipeType.BLASTING, "blasting");
        LogHelper.info("polymetalDustSmeltsToNodule: smelting and blasting each produce 1 nodule");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mekanismPolymetalRecipesLoaded(GameTestHelper helper) {
        if (!ModList.get().isLoaded("mekanism")) {
            for (String recipe : MEKANISM_RECIPES) {
                ResourceLocation id = new ResourceLocation(Reference.MOD_ID, RECIPE_PREFIX + recipe);
                helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isEmpty(), "unexpected recipe " + id);
            }
            LogHelper.info("mekanismPolymetalRecipesLoaded: Mekanism absent; 10 conditional recipes absent; slurry checks skipped");
            helper.succeed();
            return;
        }
        for (String recipe : MEKANISM_RECIPES) {
            ResourceLocation id = new ResourceLocation(Reference.MOD_ID, RECIPE_PREFIX + recipe);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(), "missing recipe " + id);
        }
        ForgeRegistry<Object> slurries = RegistryManager.ACTIVE.getRegistry(
                ResourceKey.createRegistryKey(new ResourceLocation("mekanism", "slurry")));
        helper.assertTrue(slurries != null, "Mekanism slurry registry must exist");
        for (String slurry : List.of("dirty_polymetal", "clean_polymetal")) {
            helper.assertTrue(slurries.containsKey(new ResourceLocation(Reference.MOD_ID, slurry)), "missing slurry " + slurry);
        }
        LogHelper.info("mekanismPolymetalRecipesLoaded: Mekanism present; 10 recipes and 2 slurries verified (not skipped)");
        helper.succeed();
    }

    private static void assertTagged(GameTestHelper helper, Item item, String namespace, String path) {
        helper.assertTrue(new ItemStack(item).is(ItemTags.create(new ResourceLocation(namespace, path))),
                item + " must be in #" + namespace + ":" + path);
    }

    private static <T extends AbstractCookingRecipe> void assertCooksToNodule(GameTestHelper helper, RecipeType<T> type,
                                                                              String label) {
        SimpleContainer input = new SimpleContainer(new ItemStack(ModItems.POLYMETAL_DUST.get()));
        ItemStack out = helper.getLevel().getRecipeManager().getRecipeFor(type, input, helper.getLevel())
                .map(recipe -> recipe.assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(out.getItem() == ModItems.POLYMETAL_NODULE.get() && out.getCount() == 1,
                label + " polymetal dust must give 1 polymetal nodule but gave " + out);
    }
}
