package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillActivationRegressionGameTests {
    private SkillActivationRegressionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_activation_regression")
    public static void firstHeavyAttackPreparesTenryuuWithoutOrdinaryMissile(GameTestHelper helper) {
        Vec3 home = new Vec3(2.5D, 3D, 2.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var mode = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = entities.add(ModEntities.CL_TENRYUU.get().create(helper.getLevel()));
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setAmmoHeavy(1_000);
                ship.calcShipAttributes(31, false);
                ship.setEntitySit(false);
                ship.moveTo(helper.absoluteVec(home));
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship registration");
                Mob target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                target.setNoAi(true);
                target.moveTo(ship.position().add(1D, 0D, 0D));
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Target registration");
                helper.assertTrue(ship.getAmmoHeavy() == 1_000 && ship.getStateMinor(ID.M.NumGrudge) > 0,
                        "Fixture must have heavy ammo and fuel");
                helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Heavy attack must be available");
                helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == -1,
                        "First heavy attack must prepare Tenryuu skill; phase=" + ship.getStateEmotion(ID.S.Phase));
            }
            helper.succeed();
        }, home);
    }
}
