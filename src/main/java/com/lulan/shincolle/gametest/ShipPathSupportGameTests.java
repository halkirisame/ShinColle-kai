package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPathSupportGameTests {

    private static final int[][] ROUTE = {
        {1, 2}, {2, 2}, {3, 2}, {3, 3}, {3, 4},
        {4, 4}, {5, 4}, {6, 4}, {7, 4}, {8, 4}, {9, 4}, {10, 4},
        {10, 3}, {10, 2},
    };

    private ShipPathSupportGameTests() {
    }

    @GameTest(template = "arena", batch = "ship_path_support")
    public static void shipNavigationFollowsSupportedDetour(GameTestHelper helper) {
        run(helper, "PROBE-NEW", null);
    }

    @GameTest(template = "arena", batch = "ship_path_support")
    public static void vanillaNavigationFollowsSupportedDetour(GameTestHelper helper) {
        run(helper, "PROBE-OLD", "vanilla");
    }

    private static void run(GameTestHelper helper, String label, String vanilla) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (int x = 0; x < 12; x++) {
                for (int z = 0; z < 6; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    for (int y = 2; y <= 6; y++) {
                        boolean wall = x == 0 || x == 11 || z == 0 || z == 5;
                        helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                    }
                }
            }
            // 床を完全に抜いた隙間。迂回車線 z=4 だけ残す。
            for (int x = 4; x <= 9; x++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.AIR);
                }
            }

            BasicEntityShip ship = entities.add(ModEntities.BB_RE.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Failed to create Re-class ship");
            ship.calcShipAttributes(31, false);
            ship.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1D);
            ship.setStateMinor(ID.M.NumGrudge, 1000);
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 2.5D)));
            ship.setOnGround(true);
            ship.setYRot(-90F);

            List<Node> nodes = new ArrayList<>();
            for (int[] step : ROUTE) {
                BlockPos pos = helper.absolutePos(new BlockPos(step[0], 2, step[1]));
                nodes.add(new Node(pos.getX(), pos.getY(), pos.getZ()));
            }
            Path path = new Path(nodes, helper.absolutePos(new BlockPos(10, 2, 2)), true);

            PathNavigation nav = vanilla == null
                    ? ship.getNavigation()
                    : new AmphibiousPathNavigation(ship, helper.getLevel());
            helper.assertTrue(nav.moveTo(path, 1D), "Detour path must be accepted");

            double startY = ship.getY();
            nav.tick();
            int chosen = path.getNextNodeIndex();

            double lowestY = startY;
            double maxStep = 0D;
            for (int tick = 0; tick < 400; tick++) {
                ship.tickCount++;
                nav.tick();
                ship.getMoveControl().tick();
                Vec3 before = ship.position();
                ship.travel(new Vec3(ship.xxa, ship.yya, ship.zza));
                maxStep = Math.max(maxStep, ship.position().subtract(before).horizontalDistance());
                lowestY = Math.min(lowestY, ship.getY());
            }

            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            String observation = label + " firstChosenNode=" + chosen + "/" + path.getNodeCount()
                    + " startY=" + startY + " lowestY=" + lowestY + " endY=" + ship.getY()
                    + " endX=" + (ship.getX() - origin.x) + " endZ=" + (ship.getZ() - origin.z)
                    + " maxStep=" + maxStep + " endNode=" + path.getNextNodeIndex();
            helper.assertTrue(chosen < 13 && lowestY >= startY - 0.01D && path.isDone(),
                    "Supported detour must avoid falling and finish: " + observation);
            helper.succeed();
        }
    }
}
