package com.lulan.shincolle.gametest;

import com.lulan.shincolle.block.BlockGrudgeHeavy;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyGrudgeContentsGameTests {
    private static final int[] STOCK = {10, 20, 30, 40};

    private HeavyGrudgeContentsGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void smallShipyardReturnsCoreStockOnceAndRejectsOverflow(GameTestHelper helper) {
        boolean easy = ConfigHandler.easyMode;
        try {
            for (boolean mode : new boolean[] {false, true}) {
                ConfigHandler.easyMode = mode;
                helper.assertTrue(ConfigHandler.easyMode() == mode, "Easy mode fixture must apply.");
                BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
                TileEntitySmallShipyard tile = new TileEntitySmallShipyard(pos,
                        ModBlocks.SMALL_SHIPYARD.get().defaultBlockState());
                tile.setLevel(helper.getLevel());
                tile.getInventory().setStackInSlot(0, stockedCore());
                TileEntitySmallShipyard.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                for (int i = 0; i < 4; i++) {
                    helper.assertTrue(tile.getMatStock(i) == STOCK[i] + (i == 0 ? (mode ? 810 : 81) : 0),
                            "Small shipyard must return stored material once, independently of easy mode: " + i);
                }
                helper.assertTrue(tile.getInventory().getStackInSlot(0).isEmpty(), "Accepted core must be consumed.");
                tile.setMatStock(1, 1000000 - 19);
                ItemStack rejected = stockedCore();
                CompoundTag before = rejected.save(new CompoundTag());
                tile.getInventory().setStackInSlot(0, rejected);
                TileEntitySmallShipyard.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                helper.assertTrue(tile.getMatStock(1) == 1000000 - 19
                                && tile.getMatStock(0) == STOCK[0] + (mode ? 810 : 81)
                                && before.equals(rejected.save(new CompoundTag())),
                        "Overflow must leave all stock and the input unchanged.");
            }
        } finally {
            ConfigHandler.easyMode = easy;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void coreTooltipReadsStoredMaterialTag(GameTestHelper helper) {
        ItemStack stack = stockedCore();
        CompoundTag before = stack.save(new CompoundTag());
        var tooltip = new ArrayList<Component>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 5, "Core tooltip must show all four saved stocks and the crafting restriction.");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(tooltip.get(i).getString().contains(STOCK[i] + " "),
                    "Core tooltip must show stored quantity " + STOCK[i]);
        }
        helper.assertTrue(before.equals(stack.save(new CompoundTag())), "Tooltip must not modify saved content.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void brokenCoreSavesUnconvertedLava(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(pos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        TileMultiGrudgeHeavy tile = (TileMultiGrudgeHeavy) helper.getLevel().getBlockEntity(pos);
        for (int i = 0; i < 4; i++) tile.setMatStock(i, STOCK[i]);
        tile.setPowerRemained(1234);
        IFluidHandler tank = tile.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new IllegalStateException("Core must expose its lava tank."));
        helper.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 500), IFluidHandler.FluidAction.EXECUTE) == 500,
                "Fixture must fill 500mB lava.");
        TileMultiGrudgeHeavy.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
        helper.assertTrue(tank.getFluidInTank(0).getAmount() == 500 && tile.getPowerRemained() == 1234,
                "Partial lava must remain unconverted before removal.");
        helper.assertTrue(helper.getLevel().destroyBlock(pos, true), "Core must be destroyed.");
        var drops = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class, new AABB(pos).inflate(2),
                entity -> entity.getEntityItem().is(ModBlocks.GRUDGE_HEAVY.get().asItem()));
        helper.assertTrue(drops.size() == 1, "Removal must drop exactly one saved core.");
        CompoundTag tag = drops.get(0).getEntityItem().getTag();
        helper.assertTrue(tag != null && Arrays.equals(tag.getIntArray("Mats"), STOCK) && tag.getInt("Fuel") == 1234,
                "Core must still retain materials and converted fuel.");
        FluidStack lava = FluidStack.loadFluidStackFromNBT(tag.getCompound("FuelFluid"));
        helper.assertTrue(lava.getFluid() == Fluids.LAVA && lava.getAmount() == 500,
                "Core drop must retain the unconverted 500mB lava.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void placedCoreRestoresUnconvertedLava(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockGrudgeHeavy block = (BlockGrudgeHeavy) ModBlocks.GRUDGE_HEAVY.get();
        ItemStack stack = stockedCore();
        stack.getOrCreateTag().putInt("Fuel", 1234);
        stack.getOrCreateTag().put("FuelFluid", new FluidStack(Fluids.LAVA, 500).writeToNBT(new CompoundTag()));
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        block.setPlacedBy(helper.getLevel(), pos, block.defaultBlockState(), null, stack);
        TileMultiGrudgeHeavy tile = (TileMultiGrudgeHeavy) helper.getLevel().getBlockEntity(pos);
        IFluidHandler tank = tile.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new IllegalStateException("Core must expose its lava tank."));
        helper.assertTrue(tank.getFluidInTank(0).getFluid() == Fluids.LAVA && tank.getFluidInTank(0).getAmount() == 500,
                "Placement must restore 500mB saved lava.");
        for (int i = 0; i < 4; i++) helper.assertTrue(tile.getMatStock(i) == STOCK[i], "Placement must retain stock.");
        helper.assertTrue(tile.getPowerRemained() == 1234, "Placement must retain converted fuel.");
        helper.succeed();
    }

    private static ItemStack stockedCore() {
        ItemStack stack = new ItemStack(ModBlocks.GRUDGE_HEAVY.get());
        stack.getOrCreateTag().putIntArray("Mats", STOCK.clone());
        return stack;
    }
}
