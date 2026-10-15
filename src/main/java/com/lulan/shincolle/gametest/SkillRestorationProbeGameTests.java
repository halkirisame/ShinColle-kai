package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipSkillAttackGate;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillRestorationProbeGameTests {
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_probe_beam")
    public static void gaeBolgDoesNotGainDoubleHitDamage(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TATSUTA.get(), (ship, target) -> {
            ship.getAttrs().setAttrsBuffed(ID.Attrs.DHIT, 1F);
            helper.assertTrue(ship.getAttrs().getAttrsBuffed(ID.Attrs.DHIT) == 1F, "Double-hit fixture");
            helper.assertTrue(ShipSkillAttackGate.areaTarget(ship, target, target), "Beam target must be eligible");
            float before = target.getHealth();
            EntityProjectileBeam beam = ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel());
            beam.initGaeBolg(ship, new Vec3(1D, 0D, 0D), 10F);
            beam.tickCount++;
            beam.tick();
            float loss = before - target.getHealth();
            helper.assertTrue(Math.abs(loss - 10F) < 0.001F,
                    "Gae Bolg must keep canMultiHit=false; expected loss=10, actual=" + loss);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_probe_slash")
    public static void tenryuuKnockbackFollowsSlashFacing(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TENRYUU.get(), (ship, target) -> {
            helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Prepare");
            helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Start");
            ship.tickCount++;
            ShipSkillAttackGate.tick(ship);
            helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 2, "Horizontal slash");
            ship.setYRot(90F);
            target.moveTo(ship.position().add(-1D, 0D, 0D));
            target.setDeltaMovement(Vec3.ZERO);
            float before = target.getHealth();
            ship.tickCount++;
            ShipSkillAttackGate.tick(ship);
            helper.assertTrue(target.getHealth() < before, "Slash must have hit");
            Vec3 motion = target.getDeltaMovement();
            helper.assertTrue(motion.x < 0D && Math.abs(motion.z) < 0.001D,
                    "Yaw 90 slash must push toward -X, not fixed +X/+Z; actual=" + motion);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_probe_punch")
    public static void nagatoFinisherStopsPreviousMovement(GameTestHelper helper) {
        fixture(helper, ModEntities.BB_NAGATO.get(), (ship, target) -> {
            for (int i = 0; i < 3; i++) helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Prepare punch");
            ship.setDeltaMovement(0.5D, 0.2D, 0D);
            helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Finish punch");
            helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0 && ship.distanceToSqr(target) <= 9D,
                    "Finisher must have teleported");
            helper.assertTrue(ship.getDeltaMovement().equals(Vec3.ZERO),
                    "Finisher must clear pre-teleport motion; actual=" + ship.getDeltaMovement());
        });
    }

    private static void fixture(GameTestHelper helper, EntityType<? extends BasicEntityShip> type,
                                BiConsumer<BasicEntityShip, Mob> verify) {
        Vec3 home = new Vec3(2.5D, 3D, 2.5D);
        Vec3 at = home.add(3D, 0D, 0D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var mode = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = entities.add(type.create(helper.getLevel()));
                ship.tickCount = 1;
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setAmmoHeavy(1_000);
                ship.calcShipAttributes(31, false);
                ship.setEntitySit(false);
                ship.setInvulnerable(true);
                ship.setHealth(ship.getMaxHealth());
                ship.moveTo(helper.absoluteVec(home));
                ship.setAITargetList();
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship registration");
                Mob target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                target.setNoAi(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000D);
                target.getAttribute(Attributes.ARMOR).setBaseValue(0D);
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D);
                target.setHealth(1_000F);
                target.moveTo(helper.absoluteVec(at));
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Target registration");
                ship.setManualTarget(target);
                authority(ship).tick();
                helper.assertTrue(ShipCombatGate.engagement(ship).target() == target, "Real manual lock");
                ship.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.CRI, 0F);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.DHIT, 0F);
                ship.getAttrs().setAttrsBuffed(ID.Attrs.THIT, 0F);
                helper.assertTrue(CombatHelper.calcMissRate(ship, 1F) == 0F && target.getArmorValue() == 0,
                        "No miss or armor fixture");
                verify.accept(ship, target);
            }
            helper.succeed();
        }, home, at);
    }

    private static ShipTargetAuthorityGoal authority(BasicEntityShip ship) {
        try {
            Field field = BasicEntityShip.class.getDeclaredField("targetAuthorityGoal");
            field.setAccessible(true);
            return (ShipTargetAuthorityGoal) field.get(ship);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
