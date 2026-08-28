package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.TargetWrench;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.TaskHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaypointPairedChestGameTests {

    private WaypointPairedChestGameTests() {
    }

    @GameTest(template = "arena")
    public static void unpairedWaypointDoesNotUseWorldOriginFurnace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = BlockPos.ZERO;
        BlockPos waypointPos = new BlockPos(2, 0, 0);
        BlockState oldOriginState = level.getBlockState(origin);
        BlockState oldWaypointState = level.getBlockState(waypointPos);
        BasicEntityShip ship = null;

        try {
            level.setBlock(origin, Blocks.FURNACE.defaultBlockState(), 3);
            level.setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
            BlockEntity waypointEntity = level.getBlockEntity(waypointPos);
            BlockEntity furnaceEntity = level.getBlockEntity(origin);
            if (!(waypointEntity instanceof TileEntityWaypoint)
                    || !(furnaceEntity instanceof AbstractFurnaceBlockEntity furnace)) {
                throw new AssertionError("Failed to create waypoint/origin furnace fixtures.");
            }

            ship = ModEntities.BB_KONGOU.get().create(level);
            if (ship == null) {
                throw new AssertionError("Failed to create ship for paired chest test.");
            }
            ship.moveTo(1.5D, 0D, 1.5D);
            level.addFreshEntity(ship);
            ship.setGuardedPos(waypointPos.getX(), waypointPos.getY(), waypointPos.getZ(),
                    level.dimension(), 1);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.RAW_IRON));
            ship.getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON));

            TaskHelper.onUpdateCooking(ship);

            helper.assertTrue(furnace.getItem(0).isEmpty(),
                    "An unpaired waypoint must not route cooking into the world-origin furnace");
        } finally {
            if (ship != null) {
                ship.discard();
            }
            level.setBlock(origin, oldOriginState, 3);
            level.setBlock(waypointPos, oldWaypointState, 3);
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void worldOriginPairSurvivesWaypointNbt(GameTestHelper helper) {
        BlockPos waypointPos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(waypointPos);
        if (!(blockEntity instanceof TileEntityWaypoint waypoint)) {
            throw new AssertionError("Waypoint block did not create its block entity.");
        }

        waypoint.setPairedChest(BlockPos.ZERO);
        helper.assertTrue(waypoint.hasPairedChest(), "World origin must be a valid paired chest position");
        CompoundTag saved = waypoint.saveWithFullMetadata();

        TileEntityWaypoint restored = new TileEntityWaypoint(waypointPos,
                ModBlocks.WAYPOINT.get().defaultBlockState());
        restored.load(saved);
        helper.assertTrue(restored.hasPairedChest(), "Origin pairing was lost during NBT round-trip");
        helper.assertTrue(BlockPos.ZERO.equals(restored.getPairedChest()),
                "Origin pairing coordinates changed during NBT round-trip");

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void legacyWaypointChestPresenceMigrates(GameTestHelper helper) {
        BlockPos waypointPos = helper.absolutePos(new BlockPos(3, 2, 2));
        TileEntityWaypoint restored = new TileEntityWaypoint(waypointPos,
                ModBlocks.WAYPOINT.get().defaultBlockState());
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("ChestX", 0);
        legacy.putInt("ChestY", 0);
        legacy.putInt("ChestZ", 0);

        restored.load(legacy);
        helper.assertTrue(!restored.hasPairedChest(),
                "Legacy zero coordinates must migrate to an unpaired waypoint");

        BlockPos legacyPair = new BlockPos(7, -20, 9);
        legacy.putInt("ChestX", legacyPair.getX());
        legacy.putInt("ChestY", legacyPair.getY());
        legacy.putInt("ChestZ", legacyPair.getZ());
        restored.load(legacy);
        helper.assertTrue(restored.hasPairedChest(),
                "Legacy non-origin coordinates must remain paired");
        helper.assertTrue(legacyPair.equals(restored.getPairedChest()),
                "Legacy paired chest coordinates changed during migration");

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void targetWrenchDistinguishesOriginFromUnselected(GameTestHelper helper) {
        ItemStack wrench = new ItemStack(ModItems.TARGET_WRENCH.get());
        helper.assertTrue(!TargetWrench.hasTilePoint(wrench, 0),
                "A fresh wrench must not have a selected point");

        TargetWrench.setTilePoint(wrench, 0, BlockPos.ZERO);
        helper.assertTrue(TargetWrench.hasTilePoint(wrench, 0),
                "World origin must be representable as an explicitly selected point");
        helper.assertTrue(BlockPos.ZERO.equals(TargetWrench.getTilePoint(wrench, 0)),
                "Selected world-origin coordinates changed");

        ItemStack legacyWrench = new ItemStack(ModItems.TARGET_WRENCH.get());
        legacyWrench.getOrCreateTag().putLong("TilePoint0", BlockPos.ZERO.asLong());
        helper.assertTrue(!TargetWrench.hasTilePoint(legacyWrench, 0),
                "A legacy reset-to-origin point must remain unselected");
        legacyWrench.getOrCreateTag().putLong("TilePoint0", new BlockPos(1, -10, 2).asLong());
        helper.assertTrue(TargetWrench.hasTilePoint(legacyWrench, 0),
                "A legacy non-origin selection must remain selected");

        helper.succeed();
    }
}
