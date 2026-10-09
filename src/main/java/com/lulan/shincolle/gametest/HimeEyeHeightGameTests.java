package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HimeEyeHeightGameTests {
    private HimeEyeHeightGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void himeStaysOnKongouAfterFiveTicks(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        for (int x = 1; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
        }
        BasicEntityShip kongou = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        BasicEntityShip hime = entities.add(ModEntities.NORTHERN_HIME.get().create(helper.getLevel()));
        kongou.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 2.5D)));
        hime.moveTo(helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D)));
        kongou.setNoAi(true);
        hime.setNoAi(true);
        helper.getLevel().addFreshEntity(kongou);
        helper.getLevel().addFreshEntity(hime);
        GameTestEntities.assertRegistered(helper, kongou);
        GameTestEntities.assertRegistered(helper, hime);
        helper.assertTrue(hime.startRiding(kongou, true), "Hime should board Kongou");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(hime.getVehicle() == kongou, "Hime should stay aboard after five ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void himeIgnoresWallButDismountsForOtherDamage(GameTestHelper helper) {
        try (var entities = GameTestEntities.open(helper)) {
            for (EntityType<? extends BasicEntityShip> type :
                    java.util.List.<EntityType<? extends BasicEntityShip>>of(
                            ModEntities.NORTHERN_HIME.get(), ModEntities.SSNH.get())) {
                BasicEntityShip kongou = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                BasicEntityShip hime = entities.add(type.create(helper.getLevel()));
                helper.assertTrue(hime.startRiding(kongou, true), "Hime should board Kongou");
                hime.hurt(hime.damageSources().inWall(), 1F);
                helper.assertTrue(hime.getVehicle() == kongou, "Wall damage must not dismount " + type);
                hime.hurt(hime.damageSources().generic(), 1F);
                helper.assertTrue(!hime.isPassenger(), "Other damage must dismount " + type);
            }
            helper.succeed();
        }
    }
}
