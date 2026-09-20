package com.lulan.shincolle.gametest;

import com.lulan.shincolle.block.BlockSmallShipyard;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallShipyardDropGameTests {

    private static final int[] STOCK = {123, 456, 789, 321};
    private static final int POWER = 2345;
    private static final int FLUID_AMOUNT = 2750;

    private SmallShipyardDropGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void stockedShipyardDropsStoredResources(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        TileEntitySmallShipyard shipyard = placeShipyard(helper.getLevel(), pos);
        for (int i = 0; i < STOCK.length; i++) {
            shipyard.setMatStock(i, STOCK[i]);
        }
        shipyard.setPowerRemained(POWER);
        fillLava(shipyard, FLUID_AMOUNT);

        helper.assertTrue(helper.getLevel().destroyBlock(pos, true), "Stocked shipyard was not destroyed.");
        List<ItemStack> drops = shipyardDrops(helper.getLevel(), pos);
        helper.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1,
                "Stocked shipyard must drop exactly one shipyard item, saw " + drops);

        CompoundTag tag = drops.get(0).getTag();
        helper.assertTrue(tag != null, "Stocked shipyard drop must contain NBT.");
        helper.assertTrue(Arrays.equals(tag.getIntArray("Mats"), STOCK),
                "Stocked shipyard drop did not retain material stocks.");
        helper.assertTrue(tag.getInt("Fuel") == POWER,
                "Stocked shipyard drop did not retain stored power.");
        FluidTank restoredTank = new FluidTank(16000);
        restoredTank.readFromNBT(tag.getCompound("FuelFluid"));
        helper.assertTrue(restoredTank.getFluid().getFluid() == Fluids.LAVA
                        && restoredTank.getFluidAmount() == FLUID_AMOUNT,
                "Stocked shipyard drop did not retain lava tank contents.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void stockedShipyardItemRestoresStoredResources(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModBlocks.SMALL_SHIPYARD.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putIntArray("Mats", STOCK);
        tag.putInt("Fuel", POWER);
        FluidTank storedTank = new FluidTank(16000);
        storedTank.fill(new FluidStack(Fluids.LAVA, FLUID_AMOUNT), IFluidHandler.FluidAction.EXECUTE);
        tag.put("FuelFluid", storedTank.writeToNBT(new CompoundTag()));

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockSmallShipyard block = (BlockSmallShipyard) ModBlocks.SMALL_SHIPYARD.get();
        BlockState state = block.defaultBlockState();
        level.setBlock(pos, state, 3);
        block.setPlacedBy(level, pos, state, null, stack);
        TileEntitySmallShipyard shipyard = getShipyard(level, pos);

        for (int i = 0; i < STOCK.length; i++) {
            helper.assertTrue(shipyard.getMatStock(i) == STOCK[i],
                    "Placed shipyard did not restore material stock " + i + ".");
        }
        helper.assertTrue(shipyard.getPowerRemained() == POWER,
                "Placed shipyard did not restore stored power.");
        IFluidHandler fluidHandler = shipyard.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new AssertionError("Small shipyard has no fluid capability."));
        helper.assertTrue(fluidHandler.getFluidInTank(0).getFluid() == Fluids.LAVA
                        && fluidHandler.getFluidInTank(0).getAmount() == FLUID_AMOUNT,
                "Placed shipyard did not restore lava tank contents.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptyShipyardDropsUntaggedItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        placeShipyard(level, pos);

        helper.assertTrue(level.destroyBlock(pos, true), "Empty shipyard was not destroyed.");
        List<ItemStack> drops = shipyardDrops(level, pos);
        helper.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1,
                "Empty shipyard must drop exactly one shipyard item, saw " + drops);
        helper.assertTrue(!drops.get(0).hasTag(), "Empty shipyard drop must not contain NBT.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipyardInputDropsExactlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        TileEntitySmallShipyard shipyard = placeShipyard(level, pos);
        shipyard.getInventory().setStackInSlot(TileEntitySmallShipyard.SLOT_INPUT_START,
                new ItemStack(Items.COAL, 3));

        helper.assertTrue(level.destroyBlock(pos, true), "Shipyard with input was not destroyed.");
        AABB bounds = new AABB(pos).inflate(2.0D);
        int droppedCoal = level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .filter(entity -> entity.getItem().is(Items.COAL))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
        helper.assertTrue(droppedCoal == 3,
                "Shipyard input must drop once with count 3, saw " + droppedCoal + ".");
        helper.succeed();
    }

    private static TileEntitySmallShipyard placeShipyard(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState(), 3);
        return getShipyard(level, pos);
    }

    private static TileEntitySmallShipyard getShipyard(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TileEntitySmallShipyard shipyard) {
            return shipyard;
        }
        throw new AssertionError("Small shipyard block entity was not created.");
    }

    private static void fillLava(TileEntitySmallShipyard shipyard, int amount) {
        IFluidHandler fluidHandler = shipyard.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new AssertionError("Small shipyard has no fluid capability."));
        int filled = fluidHandler.fill(new FluidStack(Fluids.LAVA, amount), IFluidHandler.FluidAction.EXECUTE);
        if (filled != amount) {
            throw new AssertionError("Small shipyard accepted " + filled + " mB instead of " + amount + " mB.");
        }
    }

    private static List<ItemStack> shipyardDrops(ServerLevel level, BlockPos pos) {
        AABB bounds = new AABB(pos).inflate(2.0D);
        List<ItemStack> drops = new ArrayList<>();
        level.getEntitiesOfClass(ItemEntity.class, bounds,
                        entity -> entity.getItem().is(ModBlocks.SMALL_SHIPYARD.get().asItem()))
                .forEach(entity -> drops.add(entity.getItem()));
        level.getEntitiesOfClass(BasicEntityItem.class, bounds,
                        entity -> entity.getEntityItem().is(ModBlocks.SMALL_SHIPYARD.get().asItem()))
                .forEach(entity -> drops.add(entity.getEntityItem()));
        return drops;
    }
}
