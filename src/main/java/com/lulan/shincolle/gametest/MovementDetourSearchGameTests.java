package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.path.ShipPathNavigation;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A stuck ship's detour looks for the first point beside its destination it can reach, in the
 * detector's order, without one full search per point: a search that reaches nothing runs to the
 * search limit, so a detour runs at most one of those.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementDetourSearchGameTests {
    private static final Vec3 SHIP = new Vec3(2.5D, 0D, 2.5D);
    private static final Vec3 CENTER = new Vec3(8.5D, 0D, 2.5D);
    /** How far above the ground an unreachable destination hangs, like an owner flying. */
    private static final double ABOVE = 6D;
    /** The detour points walled up in the order test: two blocks east, south and west. */
    private static final int BLOCKED = 3;

    private MovementDetourSearchGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_search_unreachable", timeoutTicks = 100)
    public static void detourToUnreachablePointsSearchesOnceAndThenTheCenter(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = ship(helper, entities);
                Vec3 ground = MovementPlanParityGameTests.ground(helper, CENTER);
                MovementPoint center = new MovementPoint(ground.x, ground.y + ABOVE, ground.z);
                List<String> searches = new ArrayList<>();
                List<String> calls = new ArrayList<>();
                detour(ship, center, searches, calls);
                // one search for all the points together, then the path to the center as before
                helper.assertTrue(searches.size() <= 2, "expected at most 2 path searches, got " + searches);
                helper.assertTrue(!calls.isEmpty() && calls.get(calls.size() - 1).startsWith(moveTo(ship, center)),
                        "expected the last path to be to the center " + moveTo(ship, center) + ", got " + calls);
                helper.succeed();
            }
        }, SHIP, CENTER);
    }

    @GameTest(template = "arena", batch = "isolated_movement_detour_search_order", timeoutTicks = 100)
    public static void detourTakesTheFirstReachablePointInOrderWithOneFailedSearch(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            List<BlockPos> walls = new ArrayList<>();
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = ship(helper, entities);
                Vec3 ground = MovementPlanParityGameTests.ground(helper, CENTER);
                MovementPoint center = new MovementPoint(ground.x, ground.y, ground.z);
                List<MovementPoint> points = StuckDetector.detourPoints(center);
                // the first points in order stand inside walls two blocks high, so no path reaches them
                for (int i = 0; i < BLOCKED; i++) {
                    BlockPos foot = BlockPos.containing(points.get(i).x(), points.get(i).y(), points.get(i).z());
                    for (BlockPos wall : List.of(foot, foot.above())) {
                        helper.getLevel().setBlockAndUpdate(wall, Blocks.BARRIER.defaultBlockState());
                        walls.add(wall);
                    }
                }
                MovementPoint expected = points.get(BLOCKED);
                List<String> searches = new ArrayList<>();
                List<String> calls = new ArrayList<>();
                detour(ship, center, searches, calls);
                long failed = searches.stream().filter(search -> !search.endsWith("=reached")).count();
                helper.assertTrue(failed <= 1, "expected at most 1 search that reaches nothing, got " + searches);
                helper.assertTrue(!calls.isEmpty() && calls.get(calls.size() - 1).startsWith(moveTo(ship, expected)),
                        "expected the last path to be to the first open point " + moveTo(ship, expected)
                                + ", got " + calls);
                helper.succeed();
            } finally {
                for (BlockPos wall : walls) helper.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            }
        }, SHIP, CENTER);
    }

    /** A ship on the ground with no goals of its own, so only the detour paths it. */
    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, SHIP);
        ship.setNoAi(true);
        helper.assertTrue(ship.getNavigation().isDone(), "fixture ship already has a path");
        return ship;
    }

    private static void detour(BasicEntityShip ship, MovementPoint center, List<String> searches, List<String> calls) {
        ShipPathNavigation navigation = MovementPlanParityGameTests.navigation(ship);
        navigation.recordSearches(searches);
        navigation.recordCalls(calls);
        try {
            ShipMovementExecutor.run(ship, MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF,
                    new MovementTarget.Around(center), 1D, MovementReason.STUCK_DETOUR)));
        } finally {
            navigation.recordSearches(null);
            navigation.recordCalls(null);
        }
    }

    private static String moveTo(BasicEntityShip ship, MovementPoint point) {
        return ship.tickCount + ":moveTo(" + MovementPlanParityGameTests.fmt(point.x()) + ","
                + MovementPlanParityGameTests.fmt(point.y()) + "," + MovementPlanParityGameTests.fmt(point.z()) + ")";
    }
}
