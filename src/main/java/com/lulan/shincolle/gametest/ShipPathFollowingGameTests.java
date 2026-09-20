package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Regression fixtures for a ship that has already overshot its current waypoint. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPathFollowingGameTests {

    private ShipPathFollowingGameTests() {
    }

    @GameTest(template = "arena", batch = "ship_path_following")
    public static void moveOrderReachesLandDestinationAfterOvershoot(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities);
            // Fix the measured MOV=0.6 scenario independently of default morale/config buffs.
            ship.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.6D);
            BlockPos destination = helper.absolutePos(new BlockPos(9, 2, 2));
            ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                    helper.getLevel().dimension(), 0);
            ship.setStateFlag(ID.F.CanFollow, false);
            ShipGuardingGoal goal = new ShipGuardingGoal(ship);
            helper.assertTrue(goal.canUse(), "Fixture must activate the real guarding goal");
            goal.start();
            // Explicitly drive the production goal: no dependency on the tick-16 AI registration.
            for (int tick = 0; tick < 10; tick++) {
                ship.tickCount++;
                goal.tick();
            }
            helper.assertTrue(ship.getNavigation().getPath() != null, "Guard move must generate a land path");
            // Freeze the causal input: the ship crossed several waypoints in a fast movement step.
            // Keep the original guard cooldown; no replan or teleport may stand in for following.
            Path path = straightPath(helper, 1, 9);
            helper.assertTrue(ship.getNavigation().moveTo(path, 1D), "Overshot path must be accepted");
            moveTo(helper, ship, 5.5D, 2D, 2.5D);
            ship.setOnGround(true);
            ship.setYRot(-90F);
            double minDistance = ship.getStateMinor(ID.M.FollowMin) + ship.getBbWidth() * 0.75F;
            double minDistSq = minDistance * minDistance;
            double closest = ship.distanceToSqr(Vec3.atCenterOf(destination));
            double maxStep = 0D;
            for (int tick = 0; tick < 8; tick++) {
                ship.tickCount++;
                goal.tick();
                helper.assertTrue(ship.getNavigation().getPath() == path,
                        "Arrival must use the same path, without replanning or teleport recovery");
                ship.getNavigation().tick();
                ship.getMoveControl().tick();
                Vec3 before = ship.position();
                ship.travel(new Vec3(ship.xxa, ship.yya, ship.zza));
                maxStep = Math.max(maxStep, ship.position().subtract(before).horizontalDistance());
                closest = Math.min(closest, ship.distanceToSqr(Vec3.atCenterOf(destination)));
                if (closest <= minDistSq) {
                    helper.assertTrue(maxStep > 0.4D, "Fixture must move farther than the old waypoint tolerance");
                    helper.succeed();
                    return;
                }
            }
            helper.fail("Land move did not arrive within 8 follow ticks after overshoot: closestDistSq="
                    + closest + " minDistSq=" + minDistSq + " nextNode=" + path.getNextNodeIndex()
                    + " maxStep=" + maxStep);
        }
    }

    @GameTest(template = "arena", batch = "ship_path_following")
    public static void pathIndexAdvancesWithoutReturningToMissedNodes(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities);
            Path path = straightPath(helper, 1, 9);
            helper.assertTrue(ship.getNavigation().moveTo(path, 1D), "Land path must be accepted");
            int previous = path.getNextNodeIndex();
            int unchanged = 0;
            // Sample a forward trajectory with steps larger than the old acceptance window.
            for (double x : new double[]{5.5D, 7.1D, 8.7D, 9.5D}) {
                moveTo(helper, ship, x, 2D, 2.5D);
                ship.getNavigation().tick();
                int current = path.getNextNodeIndex();
                helper.assertTrue(current >= previous, "Index moved backwards: " + previous + " -> " + current);
                boolean missedNode = !path.isDone()
                        && path.getNextNodePos().getX() + 0.5D < ship.getX() - 0.75D;
                unchanged = current == previous && missedNode ? unchanged + 1 : 0;
                helper.assertTrue(unchanged < 2,
                        "Index stayed on a missed node for 2 forward samples: index=" + current);
                previous = current;
            }
            helper.assertTrue(path.isDone(), "Forward traversal must finish the original path");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "ship_path_following")
    public static void dryLandLookaheadRespectsWallsAndHeightChanges(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities);
            helper.assertTrue(!ship.isInWaterOrBubble() && !ship.isInLava(), "Fixture must be outside liquids");
            Path path = straightPath(helper, 1, 9);
            ship.getNavigation().moveTo(path, 1D);
            ship.getNavigation().tick();
            helper.assertTrue(path.getNextNodeIndex() == 8,
                    "Dry-land lookahead must select the farthest clear node: expected=8 actual=" + path.getNextNodeIndex());

            // A wall blocks all later nodes; the farthest visible one is immediately before it.
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(6, y, 2), Blocks.STONE);
            }
            ship.getNavigation().stop();
            path = straightPath(helper, 1, 9);
            ship.getNavigation().moveTo(path, 1D);
            ship.getNavigation().tick();
            helper.assertTrue(path.getNextNodeIndex() == 4, "Lookahead must stop before the wall");
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(6, y, 2), Blocks.AIR);
            }

            ship.getNavigation().stop();
            path = straightPath(helper, 1, 9);
            Node raised = path.getNode(4);
            path.replaceNode(4, new Node(raised.x, raised.y + 1, raised.z));
            ship.getNavigation().moveTo(path, 1D);
            ship.getNavigation().tick();
            helper.assertTrue(path.getNextNodeIndex() == 3, "Lookahead must not cross the first height change");

            // Arrival is XZ-only and retains the original minimum tolerance, including the final node.
            ship.getNavigation().stop();
            path = straightPath(helper, 1, 1);
            ship.getNavigation().moveTo(path, 1D);
            moveTo(helper, ship, 2.1D, 3.5D, 2.5D);
            ship.getNavigation().tick();
            helper.assertTrue(path.isDone(), "XZ arrival at offset 0.6 must ignore a 1.5-block Y difference");
            helper.assertTrue(ship.getNavigation().getMaxDistanceToWaypoint() == 0.75F,
                    "Debug waypoint tolerance must match the 0.75 minimum");
            helper.succeed();
        }
    }

    @GameTest(template = "arena", batch = "ship_path_following")
    public static void waterLookaheadAdvancesAcrossSupportedSurface(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = createShip(helper, entities);
            for (int x = 1; x <= 10; x++) {
                for (int z = 1; z <= 4; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
                }
            }
            // A source fluid's surface is below the block top; intersect it explicitly.
            moveTo(helper, ship, 1.5D, 1.8D, 2.5D);
            ship.setOnGround(false);
            ship.baseTick();
            helper.assertTrue(ship.isInWater(), "Fixture must be in water without ground contact");
            Path path = straightPath(helper, 1, 9);
            helper.assertTrue(ship.getNavigation().moveTo(path, 1D), "Water path must be accepted");
            ship.getNavigation().tick();
            helper.assertTrue(path.getNextNodeIndex() == 8,
                    "Water support must allow farthest lookahead: expected=8 actual=" + path.getNextNodeIndex());
            moveTo(helper, ship, 9.5D, 1.8D, 2.5D);
            ship.getNavigation().tick();
            helper.assertTrue(path.isDone(), "Water traversal samples must finish the path");
            helper.succeed();
        }
    }

    private static BasicEntityShip createShip(GameTestHelper helper, GameTestEntities entities) {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 6; y++) {
                    boolean wall = x == 0 || x == 11 || z == 0 || z == 5;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        BasicEntityShip ship = entities.add(ModEntities.BB_RE.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create Re-class ship");
        ship.calcShipAttributes(31, false);
        helper.assertTrue(ship.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0D, "Ship must have initialized movement speed");
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateMinor(ID.M.FormatType, 0);
        // The arena is shorter than the default FollowMax=12 activation distance.
        ship.setStateMinor(ID.M.FollowMin, 1);
        ship.setStateMinor(ID.M.FollowMax, 2);
        ship.setStateFlag(ID.F.PickItem, false);
        ship.setStateFlag(ID.F.PassiveAI, true);
        moveTo(helper, ship, 1.5D, 2D, 2.5D);
        ship.setOnGround(true);
        return ship;
    }

    private static Path straightPath(GameTestHelper helper, int firstX, int lastX) {
        List<Node> nodes = new ArrayList<>();
        for (int x = firstX; x <= lastX; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 2));
            nodes.add(new Node(pos.getX(), pos.getY(), pos.getZ()));
        }
        return new Path(nodes, helper.absolutePos(new BlockPos(lastX, 2, 2)), true);
    }

    private static void moveTo(GameTestHelper helper, BasicEntityShip ship, double x, double y, double z) {
        ship.moveTo(helper.absoluteVec(new Vec3(x, y, z)));
    }
}
