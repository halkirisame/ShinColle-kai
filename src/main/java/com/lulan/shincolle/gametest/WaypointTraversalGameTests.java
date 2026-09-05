package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaypointTraversalGameTests {

    private WaypointTraversalGameTests() {
    }

    @GameTest(template = "arena")
    public static void linkedWaypointChangesBlockGuardAtArrival(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos second = helper.absolutePos(new BlockPos(7, 2, 2));
        BasicEntityShip ship = null;

        try {
            TileEntityWaypoint firstWaypoint = placeWaypoint(level, first);
            placeWaypoint(level, second);
            firstWaypoint.setNextWaypoint(second);

            ship = createArrivedShip(level, first);
            helper.assertTrue(EntityHelper.updateWaypointMove(ship),
                    "An arrived linked waypoint must advance the ship guard target");
            assertGuardTarget(helper, ship, second, "Linked waypoint did not become the next guard target");
            helper.assertTrue(ship.hasLastWaypoint() && first.equals(ship.getLastWaypoint()),
                    "Traversal did not retain the current waypoint as route history");

            ship.moveTo(second.getX() + 0.5D, second.getY() + 0.5D, second.getZ() + 0.5D);
            helper.assertTrue(!EntityHelper.updateWaypointMove(ship),
                    "An unlinked waypoint must not replace its current block guard target");
            assertGuardTarget(helper, ship, second, "An unlinked waypoint changed the guard target");
            helper.assertTrue(ship.getWpStayTime() == 0 && second.equals(ship.getLastWaypoint()),
                    "An unlinked waypoint retained stay progress or route history from the prior point");
        } finally {
            if (ship != null) {
                ship.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void matchingPreviousNextUsesWaypointLastLink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos current = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos previous = helper.absolutePos(new BlockPos(7, 2, 2));
        BlockPos reverse = helper.absolutePos(new BlockPos(2, 2, 7));
        BasicEntityShip ship = null;

        try {
            TileEntityWaypoint currentWaypoint = placeWaypoint(level, current);
            placeWaypoint(level, previous);
            placeWaypoint(level, reverse);
            currentWaypoint.setNextWaypoint(previous);
            currentWaypoint.setLastWaypoint(reverse);

            ship = createArrivedShip(level, current);
            ship.setLastWaypoint(previous);
            helper.assertTrue(EntityHelper.updateWaypointMove(ship),
                    "A reverse-link waypoint must choose its explicit last link");
            assertGuardTarget(helper, ship, reverse,
                    "Traversal reused the previous waypoint instead of following the last link");
        } finally {
            if (ship != null) {
                ship.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void routePresenceKeepsOriginAndNegativeYAcrossNbt(GameTestHelper helper) {
        BlockPos waypointPos = helper.absolutePos(new BlockPos(2, 2, 2));
        TileEntityWaypoint waypoint = new TileEntityWaypoint(waypointPos,
                ModBlocks.WAYPOINT.get().defaultBlockState());
        BlockPos negativeY = new BlockPos(4, -20, 6);

        waypoint.setNextWaypoint(BlockPos.ZERO);
        waypoint.setLastWaypoint(negativeY);
        waypoint.setWpStayTime(16);
        CompoundTag saved = waypoint.saveWithFullMetadata();

        TileEntityWaypoint restored = new TileEntityWaypoint(waypointPos,
                ModBlocks.WAYPOINT.get().defaultBlockState());
        restored.load(saved);
        helper.assertTrue(restored.hasNextWaypoint() && BlockPos.ZERO.equals(restored.getNextWaypoint()),
                "An explicitly linked world origin was lost during waypoint NBT round-trip");
        helper.assertTrue(restored.hasLastWaypoint() && negativeY.equals(restored.getLastWaypoint()),
                "A negative-Y last link was lost during waypoint NBT round-trip");
        helper.assertTrue(restored.getWpStayTime() == 16,
                "Waypoint raw stay setting was not retained during NBT round-trip");

        CompoundTag legacy = new CompoundTag();
        legacy.putInt("NextX", 0);
        legacy.putInt("NextY", 0);
        legacy.putInt("NextZ", 0);
        legacy.putInt("LastX", negativeY.getX());
        legacy.putInt("LastY", negativeY.getY());
        legacy.putInt("LastZ", negativeY.getZ());
        restored.load(legacy);
        helper.assertTrue(!restored.hasNextWaypoint(),
                "Legacy zero route coordinates must remain an unset link");
        helper.assertTrue(restored.hasLastWaypoint() && negativeY.equals(restored.getLastWaypoint()),
                "Legacy non-zero last link did not migrate as present");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void longerStaySettingControlsTraversalDelay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos second = helper.absolutePos(new BlockPos(7, 2, 2));
        BasicEntityShip ship = null;

        try {
            TileEntityWaypoint firstWaypoint = placeWaypoint(level, first);
            placeWaypoint(level, second);
            firstWaypoint.setNextWaypoint(second);
            firstWaypoint.setWpStayTime(1); // 100 ticks
            ship = createArrivedShip(level, first);
            ship.setStateMinor(ID.M.WpStay, 6); // 1200 ticks: longer setting wins

            for (int i = 0; i < 75; i++) {
                helper.assertTrue(!EntityHelper.updateWaypointMove(ship),
                        "Traversal advanced before the longer ship stay setting elapsed");
            }
            helper.assertTrue(ship.getWpStayTime() == 1200,
                    "Traversal progress did not advance in 16-tick cadence to the longer stay duration");
            helper.assertTrue(EntityHelper.updateWaypointMove(ship),
                    "Traversal did not advance after the longer stay setting elapsed");
            assertGuardTarget(helper, ship, second, "Traversal did not use the linked destination after waiting");
        } finally {
            if (ship != null) {
                ship.discard();
            }
        }
        helper.succeed();
    }

    private static TileEntityWaypoint placeWaypoint(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof TileEntityWaypoint waypoint)) {
            throw new AssertionError("Waypoint block did not create its block entity.");
        }
        return waypoint;
    }

    private static BasicEntityShip createArrivedShip(ServerLevel level, BlockPos destination) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(level);
        if (ship == null) {
            throw new AssertionError("Failed to create ship for waypoint traversal test.");
        }
        ship.moveTo(destination.getX() + 0.5D, destination.getY() + 0.5D, destination.getZ() + 0.5D);
        level.addFreshEntity(ship);
        ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(), level.dimension(), 1);
        ship.setStateFlag(ID.F.CanFollow, false);
        return ship;
    }

    private static void assertGuardTarget(GameTestHelper helper, BasicEntityShip ship, BlockPos expected,
                                          String message) {
        helper.assertTrue(ship.getGuardedPos(0) == expected.getX()
                        && ship.getGuardedPos(1) == expected.getY()
                        && ship.getGuardedPos(2) == expected.getZ(),
                message);
    }
}
