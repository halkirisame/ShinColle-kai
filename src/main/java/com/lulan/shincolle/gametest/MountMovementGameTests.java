package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFloatingGoal;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** A mount walks for the ship it carries, as upstream's mount AI did (NEW authority only). */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountMovementGameTests {
    private MountMovementGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_follow_target", timeoutTicks = 300)
    public static void newMountedShipKeepsGuardingMovingEntity(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BasicEntityShip grounded = ship(helper, context, new Vec3(1.5D, 1D, 1.5D));
            BasicEntityShip mounted = ship(helper, context, new Vec3(1.5D, 1D, 4.5D));
            BasicEntityMount mount = mount(helper, context, mounted);
            Cow groundedCow = cow(helper, context, new Vec3(10.5D, 1D, 1.5D));
            Cow mountedCow = cow(helper, context, new Vec3(10.5D, 1D, 4.5D));
            helper.runAtTickTime(20, () -> context.stage(() -> {
                guard(grounded, groundedCow);
                guard(mounted, mountedCow);
            }));
            helper.runAtTickTime(140, () -> context.stage(() -> {
                close(helper, "first approach", grounded, groundedCow, mount, mountedCow);
                groundedCow.teleportTo(groundedCow.getX() - 8D, groundedCow.getY(), groundedCow.getZ());
                mountedCow.teleportTo(mountedCow.getX() - 8D, mountedCow.getY(), mountedCow.getZ());
            }));
            helper.runAtTickTime(280, () -> context.finish(() ->
                    close(helper, "after the target moved", grounded, groundedCow, mount, mountedCow)));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_release", timeoutTicks = 300)
    public static void newMountedOneShotMoveReleasesOnArrival(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            // The mount must not stop short of the destination at its hold radius, or the carried
            // ship never sees a completed path; larger FollowMin values widen that radius.
            BasicEntityShip near = ship(helper, context, new Vec3(1.5D, 1D, 1.5D));
            BasicEntityShip far = ship(helper, context, new Vec3(1.5D, 1D, 4.5D));
            near.setStateMinor(ID.M.FollowMin, 2);
            far.setStateMinor(ID.M.FollowMin, 4);
            far.setStateMinor(ID.M.FollowMax, 5);
            BasicEntityMount nearMount = mount(helper, context, near);
            BasicEntityMount farMount = mount(helper, context, far);
            helper.runAtTickTime(20, () -> context.stage(() -> {
                move(helper, near, new BlockPos(10, 1, 1));
                move(helper, far, new BlockPos(10, 1, 4));
                helper.assertTrue(near.getCommandState().movement() instanceof MovementOrder.MoveTo
                        && far.getCommandState().movement() instanceof MovementOrder.MoveTo, "Fixture must move");
            }));
            helper.runAtTickTime(280, () -> context.finish(() -> {
                for (BasicEntityShip ship : java.util.List.of(near, far)) {
                    helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                            "A one-shot move must end on arrival for FollowMin=" + ship.getStateMinor(ID.M.FollowMin)
                                    + ": " + ship.getCommandState().movement());
                }
                helper.assertTrue(near.getVehicle() == nearMount && far.getVehicle() == farMount,
                        "Arrival must keep the ships on their mounts");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_target_lost", timeoutTicks = 100)
    public static void newMountGuardLossEndsCarriedShipsCommand(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 1D, 2.5D));
            ship.enableCommandProjectionCheckForTest();
            BasicEntityMount mount = mount(helper, context, ship);
            Cow cow = cow(helper, context, new Vec3(9.5D, 1D, 2.5D));
            helper.runAtTickTime(20, () -> context.stage(() -> {
                guard(ship, cow);
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardEntity,
                        "Fixture must guard the cow");
            }));
            helper.runAtTickTime(30, () -> context.stage(() -> cow.discard()));
            helper.runAtTickTime(60, () -> context.finish(() -> {
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Losing the guarded entity must end the carried ship's command through its state: "
                                + ship.getCommandState().movement());
                ship.assertCommandProjection();
                helper.assertTrue(ship.getVehicle() == mount, "The ship must stay on its mount");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_player", timeoutTicks = 60)
    public static void newMountDoesNotWalkWhilePlayerSteers(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 1D, 2.5D));
            BasicEntityMount mount = mount(helper, context, ship);
            Cow cow = cow(helper, context, new Vec3(10.5D, 1D, 2.5D));
            helper.runAtTickTime(20, () -> context.finish(() -> {
                guard(ship, cow);
                WrappedGoal guarding = goal(mount, ShipGuardingGoal.class);
                helper.assertTrue(guarding != null, "NEW must give the mount a guarding goal");
                helper.assertTrue(guarding.getGoal().canUse(), "Fixture mount must want to walk to the cow");
                Player player = context.entities.add(helper.makeMockPlayer());
                helper.assertTrue(player.startRiding(mount, true), "Player must board the mount");
                helper.assertTrue(mount.getControllingPassenger() == player, "Player must steer the mount");
                helper.assertTrue(!guarding.getGoal().canUse(), "Mount must not walk on its own while a player steers");
                player.stopRiding();
                helper.assertTrue(guarding.getGoal().canUse(), "Mount must walk again once the player leaves");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_floating", timeoutTicks = 260)
    public static void newMountStaysAtSurfaceWithItsShipAboard(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            // A glass tank, water 6 deep. The mount starts in the top water block with air above,
            // the only place a following mount floats: deeper down its nearby ship counts as a
            // guard position, as upstream's floating goal did.
            for (int x = 0; x <= 5; x++) {
                for (int z = 0; z <= 5; z++) {
                    for (int y = 0; y <= 7; y++) {
                        boolean inner = x >= 1 && x <= 4 && z >= 1 && z <= 4 && y >= 1;
                        if (inner && y <= 6) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                        } else if (!inner) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                        }
                    }
                }
            }
            BasicEntityShip ship = ship(helper, context, new Vec3(2.5D, 6.5D, 2.5D));
            BasicEntityMount mount = mount(helper, context, ship);
            // Below y=0 the depth probe truncates the feet block toward zero, so the mount settles one
            // block lower than it would at sea level. Sinking still ends on the bottom, five blocks down.
            int surfaceFloorY = helper.absolutePos(new BlockPos(2, 5, 2)).getY();
            helper.runAtTickTime(240, () -> context.finish(() -> {
                helper.assertTrue(ship.getVehicle() == mount, "The ship must stay on its mount");
                helper.assertTrue(mount.getY() >= surfaceFloorY, "The mount must stay at the surface with its ship aboard: y="
                        + mount.getY() + " surfaceFloorY=" + surfaceFloorY + " depth=" + mount.getShipDepth());
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_floating_to_legacy", timeoutTicks = 360)
    public static void mountKeepsNewFloatingUntilItsAiIsRebuiltAfterSwitchToLegacy(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            buildTank(helper);
            BasicEntityShip ship = ship(helper, context, new Vec3(2.5D, 6.5D, 2.5D));
            BasicEntityMount mount = mount(helper, context, ship);
            int surfaceFloorY = helper.absolutePos(new BlockPos(2, 5, 2)).getY();
            // After the ship re-registered its goals (tick 16) and the mount's goal list is settled.
            helper.runAtTickTime(30, () -> context.stage(() -> {
                helper.assertTrue(goal(mount, ShipFloatingGoal.class) != null, "Fixture: a NEW mount must float");
                ConfigHandler.setShipAiTargetAuthorityForTest(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                helper.assertTrue(!ShipCommandStateAdapter.isNew(), "Fixture: the setting must now read LEGACY");
            }));
            // The goals, the depth probe and the liquid movement all stay NEW until setAIList runs again.
            helper.runAtTickTime(200, () -> context.stage(() -> {
                helper.assertTrue(goal(mount, ShipFloatingGoal.class) != null,
                        "The floating goal must stay until the mount's AI is rebuilt");
                helper.assertTrue(mount.getY() >= surfaceFloorY,
                        "The mount must keep floating until its AI is rebuilt: y=" + mount.getY()
                                + " surfaceFloorY=" + surfaceFloorY + " depth=" + mount.getShipDepth());
                mount.setAIList();
                helper.assertTrue(goal(mount, ShipFloatingGoal.class) == null,
                        "Rebuilding under LEGACY must drop the floating goal");
            }));
            helper.runAtTickTime(340, () -> context.finish(() ->
                    helper.assertTrue(mount.getY() < surfaceFloorY - 1,
                            "A rebuilt LEGACY mount keeps the unchanged (sinking) behavior: y=" + mount.getY()
                                    + " surfaceFloorY=" + surfaceFloorY)));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_floating_to_new", timeoutTicks = 300)
    public static void mountStaysLegacyUntilItsAiIsRebuiltAfterSwitchToNew(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
        try {
            buildTank(helper);
            BasicEntityShip ship = ship(helper, context, new Vec3(2.5D, 6.5D, 2.5D));
            BasicEntityMount mount = mount(helper, context, ship);
            int surfaceFloorY = helper.absolutePos(new BlockPos(2, 5, 2)).getY();
            helper.runAtTickTime(30, () -> context.stage(() -> {
                helper.assertTrue(goal(mount, ShipFloatingGoal.class) == null, "Fixture: a LEGACY mount must not float");
                ConfigHandler.setShipAiTargetAuthorityForTest(ConfigHandler.ShipAiTargetAuthority.NEW);
                helper.assertTrue(ShipCommandStateAdapter.isNew(), "Fixture: the setting must now read NEW");
            }));
            // Still LEGACY in all three: no goal, and it sinks under the unchanged water physics.
            helper.runAtTickTime(130, () -> context.stage(() -> {
                helper.assertTrue(goal(mount, ShipFloatingGoal.class) == null,
                        "No floating goal may appear before the mount's AI is rebuilt");
                helper.assertTrue(mount.getY() < surfaceFloorY - 1,
                        "The mount must keep the LEGACY (sinking) behavior until its AI is rebuilt: y=" + mount.getY()
                                + " surfaceFloorY=" + surfaceFloorY);
                // Back to the surface (a sunk mount is under water, which counts as a guard position).
                mount.teleportTo(helper.absoluteVec(new Vec3(2.5D, 6.5D, 2.5D)).x,
                        helper.absoluteVec(new Vec3(2.5D, 6.5D, 2.5D)).y,
                        helper.absoluteVec(new Vec3(2.5D, 6.5D, 2.5D)).z);
                mount.setDeltaMovement(Vec3.ZERO);
                mount.setAIList();
                helper.assertTrue(goal(mount, ShipFloatingGoal.class) != null,
                        "Rebuilding under NEW must add the floating goal");
            }));
            helper.runAtTickTime(280, () -> context.finish(() -> {
                helper.assertTrue(ship.getVehicle() == mount, "The ship must stay on its mount");
                helper.assertTrue(mount.getY() >= surfaceFloorY,
                        "The rebuilt NEW mount must float: y=" + mount.getY() + " surfaceFloorY=" + surfaceFloorY);
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    /** A glass tank, water 6 deep, air above (the fixture of the floating test). */
    private static void buildTank(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int y = 0; y <= 7; y++) {
                    boolean inner = x >= 1 && x <= 4 && z >= 1 && z <= 4 && y >= 1;
                    if (inner && y <= 6) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                    } else if (!inner) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
    }

    @GameTest(template = "arena", batch = "isolated_mount_movement_legacy", timeoutTicks = 40)
    public static void legacyMountKeepsItsSingleGoal(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 1D, 2.5D));
            BasicEntityMount mount = mount(helper, context, ship);
            helper.runAtTickTime(5, () -> context.finish(() ->
                    helper.assertTrue(goal(mount, ShipGuardingGoal.class) == null
                                    && goal(mount, ShipFollowOwnerGoal.class) == null
                                    && goal(mount, ShipFloatingGoal.class) == null,
                            "LEGACY mounts must keep their unchanged goal list")));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    /** Upstream's mount advanced the waypoint route for the ship it carried (BasicEntityMount:548). */
    @GameTest(template = "arena", batch = "isolated_mount_movement_waypoint", timeoutTicks = 200)
    public static void mountedShipTraversesLinkedWaypoint(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BlockPos groundedFirst = helper.absolutePos(new BlockPos(2, 1, 1));
            BlockPos groundedSecond = helper.absolutePos(new BlockPos(7, 1, 1));
            BlockPos mountedFirst = helper.absolutePos(new BlockPos(2, 1, 5));
            BlockPos mountedSecond = helper.absolutePos(new BlockPos(7, 1, 5));
            linkWaypoints(helper, groundedFirst, groundedSecond);
            linkWaypoints(helper, mountedFirst, mountedSecond);
            BasicEntityShip grounded = ship(helper, context, new Vec3(3.5D, 1D, 2.5D));
            BasicEntityShip mounted = ship(helper, context, new Vec3(3.5D, 1D, 6.5D));
            BasicEntityMount mount = mount(helper, context, mounted);
            helper.runAtTickTime(20, () -> context.stage(() -> {
                guardBlock(helper, grounded, groundedFirst);
                guardBlock(helper, mounted, mountedFirst);
            }));
            helper.runAtTickTime(160, () -> context.finish(() -> {
                helper.assertTrue(guardsBlock(grounded, groundedSecond),
                        "control (not mounted) did not advance to the linked waypoint: guard="
                                + guardPos(grounded));
                helper.assertTrue(mounted.getVehicle() == mount, "The ship must stay on its mount");
                helper.assertTrue(guardsBlock(mounted, mountedSecond),
                        "a ship on a mount did not advance to the linked waypoint: guard=" + guardPos(mounted)
                                + " expected=" + mountedSecond.toShortString() + " first=" + mountedFirst.toShortString()
                                + " distSq=" + mounted.distanceToSqr(Vec3.atCenterOf(mountedFirst))
                                + " check=" + mounted.lastWaypointCheck()
                                + " block=" + helper.getLevel().getBlockState(mountedFirst)
                                + " entity=" + helper.getLevel().getBlockEntity(mountedFirst));
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    /**
     * A ship on a mount whose FollowMin is 2 walks A to B on its mount, and the mount comes close enough to B
     * for the route to go on to C.
     */
    @GameTest(template = "arena", batch = "isolated_mount_movement_waypoint_route", timeoutTicks = 300)
    public static void mountedShipWithFollowMinTwoPassesTheMiddleWaypoint(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BlockPos first = helper.absolutePos(new BlockPos(2, 1, 5));
            BlockPos second = helper.absolutePos(new BlockPos(9, 1, 5));
            BlockPos third = helper.absolutePos(new BlockPos(2, 1, 7));
            linkWaypoints(helper, first, second);
            linkWaypoints(helper, second, third);
            BasicEntityShip ship = ship(helper, context, new Vec3(3.5D, 1D, 6.5D));
            ship.setStateMinor(ID.M.FollowMin, 2);
            ship.setStateMinor(ID.M.FollowMax, 3);
            BasicEntityMount mount = mount(helper, context, ship);
            helper.runAtTickTime(20, () -> context.stage(() -> guardBlock(helper, ship, first)));
            helper.runAtTickTime(260, () -> context.finish(() -> {
                helper.assertTrue(ship.getVehicle() == mount, "The ship must stay on its mount");
                helper.assertTrue(guardsBlock(ship, third),
                        "a ship on a mount did not pass the middle waypoint: guard=" + guardPos(ship)
                                + " check=" + ship.lastWaypointCheck()
                                + " block=" + helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 1, 5)))
                                + " entity=" + helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 5)))
                                + " firstX=" + helper.absolutePos(new BlockPos(2, 1, 5)).getX()
                                + " distSqToMiddle=" + mount.distanceToSqr(Vec3.atCenterOf(second)));
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    /**
     * Ships on a mount whose FollowMin is 2 and 4 pass the middle waypoint with the linked points ten blocks
     * apart. The mount is wide: the guard goal used to stop it short of the arrival range there.
     */
    @GameTest(template = "arena", batch = "isolated_mount_movement_waypoint_through", timeoutTicks = 300)
    public static void mountedShipsWithLargeFollowMinPassTheMiddleWaypoint(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            int[] followMins = {2, 4};
            BasicEntityShip[] ships = new BasicEntityShip[2];
            BasicEntityMount[] mounts = new BasicEntityMount[2];
            BlockPos[] firsts = new BlockPos[2];
            BlockPos[] seconds = new BlockPos[2];
            BlockPos[] thirds = new BlockPos[2];
            for (int i = 0; i < 2; i++) {
                firsts[i] = helper.absolutePos(new BlockPos(0, 1, 1 + 4 * i));
                seconds[i] = helper.absolutePos(new BlockPos(10, 1, 1 + 4 * i));
                thirds[i] = helper.absolutePos(new BlockPos(0, 1, 3 + 4 * i));
                linkWaypoints(helper, firsts[i], seconds[i]);
                linkWaypoints(helper, seconds[i], thirds[i]);
                ships[i] = ship(helper, context, new Vec3(5.5D, 1D, 2.5D + 4 * i));
                ships[i].setStateMinor(ID.M.FollowMin, followMins[i]);
                ships[i].setStateMinor(ID.M.FollowMax, followMins[i] + 1);
                helper.assertTrue(ships[i].getStateMinor(ID.M.FollowMin) == followMins[i]
                                && ships[i].getStateMinor(ID.M.FollowMax) == followMins[i] + 1,
                        "Fixture: the ship's FollowMin and FollowMax must be set");
                mounts[i] = mount(helper, context, ships[i]);
            }
            helper.runAtTickTime(20, () -> context.stage(() -> {
                for (int i = 0; i < 2; i++) {
                    guardBlock(helper, ships[i], firsts[i]);
                }
            }));
            helper.runAtTickTime(240, () -> context.finish(() -> {
                StringBuilder report = new StringBuilder();
                boolean allPassed = true;
                for (int i = 0; i < 2; i++) {
                    boolean passed = guardsBlock(ships[i], thirds[i]);
                    allPassed &= passed;
                    report.append(" FollowMin=").append(followMins[i]).append(" passed=").append(passed)
                            .append(" guard=").append(guardPos(ships[i])).append(" distSq=")
                            .append(String.format(java.util.Locale.ROOT, "%.2f",
                                    mounts[i].distanceToSqr(Vec3.atCenterOf(seconds[i]))))
                            .append(" last=").append(ships[i].lastWaypointCheck()).append(';');
                }
                helper.assertTrue(allPassed, "A ship on a mount did not pass the middle waypoint:" + report);
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    private static void linkWaypoints(GameTestHelper helper, BlockPos first, BlockPos second) {
        for (BlockPos pos : java.util.List.of(first, second)) {
            if (!(helper.getLevel().getBlockEntity(pos) instanceof TileEntityWaypoint)) {
                helper.getLevel().setBlock(pos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
            }
        }
        if (!(helper.getLevel().getBlockEntity(first) instanceof TileEntityWaypoint waypoint)) {
            throw new AssertionError("Waypoint block did not create its block entity.");
        }
        waypoint.setNextWaypoint(second);
    }

    private static void guardBlock(GameTestHelper helper, BasicEntityShip ship, BlockPos pos) {
        var id = helper.getLevel().dimension().location();
        ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                        new DimensionKey(id.getNamespace(), id.getPath()),
                        new CommandPos(pos.getX(), pos.getY(), pos.getZ()), false)));
    }

    private static boolean guardsBlock(BasicEntityShip ship, BlockPos pos) {
        return ship.getGuardedPos(0) == pos.getX() && ship.getGuardedPos(1) == pos.getY()
                && ship.getGuardedPos(2) == pos.getZ();
    }

    private static String guardPos(BasicEntityShip ship) {
        return "(" + ship.getGuardedPos(0) + "," + ship.getGuardedPos(1) + "," + ship.getGuardedPos(2) + ")";
    }

    private static void close(GameTestHelper helper, String stage, BasicEntityShip grounded, Cow groundedCow,
                              BasicEntityMount mount, Cow mountedCow) {
        double groundedDist = grounded.distanceTo(groundedCow);
        double mountedDist = mount.distanceTo(mountedCow);
        helper.assertTrue(groundedDist < 5D, "control (not mounted) failed at " + stage + ": dist=" + groundedDist);
        helper.assertTrue(mountedDist < 5D, "mount did not keep up at " + stage + ": mountDist=" + mountedDist
                + " controlDist=" + groundedDist + " stillRiding=" + (mount.getPassengers().size() == 1));
    }

    private static void move(GameTestHelper helper, BasicEntityShip ship, BlockPos relative) {
        BlockPos destination = helper.absolutePos(relative);
        var id = helper.getLevel().dimension().location();
        ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                new CommandStateOp.Apply(new ShipCommand.Move(new DimensionKey(id.getNamespace(), id.getPath()),
                        new CommandPos(destination.getX(), destination.getY(), destination.getZ()), true)));
    }

    private static void guard(BasicEntityShip ship, Entity target) {
        ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                new CommandStateOp.Apply(new ShipCommand.GuardEntity(ShipCommandStateAdapter.handle(target))));
    }

    private static BasicEntityShip ship(GameTestHelper helper, Context context, Vec3 position) {
        BasicEntityShip ship = context.entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "failed to create ship");
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PickItem, false);
        ship.setStateMinor(ID.M.GuardType, 0);
        ship.setStateMinor(ID.M.FormatType, 0);
        ship.setStateMinor(ID.M.FollowMin, 1);
        ship.setStateMinor(ID.M.FollowMax, 2);
        ship.calcShipAttributes(31, false);
        ship.moveTo(helper.absoluteVec(position));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add ship");
        return ship;
    }

    private static BasicEntityMount mount(GameTestHelper helper, Context context, BasicEntityShip ship) {
        BasicEntityMount mount = context.entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
        mount.moveTo(ship.position());
        helper.getLevel().addFreshEntity(mount);
        mount.setHost(ship);
        helper.assertTrue(ship.startRiding(mount, true), "fixture must mount the ship");
        return mount;
    }

    private static Cow cow(GameTestHelper helper, Context context, Vec3 position) {
        Cow cow = context.entities.add(EntityType.COW.create(helper.getLevel()));
        helper.assertTrue(cow != null, "failed to create cow");
        cow.setNoAi(true);
        cow.moveTo(helper.absoluteVec(position));
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "failed to add cow");
        return cow;
    }

    private static WrappedGoal goal(Mob mob, Class<?> goalType) {
        return selector(mob).getAvailableGoals().stream()
                .filter(wrapped -> goalType.isInstance(wrapped.getGoal()))
                .findFirst().orElse(null);
    }

    private static GoalSelector selector(Mob mob) {
        try {
            Field field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Failed to inspect goalSelector", error);
        }
    }

    private static final class Context implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final ShipAiAuthorityOverride authority;
        private boolean closed;

        private Context(GameTestHelper helper, GameTestEntities entities, ShipAiAuthorityOverride authority) {
            this.helper = helper;
            this.entities = entities;
            this.authority = authority;
        }

        static Context open(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
            ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
            try {
                return new Context(helper, GameTestEntities.open(helper), authority);
            } catch (RuntimeException | Error failure) {
                authority.close();
                throw failure;
            }
        }

        void stage(Runnable action) {
            try {
                action.run();
            } catch (Throwable error) {
                close();
                throw error;
            }
        }

        void finish(Runnable assertions) {
            try {
                assertions.run();
                this.helper.succeed();
            } finally {
                close();
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                this.entities.close();
            } finally {
                this.authority.close();
            }
        }
    }
}
