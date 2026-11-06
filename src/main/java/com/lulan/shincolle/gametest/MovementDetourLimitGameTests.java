package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.FollowMovePlanner;
import com.lulan.shincolle.ai.domain.movement.FollowRange;
import com.lulan.shincolle.ai.domain.movement.GuardMovePlanner;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import com.lulan.shincolle.ai.domain.movement.TeleportRule;
import com.lulan.shincolle.ai.path.ShipPathNavigation;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Where a search reaches its node limit, searching the detour points together picks other points than searching
 * them one at a time. The detour searches them one at a time, as it always chose, and spreads the searches that
 * reach nothing over ticks, one a tick. Each scene stands in the open sky, far from the other tests, and is taken
 * down at the end.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementDetourLimitGameTests {
    private static final TicketType<ChunkPos> TERRAIN_TICKET =
            TicketType.create("shincolle_kai_gametest_detour_terrain", Comparator.comparingLong(ChunkPos::toLong));
    private static final Vec3 SHIP = new Vec3(2.5D, 0D, 2.5D);
    /** How far above the arena the scene stands. */
    private static final int LIFT = 60;
    /** The farthest the destination stands from the ship in any scene. */
    private static final int MAX_DISTANCE = 61;
    private static final int LAND_HALF_WIDTH = 48;
    private static final int WATER_HALF_WIDTH = 24;
    private static final int WATER_DEPTH = 8;
    /** Ticks the detour gets to decide: sixteen points one a tick, then the destination itself. */
    private static final int DECIDE_TICKS = 20;
    /** The point the detour tries first: two blocks east of the destination. */
    private static final int FIRST = 0;
    private static final int CENTER = -1;
    /** Open land this far off leaves the first point's search short of it, so the detour goes on looking. */
    private static final int PENDING_DISTANCE = 40;
    /** The default teleport rule; a far timer at its cooldown teleports on the next far goal tick. */
    private static final int TELEPORT_COOLDOWN = 200;
    private static final TeleportRule TELEPORT_RULE = new TeleportRule(true, TELEPORT_COOLDOWN, 256);

    private MovementDetourLimitGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_land_first_open", timeoutTicks = 200)
    public static void landDetourTakesTheFirstPointThatItsOwnSearchReaches(GameTestHelper helper) {
        // together the points run out of nodes; the first point's own search reaches it
        scene(helper, false, true, 26, FIRST);
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_water_first_open", timeoutTicks = 200)
    public static void waterDetourTakesTheFirstPointThatItsOwnSearchReaches(GameTestHelper helper) {
        scene(helper, true, true, 44, FIRST);
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_water_first_unreached", timeoutTicks = 200)
    public static void waterDetourGoesToTheCenterWhenThePointsOwnSearchFallsShort(GameTestHelper helper) {
        // the other way round: together they reach the first point, its own search does not
        scene(helper, true, true, 58, CENTER);
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_land_all_open", timeoutTicks = 200)
    public static void landDetourSpreadsSearchesThatReachNothingOverTicks(GameTestHelper helper) {
        // every point stands on open ground too far to reach within the limit: sixteen searches, one a tick
        scene(helper, false, false, 40, CENTER);
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_follow_teleport", timeoutTicks = 200)
    public static void laterTeleportInTheSamePlanCancelsPendingDetour(GameTestHelper helper) {
        // the follow planner asks for a detour and, the owner having been far too long, a teleport after it
        scene(helper, false, Scene::followThenTeleport, scene -> scene.verifyDropped("a later teleport"));
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_guard_teleport", timeoutTicks = 200)
    public static void laterGuardTeleportInTheSamePlanCancelsPendingDetour(GameTestHelper helper) {
        scene(helper, false, Scene::guardThenTeleport, scene -> scene.verifyDropped("a later teleport"));
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_limit_death", timeoutTicks = 200)
    public static void deathDropsPendingDetour(GameTestHelper helper) {
        // a ship lies dead for a while before it is removed; its detour goes no further
        scene(helper, false, Scene::dieWhileLooking, scene -> scene.verifyDropped("death"));
    }

    private static void scene(GameTestHelper helper, boolean water, boolean onlyFirstOpen, int distance,
                              int expected) {
        scene(helper, water, scene -> scene.start(distance, onlyFirstOpen, expected), Scene::verify);
    }

    private static void scene(GameTestHelper helper, boolean water, Consumer<Scene> start, Consumer<Scene> verify) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, SHIP);
            // wandering would ask for its own path
            GameTestEntities.removeGoalsEachTick(helper, ship, goal -> goal instanceof ShipWanderGoal);
            BlockPos origin = BlockPos.containing(MovementPlanParityGameTests.ground(helper, SHIP)).above(LIFT);
            Scene scene = new Scene(helper, ship, origin, water);
            GameTestEntities.onCompletion(helper, scene::takeDown, "detour terrain removal");
            scene.build();
            helper.runAfterDelay(1, () -> scene.guard(() -> start.accept(scene)));
            helper.runAfterDelay(1 + DECIDE_TICKS, () -> scene.guard(() -> {
                helper.assertTrue(ship.tickCount > 16 && MovementPlanParityGameTests.selector(ship)
                                .getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof ShipWanderGoal),
                        "fixture: the ship must have no wander goal after it registers its goals again, tick "
                                + ship.tickCount);
                verify.accept(scene);
                helper.succeed();
            }));
        }, SHIP);
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final BasicEntityShip ship;
        private final BlockPos origin;
        private final boolean water;
        private final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        private final Set<ChunkPos> held = new LinkedHashSet<>();
        private final List<String> searches = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();
        private List<MovementPoint> points = List.of();
        private MovementPoint center;
        private int oneByOne;
        private int expected;
        private boolean down;

        Scene(GameTestHelper helper, BasicEntityShip ship, BlockPos origin, boolean water) {
            this.helper = helper;
            this.ship = ship;
            this.origin = origin;
            this.water = water;
        }

        void guard(Runnable step) {
            try {
                step.run();
            } catch (GameTestAssertException failure) {
                this.takeDown();
                throw failure;
            } catch (RuntimeException error) {
                this.takeDown();
                throw new GameTestAssertException("detour scene failed: " + error);
            }
        }

        private ServerLevel level() {
            return this.helper.getLevel();
        }

        private int minX() {
            return this.water ? -11 : -12;
        }

        private int maxX() {
            return MAX_DISTANCE + (this.water ? 11 : 12);
        }

        private int halfWidth() {
            return this.water ? WATER_HALF_WIDTH + 1 : LAND_HALF_WIDTH;
        }

        void build() {
            for (int cx = (this.origin.getX() + this.minX()) >> 4; cx <= (this.origin.getX() + this.maxX()) >> 4; cx++) {
                for (int cz = (this.origin.getZ() - this.halfWidth()) >> 4;
                     cz <= (this.origin.getZ() + this.halfWidth()) >> 4; cz++) {
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    this.level().getChunkSource().addRegionTicket(TERRAIN_TICKET, chunk, 1, chunk);
                    this.held.add(chunk);
                    this.level().getChunk(cx, cz);
                }
            }
            if (this.water) {
                int inner = WATER_HALF_WIDTH;
                for (int x = this.minX(); x <= this.maxX(); x++) {
                    for (int z = -inner - 1; z <= inner + 1; z++) {
                        for (int y = -WATER_DEPTH; y <= 1; y++) {
                            boolean shell = x == this.minX() || x == this.maxX() || Math.abs(z) > inner
                                    || y == -WATER_DEPTH;
                            if (shell) {
                                this.place(this.origin.offset(x, y, z), Blocks.GLASS.defaultBlockState());
                            } else if (y <= 0) {
                                this.place(this.origin.offset(x, y, z), Blocks.WATER.defaultBlockState());
                            }
                        }
                    }
                }
            } else {
                for (int x = this.minX(); x <= this.maxX(); x++) {
                    for (int z = -LAND_HALF_WIDTH; z <= LAND_HALF_WIDTH; z++) {
                        this.place(this.origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
            this.stand();
        }

        private void place(BlockPos pos, BlockState state) {
            BlockState before = this.level().getBlockState(pos);
            if (!this.placed.containsKey(pos)) {
                this.helper.assertTrue(before.isAir(), "the sky is not empty at " + pos + ": " + before);
                this.placed.put(pos.immutable(), before);
            }
            this.level().setBlock(pos, state, 2);
        }

        /** The ship stands still at the scene's start, in the water's top layer or on the stone. */
        private void stand() {
            MovementPlanParityGameTests.navigation(this.ship).stop();
            this.ship.setNoGravity(this.water);
            this.ship.moveTo(this.origin.getX() + 0.5D, this.origin.getY(), this.origin.getZ() + 0.5D, 0F, 0F);
            this.ship.setDeltaMovement(Vec3.ZERO);
        }

        void start(int distance, boolean onlyFirstOpen, int expected) {
            this.prepare(distance, onlyFirstOpen, expected);
            ShipMovementExecutor.run(this.ship, MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF,
                    new MovementTarget.Around(this.center), 1D, MovementReason.STUCK_DETOUR)));
        }

        /** A follow plan, as the planner makes it: a detour beside the owner, then a teleport to the owner. */
        void followThenTeleport() {
            this.prepare(PENDING_DISTANCE, false, CENTER);
            PlannedMove<MovementState.Follow> planned = FollowMovePlanner.move(
                    new MovementState.Follow(32, 21, TELEPORT_COOLDOWN, 0, this.center, this.center),
                    new FollowMovePlanner.Facts(PENDING_DISTANCE * PENDING_DISTANCE, new FollowRange(4D, 7D),
                            Optional.of(this.center), ShipCommandStateAdapter.handle(this.ship).dimension(),
                            TELEPORT_RULE, this.stuck()));
            this.runThenTeleport(planned.plan());
        }

        /** A guard plan, as the planner makes it: a detour beside the guarded point, then a teleport to it. */
        void guardThenTeleport() {
            this.prepare(PENDING_DISTANCE, false, CENTER);
            PlannedMove<MovementState.Guard> planned = GuardMovePlanner.move(
                    new MovementState.Guard(32, 0, TELEPORT_COOLDOWN, 0, this.center, this.center, true),
                    new GuardMovePlanner.Facts(PENDING_DISTANCE * PENDING_DISTANCE, new FollowRange(4D, 7D), false,
                            ShipCommandStateAdapter.handle(this.ship).dimension(), TELEPORT_RULE, this.stuck()));
            this.runThenTeleport(planned.plan());
        }

        /** The second window stuck in a row, seen this goal tick: the stage that asks for a detour. */
        private StuckState stuck() {
            return new StuckState(true, new MovementPoint(this.ship.getX(), this.ship.getY(), this.ship.getZ()),
                    this.ship.tickCount, 2, true);
        }

        private void runThenTeleport(MovementPlan plan) {
            List<MovementStep> steps = plan.steps();
            this.helper.assertTrue(steps.size() == 2 && steps.get(0) instanceof MovementStep.PathTo path
                            && path.target() instanceof MovementTarget.Around
                            && steps.get(1) instanceof MovementStep.Teleport,
                    "fixture: the planner must ask for a detour and then a teleport, got " + steps);
            ShipMovementExecutor.run(this.ship, plan);
            this.helper.assertTrue(this.ship.getX() == this.center.x() && this.ship.getZ() == this.center.z(),
                    "fixture: the teleport must be carried out, the ship is at " + this.ship.position());
            this.assertLeftLooking();
        }

        /** A detour left looking for its point, then the ship dies; it lies dead until it is removed. */
        void dieWhileLooking() {
            this.prepare(PENDING_DISTANCE, false, CENTER);
            ShipMovementExecutor.run(this.ship, MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF,
                    new MovementTarget.Around(this.center), 1D, MovementReason.STUCK_DETOUR)));
            this.assertLeftLooking();
            this.ship.setHealth(0F);
            this.helper.assertTrue(!this.ship.isAlive() && !this.ship.isRemoved(),
                    "fixture: the ship must lie dead and not yet be removed");
        }

        private void assertLeftLooking() {
            this.helper.assertTrue(this.searches.size() == 1 && this.searches.get(0).endsWith("=partial"),
                    "fixture: the first point's search must reach nothing and leave the detour looking, got "
                            + this.searches);
        }

        /** Nothing more of the detour: no further search and no path asked for. */
        void verifyDropped(String after) {
            ShipPathNavigation navigation = MovementPlanParityGameTests.navigation(this.ship);
            navigation.recordSearches(null);
            navigation.recordCalls(null);
            boolean moved = this.calls.stream().anyMatch(call -> call.contains(":moveTo("));
            this.helper.assertTrue(this.searches.size() == 1 && !moved,
                    "the detour went on after " + after + "; searches " + this.searches + "; calls " + this.calls);
            this.takeDown();
        }

        /** The scene up to where the detour would begin, with the navigation's searches and calls recorded. */
        private void prepare(int distance, boolean onlyFirstOpen, int expected) {
            this.stand();
            this.expected = expected;
            this.center = new MovementPoint(this.origin.getX() + distance + 0.5D, this.origin.getY(),
                    this.origin.getZ() + 0.5D);
            this.points = StuckDetector.detourPoints(this.center);
            if (onlyFirstOpen) {
                // every point but the first stands inside a wall two blocks high
                for (int i = FIRST + 1; i < this.points.size(); i++) {
                    BlockPos foot = block(this.points.get(i));
                    for (BlockPos wall : List.of(foot, foot.above())) {
                        this.placed.putIfAbsent(wall.immutable(), this.level().getBlockState(wall));
                        this.level().setBlock(wall, Blocks.BARRIER.defaultBlockState(), 2);
                    }
                }
            }
            ShipPathNavigation navigation = MovementPlanParityGameTests.navigation(this.ship);
            this.oneByOne = this.oneByOne(navigation);
            this.helper.assertTrue(this.oneByOne == expected, "fixture: one search per point picks " + this.oneByOne
                    + ", the scene expects " + expected);
            navigation.stop();
            navigation.recordSearches(this.searches);
            navigation.recordCalls(this.calls);
        }

        /** The detour as it always chose: each point's own search in order, the first that reaches. */
        private int oneByOne(ShipPathNavigation navigation) {
            for (int i = 0; i < this.points.size(); i++) {
                MovementPoint point = this.points.get(i);
                double dx = point.x() - this.ship.getX();
                double dz = point.z() - this.ship.getZ();
                if (dx * dx + dz * dz < 1.5D * 1.5D || !this.level().hasChunkAt(block(point))) continue;
                Path path = navigation.createPath(block(point), 0);
                if (path != null && path.canReach()) return i;
            }
            return CENTER;
        }

        void verify() {
            ShipPathNavigation navigation = MovementPlanParityGameTests.navigation(this.ship);
            navigation.recordSearches(null);
            navigation.recordCalls(null);
            String last = null;
            for (String call : this.calls) {
                if (call.contains(":moveTo(")) last = call;
            }
            this.helper.assertTrue(last != null, "the detour never asked for a path; searches " + this.searches);
            MovementPoint chosen = this.expected == CENTER ? this.center : this.points.get(this.expected);
            this.helper.assertTrue(last.contains(":moveTo(" + coords(chosen) + ")"),
                    "expected the detour to go to " + coords(chosen) + " as one search per point does, got " + last
                            + "; searches " + this.searches);
            Map<String, Integer> failedPerTick = new HashMap<>();
            for (String search : this.searches) {
                if (search.endsWith("=reached")) continue;
                failedPerTick.merge(search.substring(0, search.indexOf(':')), 1, Integer::sum);
            }
            this.helper.assertTrue(failedPerTick.values().stream().allMatch(count -> count <= 1),
                    "expected at most one search that reaches nothing a tick, got " + this.searches);
            this.takeDown();
        }

        void takeDown() {
            if (this.down) return;
            this.down = true;
            MovementPlanParityGameTests.navigation(this.ship).recordSearches(null);
            MovementPlanParityGameTests.navigation(this.ship).recordCalls(null);
            List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(this.placed.entrySet());
            for (Map.Entry<BlockPos, BlockState> entry : entries) {
                this.level().setBlock(entry.getKey(), entry.getValue(), 2);
            }
            this.placed.clear();
            for (ChunkPos chunk : this.held) {
                this.level().getChunkSource().removeRegionTicket(TERRAIN_TICKET, chunk, 1, chunk);
            }
            this.held.clear();
        }

        private static BlockPos block(MovementPoint point) {
            return BlockPos.containing(point.x(), point.y(), point.z());
        }

        private static String coords(MovementPoint point) {
            return MovementPlanParityGameTests.fmt(point.x()) + "," + MovementPlanParityGameTests.fmt(point.y()) + ","
                    + MovementPlanParityGameTests.fmt(point.z());
        }
    }
}
