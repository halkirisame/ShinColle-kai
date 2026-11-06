package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MissileKillRewardGameTests {
    private static final Vec3 SHIP_POSITION = new Vec3(1D, 3D, 1D);
    private static final Vec3 VICTIM_POSITION = new Vec3(3D, 3D, 1D);

    private MissileKillRewardGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_missile_collision_reward")
    public static void missileCollisionKillRewardsLaunchingShip(GameTestHelper helper) {
        missileKillRewards(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_missile_expiry_reward")
    public static void missileExpiryKillRewardsLaunchingShip(GameTestHelper helper) {
        missileKillRewards(helper, true);
    }

    private static void missileKillRewards(GameTestHelper helper, boolean expire) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                helper.assertTrue(ship != null, "Fixture: launching ship");
                ship.setNoAi(true);
                ship.setNoGravity(true);
                ship.setInvulnerable(true);
                ship.setShipLevel(50, true);
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setStateMinor(ID.M.Kills, 5);
                ship.setMorale(3000);
                ship.moveTo(helper.absoluteVec(SHIP_POSITION));
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture: register ship");
                GameTestEntities.assertRegistered(helper, ship);
                ship.setHealth(ship.getMaxHealth());
                helper.assertTrue(ship.getStateMinor(ID.M.Kills) == 5 && ship.getMorale() == 3000
                        && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                        "Fixture: initial kill count, morale and fuel");

                Cow victim = entities.add(EntityType.COW.create(helper.getLevel()));
                helper.assertTrue(victim != null, "Fixture: victim");
                victim.setNoAi(true);
                victim.setNoGravity(true);
                victim.setInvulnerable(false);
                victim.setHealth(1F);
                victim.moveTo(helper.absoluteVec(VICTIM_POSITION));
                helper.assertTrue(helper.getLevel().addFreshEntity(victim), "Fixture: register victim");
                GameTestEntities.assertRegistered(helper, victim);
                helper.assertTrue(victim.isAlive() && victim.getHealth() == 1F && !victim.isInvulnerable(),
                        "Fixture: live HP1 victim without invulnerability");

                EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
                helper.assertTrue(missile != null, "Fixture: missile");
                missile.initMissile(ship, 0, 0, 100F, 0F, (float) ship.getY(),
                        victim.getX(), (float) victim.getY(), victim.getZ(), 140, 0F, 0F, 0F, 0F);
                missile.setPos(victim.position());
                missile.tickCount = expire ? missile.life + 1 : 6;
                helper.assertTrue(missile.getHostEntity() == ship && !TeamHelper.checkSameOwner(missile, victim)
                        && !CombatHelper.isFriendlyFire(ship, victim), "Fixture: missile belongs to the eligible attacker");
                helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Fixture: register missile");
                GameTestEntities.assertRegistered(helper, missile);
                ship.getRand().setSeed(7L);
                long worldTick = helper.getLevel().getGameTime();

                // Let the real lethal hit reach LivingEntity.die and the registered death subscribers.
                missile.tick();

                helper.assertTrue(missile.isRemoved() && victim.isDeadOrDying() && victim.getHealth() == 0F,
                        "The production missile impact must kill the victim");
                DamageSource source = victim.getLastDamageSource();
                helper.assertTrue(source != null && source.getDirectEntity() == missile && source.getEntity() == ship
                        && source.is(DamageTypes.MOB_ATTACK), "Missile classification and causing ship are retained");
                helper.assertTrue(helper.getLevel().getGameTime() == worldTick, "No periodic update between hit and reward");
                helper.assertTrue(ship.getStateMinor(ID.M.Kills) == 6 && ship.getMorale() == 3002,
                        "A missile kill rewards its launching ship once: kills=" + ship.getStateMinor(ID.M.Kills)
                                + ", morale=" + ship.getMorale() + "; expected 6 and 3002");
                helper.succeed();
            }
        }, SHIP_POSITION, VICTIM_POSITION);
    }
}
