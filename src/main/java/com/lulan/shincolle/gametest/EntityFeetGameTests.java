package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipFloating;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityFeetGameTests {
    private static final Vec3 COLUMN = new Vec3(2.5D, 0D, 2.5D);

    private EntityFeetGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void liquidProbeUsesBoundingBoxFloorAcrossZeroForEveryBody(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (Entity body : bodies(helper, entities)) {
                    for (double y : new double[]{-54.11D, -54D, -0.2D, 0D, 0.2D, 54.11D}) {
                        Vec3 at = helper.absoluteVec(COLUMN);
                        body.moveTo(at.x, y + 0.5D, at.z, 0F, 0F);
                        body.setBoundingBox(body.getBoundingBox().move(0D, -0.5D, 0D));
                        helper.assertTrue(Math.abs(body.getBoundingBox().minY - y) < 0.000001D,
                                "fixture must retain the requested bounding-box feet Y");
                        BlockPos feet = BlockPos.containing(at.x, y, at.z);
                        for (boolean lowerWet : new boolean[]{true, false}) {
                            helper.getLevel().setBlock(feet, lowerWet ? Blocks.WATER.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState(), 2);
                            helper.getLevel().setBlock(feet.above(), lowerWet ? Blocks.AIR.defaultBlockState()
                                    : Blocks.WATER.defaultBlockState(), 2);
                            helper.assertTrue(helper.getLevel().getBlockState(feet).is(lowerWet ? Blocks.WATER : Blocks.AIR)
                                            && helper.getLevel().getBlockState(feet.above())
                                                    .is(lowerWet ? Blocks.AIR : Blocks.WATER),
                                    "fixture must have opposing feet and upper states");
                            helper.assertTrue(EntityHelper.checkEntityIsInLiquid(body) == lowerWet,
                                    body.getType() + " liquid probe at minY=" + y + " lowerWet=" + lowerWet);
                        }
                        helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                        helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                helper.succeed();
            }
        }, COLUMN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void negativeDepthIncludesFeetLayerAndHonorsSolidCeiling(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (Entity body : bodies(helper, entities)) {
                    if (!(body instanceof IShipFloating floating)) continue;
                    Vec3 at = helper.absoluteVec(COLUMN);
                    body.moveTo(at.x, -54.11D, at.z, 0F, 0F);
                    helper.assertTrue(Math.abs(body.getBoundingBox().minY + 54.11D) < 0.000001D,
                            "depth fixture must really be at negative fractional Y");
                    BlockPos feet = BlockPos.containing(at.x, -54.11D, at.z);
                    helper.getLevel().setBlock(feet, Blocks.WATER.defaultBlockState(), 2);
                    helper.getLevel().setBlock(feet.above(), Blocks.WATER.defaultBlockState(), 2);
                    for (boolean ceiling : new boolean[]{false, true}) {
                        helper.getLevel().setBlock(feet.above(2), ceiling ? Blocks.STONE.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(), 2);
                        helper.assertTrue(helper.getLevel().getBlockState(feet).is(Blocks.WATER)
                                        && helper.getLevel().getBlockState(feet.above()).is(Blocks.WATER)
                                        && helper.getLevel().getBlockState(feet.above(2))
                                                .is(ceiling ? Blocks.STONE : Blocks.AIR),
                                "depth fixture must have two water layers and the requested ceiling");
                        EntityHelper.updateShipDepth(floating);
                        helper.assertTrue(Math.abs(floating.getShipDepth() - 1.11D) < 0.000001D,
                                body.getType() + " depth=" + floating.getShipDepth() + ", expected 1.11");
                        helper.assertTrue(floating.getStateFlag(ID.F.CanFloatUp) == !ceiling,
                                body.getType() + ": only an air ceiling must allow floating up");
                    }
                    helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                    helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 2);
                    helper.getLevel().setBlock(feet.above(2), Blocks.AIR.defaultBlockState(), 2);
                }
                helper.succeed();
            }
        }, COLUMN);
    }

    private static Entity[] bodies(GameTestHelper helper, GameTestEntities entities) {
        Entity[] bodies = {
                entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel())),
                entities.add(ModEntities.DESTROYER_IKAZUCHI_MOB.get().create(helper.getLevel())),
                entities.add(ModEntities.AIRPLANE_ZERO.get().create(helper.getLevel())),
                entities.add(ModEntities.MOUNT_HBH.get().create(helper.getLevel()))
        };
        for (Entity body : bodies) helper.assertTrue(body != null, "every shared-helper body must exist");
        BasicEntityShip host = entities.add(ModEntities.HARBOUR_HIME.get().create(helper.getLevel()));
        helper.assertTrue(host != null, "mount host must exist");
        host.setStateMinor(ID.M.NumGrudge, 100_000);
        host.setStateFlag(ID.F.NoFuel, false);
        BasicEntityMount mount = (BasicEntityMount) bodies[3];
        mount.setHost(host);
        mount.setStateFlag(ID.F.CanFloatUp, true);
        helper.assertTrue(mount.getHost() == host && mount.getStateFlag(ID.F.CanFloatUp)
                        && host.getStateFlag(ID.F.CanFloatUp), "mount floating flags must reach its real host");
        mount.setStateFlag(ID.F.CanFloatUp, false);
        helper.assertTrue(!mount.getStateFlag(ID.F.CanFloatUp) && !host.getStateFlag(ID.F.CanFloatUp),
                "mount floating flag must be writable in both directions");
        return bodies;
    }
}
