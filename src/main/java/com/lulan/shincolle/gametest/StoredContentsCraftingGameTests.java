package com.lulan.shincolle.gametest;

import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.item.ShipTank;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoredContentsCraftingGameTests {
    private static final String[] CORE_RECIPES = {"grudge_blocks_from_heavy_grudge", "vol_core", "vol_core_alt",
            "repair_goddess", "repair_goddess_alt", "ship_tank_3"};

    private StoredContentsCraftingGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void smallShipyardPreservesFueledCores(GameTestHelper helper) {
        checkIntake(helper, false, false);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeShipyardPreservesFueledCores(GameTestHelper helper) {
        checkIntake(helper, true, false);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void smallShipyardPreservesSavedEggsButRecyclesFreshEggs(GameTestHelper helper) {
        checkIntake(helper, false, true);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeShipyardPreservesSavedEggsButRecyclesFreshEggs(GameTestHelper helper) {
        checkIntake(helper, true, true);
        helper.succeed();
    }

    private static void checkIntake(GameTestHelper helper, boolean large, boolean egg) {
        for (int variant = 0; variant < (egg ? 2 : 3); variant++) {
            ItemStack stack = egg ? freshEgg() : new ItemStack(ModBlocks.GRUDGE_HEAVY.get());
            if (egg) {
                stack.getOrCreateTag().putIntArray("StateMinor", variant == 0 ? new int[] {30} : new int[0]);
                stack.getOrCreateTag().putString("ShipInventory", "preserved cargo");
                stack.getOrCreateTag().putString("Curios", "preserved equipment");
            } else if (variant == 0) {
                stack.getOrCreateTag().putInt("Fuel", 123);
            } else if (variant == 1) {
                stack.getOrCreateTag().put("FuelFluid", new FluidStack(Fluids.LAVA, 500).writeToNBT(new CompoundTag()));
            } else {
                stack.getOrCreateTag().putInt("Fuel", 123);
                stack.getOrCreateTag().putIntArray("Mats", new int[] {10, 20, 30, 40});
            }
            CompoundTag before = stack.save(new CompoundTag());
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            if (large) {
                TileMultiGrudgeHeavy tile = new TileMultiGrudgeHeavy(pos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState());
                tile.setLevel(helper.getLevel());
                tile.getInventory().setStackInSlot(1, stack);
                TileMultiGrudgeHeavy.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                helper.assertTrue(before.equals(tile.getInventory().getStackInSlot(1).save(new CompoundTag())),
                        "Large intake must preserve rejected input variant " + variant);
                for (int i = 0; i < 4; i++) helper.assertTrue(tile.getMatStock(i) == 0, "Rejected input adds no stock.");
                helper.assertTrue(tile.getPowerRemained() == 0, "Rejected input adds no fuel.");
                if (egg) {
                    tile.getInventory().setStackInSlot(1, freshEgg());
                    TileMultiGrudgeHeavy.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                    helper.assertTrue(tile.getInventory().getStackInSlot(1).isEmpty() && tile.getMatStock(0) > 0,
                            "Fresh egg must still recycle in large yard.");
                }
            } else {
                TileEntitySmallShipyard tile = new TileEntitySmallShipyard(pos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState());
                tile.setLevel(helper.getLevel());
                tile.getInventory().setStackInSlot(0, stack);
                TileEntitySmallShipyard.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                helper.assertTrue(before.equals(tile.getInventory().getStackInSlot(0).save(new CompoundTag())),
                        "Small intake must preserve rejected input variant " + variant);
                for (int i = 0; i < 4; i++) helper.assertTrue(tile.getMatStock(i) == 0, "Rejected input adds no stock.");
                helper.assertTrue(tile.getPowerRemained() == 0, "Rejected input adds no fuel.");
                if (egg) {
                    tile.getInventory().setStackInSlot(0, freshEgg());
                    TileEntitySmallShipyard.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                    helper.assertTrue(tile.getInventory().getStackInSlot(0).isEmpty() && tile.getMatStock(0) > 0,
                            "Fresh egg must still recycle in small yard.");
                }
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void coreCraftsRejectEveryStoredContentAndKeepEmptyRecipes(GameTestHelper helper) {
        for (String name : CORE_RECIPES) {
            CraftingRecipe recipe = recipe(helper, name);
            for (int variant = 0; variant < 4; variant++) {
                CraftingContainer grid = grid(recipe);
                helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Empty core must match " + name);
                ItemStack core = find(grid, "grudge_heavy");
                switch (variant) {
                    case 0 -> core.getOrCreateTag().putIntArray("Mats", new int[] {0, 0, 1, 0});
                    case 1 -> core.getOrCreateTag().putInt("Fuel", 1);
                    case 2 -> core.getOrCreateTag().put("FuelFluid", new FluidStack(Fluids.LAVA, 500).writeToNBT(new CompoundTag()));
                    default -> core.getOrCreateTag().putIntArray("Mats", new int[] {0, 0, 0, 0});
                }
                CompoundTag before = core.save(new CompoundTag());
                boolean allowed = variant == 3;
                helper.assertTrue(recipe.matches(grid, helper.getLevel()) == allowed, name + " content variant " + variant);
                helper.assertTrue(recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty() != allowed,
                        "Direct assembly must also protect " + name);
                helper.assertTrue(before.equals(core.save(new CompoundTag())), "Recipe must not mutate core.");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void tankUpgradesSumSameFluidAndPreserveInputs(GameTestHelper helper) {
        for (int tier = 1; tier <= 3; tier++) {
            CraftingRecipe recipe = recipe(helper, "ship_tank_" + tier);
            CraftingContainer grid = grid(recipe);
            int total = 0;
            for (int i = 0; i < grid.getContainerSize(); i++) {
                if (grid.getItem(i).getItem() instanceof ShipTank tank) {
                    fill(helper, grid.getItem(i), new FluidStack(Fluids.WATER, tank.getTankCapacity()));
                    total += tank.getTankCapacity();
                }
            }
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Same-fluid upgrade must match tier " + tier);
            ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
            FluidStack fluid = result.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                    () -> new IllegalStateException("Result needs tank capability")).getFluidInTank(0);
            helper.assertTrue(fluid.getFluid() == Fluids.WATER && fluid.getAmount() == total,
                    "Upgrade must retain the full sum at tier " + tier);
            ItemStack again = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(ItemStack.isSameItemSameTags(result, again), "Repeated preview must not drain inputs.");
            for (int i = 0; i < grid.getContainerSize(); i++) {
                if (grid.getItem(i).getItem() instanceof ShipTank tank) {
                    helper.assertTrue(grid.getItem(i).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                            () -> new IllegalStateException("Input needs tank capability")).getFluidInTank(0).getAmount()
                            == tank.getTankCapacity(), "Inputs must stay full.");
                }
            }
            CraftingContainer sparse = grid(recipe);
            FluidStack tagged = new FluidStack(Fluids.LAVA, 1000);
            tagged.getOrCreateTag().putString("kind", "retain me");
            fill(helper, sparse.getItem(4), tagged);
            helper.assertTrue(recipe.matches(sparse, helper.getLevel()), "Empty tanks can mix with one filled tank.");
            ItemStack sparseResult = recipe.assemble(sparse, helper.getLevel().registryAccess());
            helper.assertTrue(sparseResult.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                    () -> new IllegalStateException("Result needs tank capability")).getFluidInTank(0).isFluidStackIdentical(tagged),
                    "Upgrade must retain fluid type, amount and tags while other tanks are empty.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void tankUpgradesRejectMixedFluidIncludingDifferentFluidTags(GameTestHelper helper) {
        for (int tier = 1; tier <= 3; tier++) {
            for (boolean tagged : new boolean[] {false, true}) {
                CraftingRecipe recipe = recipe(helper, "ship_tank_" + tier);
                CraftingContainer grid = grid(recipe);
                FluidStack first = new FluidStack(Fluids.WATER, 1000);
                FluidStack second = new FluidStack(tagged ? Fluids.WATER : Fluids.LAVA, 1000);
                if (tagged) second.getOrCreateTag().putString("kind", "different");
                fill(helper, grid.getItem(1), first);
                fill(helper, grid.getItem(4), second);
                helper.assertTrue(!recipe.matches(grid, helper.getLevel())
                                && recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty(),
                        "Mixed fluid must yield no output at tier " + tier);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void tankUpgradesRejectOverflowWithoutTruncatingStoredFluid(GameTestHelper helper) {
        for (int tier = 1; tier <= 3; tier++) {
            CraftingRecipe recipe = recipe(helper, "ship_tank_" + tier);
            CraftingContainer grid = grid(recipe);
            int capacity = ((ShipTank) recipe.getResultItem(helper.getLevel().registryAccess()).getItem()).getTankCapacity();
            ItemStack input = grid.getItem(1);
            input.getOrCreateTag().put("Fluid", new FluidStack(Fluids.WATER, capacity + 1).writeToNBT(new CompoundTag()));
            CompoundTag before = input.save(new CompoundTag());
            helper.assertTrue(!recipe.matches(grid, helper.getLevel())
                            && recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty(),
                    "Overflow must yield no output at tier " + tier);
            helper.assertTrue(before.equals(input.save(new CompoundTag())), "Overflow must not truncate input.");
        }
        helper.succeed();
    }

    private static ItemStack freshEgg() {
        return ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createStack(0);
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String name) {
        CraftingRecipe recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(id(name)).orElseThrow();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientboundUpdateRecipesPacket.toNetwork(buffer, recipe);
            CraftingRecipe decoded = (CraftingRecipe) ClientboundUpdateRecipesPacket.fromNetwork(buffer);
            helper.assertTrue(decoded.getId().equals(recipe.getId()) && decoded.getSerializer() == recipe.getSerializer()
                            && decoded.getIngredients().size() == recipe.getIngredients().size(),
                    "Recipe sync must preserve identity, serializer and ingredients: " + name);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, name);
    }

    private static CraftingContainer grid(CraftingRecipe recipe) {
        CraftingContainer grid = new InventoryCraftingFake(3, 3);
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            var items = recipe.getIngredients().get(i).getItems();
            if (items.length > 0) grid.setItem(i, items[0].copy());
        }
        return grid;
    }

    private static ItemStack find(CraftingContainer grid, String name) {
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (grid.getItem(i).is(BuiltInRegistries.ITEM.get(id(name)))) return grid.getItem(i);
        }
        throw new IllegalStateException("Missing ingredient " + name);
    }

    private static void fill(GameTestHelper helper, ItemStack stack, FluidStack fluid) {
        int accepted = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                () -> new IllegalStateException("Input needs tank capability")).fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(accepted == fluid.getAmount(), "Fixture must store all fluid.");
    }
}
