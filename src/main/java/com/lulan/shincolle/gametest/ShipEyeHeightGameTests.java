package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipEyeHeightGameTests {
    private static final float TOLERANCE = 1E-4F;

    private ShipEyeHeightGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void registeredDimensionsAndEyesSurviveRefresh(GameTestHelper helper) {
        try (var entities = GameTestEntities.open(helper)) {
            assertDimensionsAndEye(helper, entities, ModEntities.NORTHERN_HIME.get(), 0.72F);
            assertDimensionsAndEye(helper, entities, ModEntities.BB_KONGOU.get(), 1.5F);
            assertDimensionsAndEye(helper, entities, ModEntities.DESTROYER_SHIMAKAZE.get(), 1.5F);
            assertDimensionsAndEye(helper, entities, ModEntities.DESTROYER_HIBIKI.get(), 1.4F);
            assertDimensionsAndEye(helper, entities, ModEntities.DESTROYER_IKAZUCHI.get(), 1.4F);
            assertDimensionsAndEye(helper, entities, ModEntities.DESTROYER_INAZUMA.get(), 1.4F);
            helper.succeed();
        }
    }

    private static void assertDimensionsAndEye(GameTestHelper helper, GameTestEntities entities,
            EntityType<? extends BasicEntityShip> type, float expectedEye) {
        BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
        assertSize(helper, ship, type, expectedEye);
        ship.refreshDimensions();
        assertSize(helper, ship, type, expectedEye);
    }

    private static void assertSize(GameTestHelper helper, BasicEntityShip ship,
            EntityType<? extends BasicEntityShip> type, float expectedEye) {
        helper.assertTrue(Math.abs(ship.getEyeHeight() - expectedEye) < TOLERANCE,
                type + " eye height: " + ship.getEyeHeight() + " expected " + expectedEye);
        helper.assertTrue(Math.abs(ship.getBbWidth() - type.getWidth()) < TOLERANCE,
                type + " width: " + ship.getBbWidth());
        helper.assertTrue(Math.abs(ship.getBbHeight() - type.getHeight()) < TOLERANCE,
                type + " height: " + ship.getBbHeight());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void kongouSeesZombieOverOneBlockWall(GameTestHelper helper) {
        try (var entities = GameTestEntities.open(helper)) {
            for (int x = 1; x <= 5; x++) {
                helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            Zombie zombie = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 2.5D)));
            zombie.moveTo(helper.absoluteVec(new Vec3(5.5D, 2D, 2.5D)));
            ship.setNoAi(true);
            zombie.setNoAi(true);
            helper.getLevel().addFreshEntity(ship);
            helper.getLevel().addFreshEntity(zombie);
            GameTestEntities.assertRegistered(helper, ship);
            GameTestEntities.assertRegistered(helper, zombie);
            helper.assertTrue(ship.hasLineOfSight(zombie), "Kongou should see above the one-block wall");
            helper.succeed();
        }
    }

}
