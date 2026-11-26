package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SittingBuoyancyGameTests {
    private SittingBuoyancyGameTests() {}

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_sitting_buoyancy_depth", timeoutTicks = 120)
    public static void sittingShipKeepsItsDepthInStillWater(GameTestHelper helper) {
        observeWaterMovement(helper, true, 2.5D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_sitting_buoyancy_control", timeoutTicks = 120)
    public static void standingShipStillRisesInStillWater(GameTestHelper helper) {
        observeWaterMovement(helper, false, 2.5D);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_sitting_buoyancy_surface", timeoutTicks = 120)
    public static void sittingShipDoesNotSinkAtTheSurface(GameTestHelper helper) {
        observeWaterMovement(helper, true, 6.75D);
    }

    private static void observeWaterMovement(GameTestHelper helper, boolean sitting, double y) {
        Vec3 relative = new Vec3(4.5D, y, 4.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    for (int height = 0; height <= 9; height++) {
                        boolean inside = x > 0 && x < 8 && z > 0 && z < 8 && height > 0;
                        helper.setBlock(new BlockPos(x, height, z), !inside ? Blocks.GLASS
                                : height <= 6 ? Blocks.WATER : Blocks.AIR);
                    }
                }
            }
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip ship = entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
            ship.setPos(helper.absoluteVec(new Vec3(4.5D, 2.5D, 4.5D)));
            ship.setInvulnerable(true);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setEntitySit(true);
            // Isolate travel buoyancy from wandering, targets and the floating goal.
            GameTestEntities.removeAllGoalsEachTick(helper, ship);
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship must be added");
            int[] startTick = {0};
            double[] startY = {0D};
            helper.startSequence().thenIdle(24).thenExecute(() -> {
                GameTestEntities.assertRegistered(helper, ship);
                ship.setPos(helper.absoluteVec(relative));
                ship.setEntitySit(sitting);
                ship.setDeltaMovement(Vec3.ZERO);
                EntityHelper.updateShipDepth(ship);
                helper.assertTrue(ship.isInWater() && !ship.horizontalCollision
                                && ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel)
                                && ship.isOrderedToSit() == sitting && ship.getDeltaMovement().equals(Vec3.ZERO),
                        "Still-water fixture must retain fuel, sitting state and zero initial velocity");
                if (y < 3D) {
                    helper.assertTrue(ship.getShipDepth() >= 2D, "Deep fixture must have water above it");
                }
                startTick[0] = ship.tickCount;
                startY[0] = ship.getY();
            }).thenIdle(40).thenExecute(() -> {
                double rise = ship.getY() - startY[0];
                helper.assertTrue(ship.tickCount - startTick[0] >= 40 && ship.isAlive()
                                && !ship.horizontalCollision && ship.isOrderedToSit() == sitting,
                        "Ship must naturally tick for 40 ticks without collision or a changed command");
                helper.assertTrue(sitting ? Math.abs(rise) <= 0.01D : rise > 0.1D,
                        "Water movement: sitting=" + sitting + " startY=" + startY[0]
                                + " y=" + ship.getY() + " rise=" + rise);
                helper.succeed();
            });
        }, relative);
    }
}
