package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipHostileWanderGoal;
import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipMovementHost;
import com.lulan.shincolle.ai.ShipSitGoal;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Under NEW every movement goal stops and paths through its host's executor, so the executor's last
 * request explains the navigation call made on the same tick. Under LEGACY the executor is never used.
 * The goals here are the ones whose moves are not compared against LEGACY elsewhere: sitting (its
 * reason), a hostile ship's wander, a mount walking back to the ship it carries, and the stop that ends
 * a move order on arrival.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementExecutorBoundaryGameTests {
    private static final Vec3 SPOT = new Vec3(2.5D, 2D, 2.5D);
    private static final Vec3 AWAY = new Vec3(10.5D, 2D, 2.5D);
    private static final int ORDER_AT = 30;
    private static final int CHECK_AT = 60;

    private MovementExecutorBoundaryGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_movement_executor_sit", timeoutTicks = CHECK_AT + 40)
    public static void sitStopsThroughTheExecutorUnderNew(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
            GameTestEntities entities = GameTestEntities.open(helper);
            List<String> log = new ArrayList<>();
            Runnable close = () -> {
                try {
                    entities.close();
                } finally {
                    authority.close();
                }
            };
            BasicEntityShip[] ship = {null};
            guarded(close, () -> {
                ship[0] = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(2.5D, 0D, 2.5D));
                MovementPlanParityGameTests.navigation(ship[0]).recordCalls(log);
            });
            int start = (int) helper.getTick() + 1;
            helper.runAtTickTime(start + ORDER_AT, () -> guarded(close, () -> ship[0].applyCommandState(
                    new CommandIssuer.Player(UUID.randomUUID()),
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)))));
            helper.runAtTickTime(start + CHECK_AT, () -> guarded(close, () -> {
                MovementPlanParityGameTests.navigation(ship[0]).recordCalls(null);
                WrappedGoal sit = MovementPlanParityGameTests.selector(ship[0]).getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal() instanceof ShipSitGoal).findFirst().orElse(null);
                helper.assertTrue(sit != null && sit.isRunning(), "the sit goal is not running");
                ShipMovementExecutor.Request last = last(helper, ship[0]);
                helper.assertTrue(last.step() instanceof MovementStep.Stop stop && stop.reason() == MovementReason.SIT,
                        "the last request is not the sit's stop: " + last);
                helper.assertTrue(log.contains(last.tick() + ":stop"),
                        "no navigation stop on the tick of " + last + ": " + log);
                close.run();
                helper.succeed();
            }));
        }, SPOT);
    }

    @GameTest(template = "arena", batch = "isolated_movement_executor_hostile_wander", timeoutTicks = 40)
    public static void hostileWanderPathsThroughTheExecutorOnlyUnderNew(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            wander(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
            wander(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
            helper.succeed();
        }, SPOT);
    }

    @GameTest(template = "arena", batch = "isolated_movement_executor_mount", timeoutTicks = 40)
    public static void mountWalksToItsShipThroughTheExecutorOnlyUnderNew(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            mountToHost(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
            mountToHost(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
            helper.succeed();
        }, SPOT, AWAY);
    }

    @GameTest(template = "arena", batch = "isolated_movement_executor_move_arrived", timeoutTicks = 40)
    public static void completedMoveStopsThroughTheExecutorOnlyUnderNew(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            completedMove(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
            completedMove(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
            helper.succeed();
        }, SPOT);
    }

    private static void completedMove(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(2.5D, 0D, 2.5D));
            // the goal is driven by hand; the selector must not move the ship as well
            ship.setNoAi(true);
            BlockPos destination = helper.absolutePos(new BlockPos(1, 2, 1));
            if (mode == ConfigHandler.ShipAiTargetAuthority.LEGACY) {
                ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                        helper.getLevel().dimension(), 1);
                ship.setStateFlag(ID.F.CanFollow, false);
                ship.setReleaseGuardOnArrival(true);
            } else {
                var id = helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.Move(new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(destination.getX(), destination.getY(), destination.getZ()), true)));
            }
            helper.assertTrue(ship.shouldReleaseGuardOnArrival(), mode + ": the move order does not end on arrival");
            // a running guard goal in a selector of its own, which stops it once more when it ends
            GoalSelector selector = new GoalSelector(helper.getLevel().getProfilerSupplier());
            selector.addGoal(0, new ShipGuardingGoal(ship));
            WrappedGoal guard = selector.getAvailableGoals().iterator().next();
            guard.start();
            // a path already walked to the order's destination
            Path path = new Path(List.of(new Node(destination.getX(), destination.getY(), destination.getZ())),
                    destination, true);
            ship.getNavigation().stop();
            ship.getNavigation().moveTo(path, 1D);
            path.setNextNodeIndex(path.getNodeCount());
            List<String> log = new ExecutorMarkedLog();
            MovementPlanParityGameTests.navigation(ship).recordCalls(log);
            selector.tick();
            MovementPlanParityGameTests.navigation(ship).recordCalls(null);
            helper.assertTrue(!guard.isRunning(), mode + ": the guard goal kept running");
            helper.assertTrue(!ship.hasGuardDestination(), mode + ": the move order was not released");
            String stop = ship.tickCount + ":stop";
            List<String> expected = mode == ConfigHandler.ShipAiTargetAuthority.LEGACY
                    // the move's stop, the goal's stop, and the selector's stop of the goal
                    ? List.of(stop, stop, stop)
                    // the goal's stop and the move's, both by the executor; the selector's adds none
                    : List.of(stop + ExecutorMarkedLog.BY_EXECUTOR, stop + ExecutorMarkedLog.BY_EXECUTOR);
            helper.assertTrue(log.equals(expected), mode + ": expected " + expected + ", got " + log);
            Optional<ShipMovementExecutor.Request> last = ((ShipMovementHost) ship).shipMovementExecutor().last();
            if (mode == ConfigHandler.ShipAiTargetAuthority.LEGACY) {
                helper.assertTrue(last.isEmpty(), "LEGACY used the movement executor: " + last);
                return;
            }
            helper.assertTrue(last.isPresent() && last.get().tick() == ship.tickCount
                    && last.get().step() instanceof MovementStep.Stop arrived
                    && arrived.body() == MovementBody.SELF && arrived.reason() == MovementReason.MOVE_ARRIVED,
                    "the executor did not make the arrival's stop: " + last);
        }
    }

    private static void wander(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShipHostile ship = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "failed to create hostile ship");
            // the goal is driven by hand; the selector must not move the ship as well
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            ship.setPersistenceRequired();
            Vec3 at = MovementPlanParityGameTests.ground(helper, new Vec3(2.5D, 0D, 2.5D));
            ship.moveTo(at.x, at.y, at.z, 0F, 0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add hostile ship");
            ShipHostileWanderGoal goal = new ShipHostileWanderGoal(ship, 12, 1, 0.8D);
            boolean chosen = false;
            for (int attempt = 0; attempt < 5_000 && !chosen; attempt++) chosen = goal.canUse();
            helper.assertTrue(chosen, "hostile wander never chose a point");
            List<String> log = new ArrayList<>();
            MovementPlanParityGameTests.navigation(ship).recordCalls(log);
            goal.start();
            MovementPlanParityGameTests.navigation(ship).recordCalls(null);
            double x = MovementPlanParityGameTests.field(goal, "targetX");
            double y = MovementPlanParityGameTests.field(goal, "targetY");
            double z = MovementPlanParityGameTests.field(goal, "targetZ");
            String expected = ship.tickCount + ":moveTo(" + MovementPlanParityGameTests.fmt(x) + ","
                    + MovementPlanParityGameTests.fmt(y) + "," + MovementPlanParityGameTests.fmt(z) + ")x0.8=";
            helper.assertTrue(log.size() == 1 && log.get(0).startsWith(expected),
                    mode + ": expected one path to the chosen point " + expected + ", got " + log);
            Optional<ShipMovementExecutor.Request> last = ((ShipMovementHost) ship).shipMovementExecutor().last();
            if (mode == ConfigHandler.ShipAiTargetAuthority.LEGACY) {
                helper.assertTrue(last.isEmpty(), "LEGACY used the movement executor: " + last);
                return;
            }
            helper.assertTrue(last.isPresent() && last.get().tick() == ship.tickCount
                    && last.get().step() instanceof MovementStep.PathTo path
                    && path.reason() == MovementReason.WANDER
                    && path.target().equals(new MovementTarget.Point(new MovementPoint(x, y, z))),
                    "the executor did not make the wander's path: " + last);
        }
    }

    private static void mountToHost(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(2.5D, 0D, 2.5D));
            ship.setNoAi(true);
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            helper.assertTrue(mount != null, "failed to create mount");
            Vec3 at = MovementPlanParityGameTests.ground(helper, new Vec3(10.5D, 0D, 2.5D));
            mount.moveTo(at.x, at.y, at.z, 0F, 0F);
            mount.setInvulnerable(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(mount), "failed to add mount");
            mount.setHost(ship);
            // the goal is driven by hand; the selector must not move the mount as well
            mount.setNoAi(true);
            helper.assertTrue(mount.getPassengers().isEmpty() && mount.distanceToSqr(ship) > 16D,
                    "the mount must be empty and away from its ship");
            Goal goal = MovementPlanParityGameTests.selector(mount).getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getPriority() == 1
                            && wrapped.getGoal().getClass().getEnclosingClass() == BasicEntityMount.class)
                    .map(WrappedGoal::getGoal).findFirst().orElse(null);
            helper.assertTrue(goal != null, "the mount has no goal to walk back to its ship");
            helper.assertTrue(goal.canUse(), "the mount does not want to walk back to its ship");
            List<String> log = new ArrayList<>();
            MovementPlanParityGameTests.navigation(mount).recordCalls(log);
            goal.start();
            goal.tick();
            int pathTick = mount.tickCount;
            Optional<ShipMovementExecutor.Request> path = ((ShipMovementHost) mount).shipMovementExecutor().last();
            goal.stop();
            MovementPlanParityGameTests.navigation(mount).recordCalls(null);
            Optional<ShipMovementExecutor.Request> stopped = ((ShipMovementHost) mount).shipMovementExecutor().last();
            String walk = pathTick + ":moveTo(" + ship.getType().getDescriptionId() + "@";
            helper.assertTrue(log.size() == 2 && log.get(0).startsWith(walk) && log.get(1).equals(pathTick + ":stop"),
                    mode + ": expected a path to the ship and then a stop, got " + log);
            if (mode == ConfigHandler.ShipAiTargetAuthority.LEGACY) {
                helper.assertTrue(stopped.isEmpty(), "LEGACY used the movement executor: " + stopped);
                return;
            }
            helper.assertTrue(path.isPresent() && path.get().tick() == pathTick
                    && path.get().step() instanceof MovementStep.PathTo walkTo
                    && walkTo.reason() == MovementReason.MOUNT_TO_HOST
                    && walkTo.target() instanceof MovementTarget.Entity target
                    && target.handle().uuid().equals(ship.getUUID()),
                    "the executor did not make the mount's path: " + path);
            helper.assertTrue(stopped.isPresent() && stopped.get().step() instanceof MovementStep.Stop stop
                    && stop.reason() == MovementReason.GOAL_STOPPED,
                    "the executor did not make the mount's stop: " + stopped);
        }
    }

    private static ShipMovementExecutor.Request last(GameTestHelper helper, Mob host) {
        Optional<ShipMovementExecutor.Request> last = ((ShipMovementHost) host).shipMovementExecutor().last();
        helper.assertTrue(last.isPresent(), "NEW never used the movement executor");
        return last.get();
    }

    private static void guarded(Runnable close, Runnable body) {
        try {
            body.run();
        } catch (Throwable error) {
            close.run();
            throw error;
        }
    }

    /** A navigation call log that marks each call made from inside the movement executor. */
    private static final class ExecutorMarkedLog extends ArrayList<String> {
        static final String BY_EXECUTOR = "@executor";

        @Override
        public boolean add(String call) {
            boolean byExecutor = StackWalker.getInstance().walk(frames -> frames.anyMatch(
                    frame -> frame.getClassName().equals(ShipMovementExecutor.class.getName())));
            return super.add(byExecutor ? call + BY_EXECUTOR : call);
        }
    }
}
