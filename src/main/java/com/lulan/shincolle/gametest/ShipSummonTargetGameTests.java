package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipAircraftAttackGoal;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.entity.other.EntityFloatingFort;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipSummonTargetGameTests {
    private ShipSummonTargetGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_summon_inherits_non_living_target")
    public static void summonInheritsNonLivingHostTarget(GameTestHelper helper) {
        waitForSynchronousFixture(helper, () -> verifySummonInheritsNonLivingHostTarget(helper));
    }

    private static void verifySummonInheritsNonLivingHostTarget(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TestHostShip host = host(helper, entities, 1D);
            EntityAbyssMissile missile = missile(helper, entities, host, 3D);
            host.setTestTarget(missile);
            EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
            helper.assertTrue(summon != null, "Could not create summon fixture");
            summon.setHost(host);
            summon.setNumAmmoLight(0);
            add(helper, summon, 2D);

            summon.tick();

            helper.assertTrue(summon.isAlive(), "Summon despawned for a live non-living host target");
            helper.assertTrue(summon.getEntityTarget() == missile,
                    "Summon did not retain the non-living host target");
            helper.assertTrue(summon.getTarget() == null,
                    "Non-living target was projected into Mob.target");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_summon_rejects_dead_host_target")
    public static void summonDespawnsForDeadHostTarget(GameTestHelper helper) {
        waitForSynchronousFixture(helper, () -> verifySummonDespawnsForDeadHostTarget(helper));
    }

    private static void verifySummonDespawnsForDeadHostTarget(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TestHostShip host = host(helper, entities, 1D);
            EntityBBKongou deadTarget = entities.add(
                    ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(deadTarget != null, "Could not create dead target fixture");
            add(helper, deadTarget, 3D);
            deadTarget.setHealth(0F);
            host.setTestTarget(deadTarget);
            EntityRensouhou summon = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
            helper.assertTrue(summon != null, "Could not create summon fixture");
            summon.setHost(host);
            summon.setNumAmmoLight(0);
            add(helper, summon, 2D);

            summon.tick();

            helper.assertTrue(!summon.isAlive(), "Summon inherited a dead host target");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_aircraft_attacks_non_living_target")
    public static void aircraftInitializesAndAttacksNonLivingTarget(GameTestHelper helper) {
        waitForSynchronousFixture(helper, () -> verifyAircraftInitializesAndAttacksNonLivingTarget(helper));
    }

    private static void verifyAircraftInitializesAndAttacksNonLivingTarget(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            TestHostShip host = host(helper, entities, 1D);
            host.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            EntityAbyssMissile missile = missile(helper, entities, host, 3D);
            missile.setPlayerUID(-2);
            Entity airplaneEntity = entities.add(ModEntities.AIRPLANE.get().create(helper.getLevel()));
            helper.assertTrue(airplaneEntity instanceof EntityAirplane,
                    "Could not create aircraft fixture");
            BasicEntityAirplane airplane = (BasicEntityAirplane) airplaneEntity;

            airplane.initAttrs(host, missile, 1, (float) host.getY());
            airplane.getAttrs().setAttrsBuffed(ID.Attrs.ATK_AL, 20F);
            add(helper, airplane, 2D);

            helper.assertTrue(airplane.getEntityTarget() == missile,
                    "Aircraft did not retain its non-living initial target");
            helper.assertTrue(airplane.getTarget() == null,
                    "Aircraft projected a non-living target into Mob.target");
            airplane.tickCount = 21;
            helper.assertTrue(new ShipAircraftAttackGoal(airplane).canUse(),
                    "Aircraft attack goal rejected the live non-living target");
            helper.assertTrue(airplane.attackEntityWithAmmo(missile),
                    "Aircraft attack did not call hurt on the missile");
            helper.assertTrue(!missile.isAlive(), "Missile survived aircraft damage above eight");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 20,
            batch = "isolated_floating_fort_retains_non_living_target")
    public static void floatingFortRetainsNonLivingTargetAcrossNaturalTick(GameTestHelper helper) {
        waitForSummonFixture(helper, () -> verifyFloatingFortRetainsNonLivingTargetAcrossNaturalTick(helper));
    }

    private static void verifyFloatingFortRetainsNonLivingTargetAcrossNaturalTick(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            TestHostShip host = host(helper, entities, 1D);
            EntityAbyssMissile missile = missile(helper, entities, host, 10D);
            EntityFloatingFort floatingFort = entities.add(
                    ModEntities.FLOATING_FORT.get().create(helper.getLevel()));
            helper.assertTrue(floatingFort != null, "Could not create floating fort fixture");
            floatingFort.initAttrs(host, missile, 1, (float) host.getY());
            add(helper, floatingFort, 2D);
            Vec3 initialPosition = floatingFort.position();

            helper.runAfterDelay(3, () -> {
                try (entities) {
                    helper.assertTrue(floatingFort.isAlive(),
                            "Floating fort exploded for a live non-living target");
                    helper.assertTrue(floatingFort.getEntityTarget() == missile,
                            "Floating fort lost its non-living target");
                    helper.assertTrue(floatingFort.getX() > initialPosition.x + 0.1D,
                            "Floating fort did not move toward its non-living target");
                    helper.succeed();
                }
            });
        } catch (Throwable error) {
            entities.close();
            throw error;
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 20,
            batch = "isolated_aircraft_moves_toward_non_living_target")
    public static void aircraftMovesTowardNonLivingTargetAcrossNaturalTick(GameTestHelper helper) {
        waitForSummonFixture(helper, () -> verifyAircraftMovesTowardNonLivingTargetAcrossNaturalTick(helper));
    }

    private static void verifyAircraftMovesTowardNonLivingTargetAcrossNaturalTick(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            TestHostShip host = host(helper, entities, 1D);
            EntityAbyssMissile missile = missile(helper, entities, host, 10D);
            EntityAirplane airplane = entities.add(ModEntities.AIRPLANE.get().create(helper.getLevel()));
            helper.assertTrue(airplane != null, "Could not create aircraft fixture");
            airplane.initAttrs(host, missile, 1, (float) host.getY());
            add(helper, airplane, 2D);
            Vec3 initialPosition = airplane.position();

            helper.runAfterDelay(3, () -> {
                try (entities) {
                    helper.assertTrue(airplane.isAlive(), "Aircraft did not survive its launch ticks");
                    helper.assertTrue(airplane.getEntityTarget() == missile,
                            "Aircraft lost its non-living target");
                    helper.assertTrue(airplane.getX() > initialPosition.x + 0.1D,
                            "Aircraft did not move toward its non-living target after launch");
                    helper.succeed();
                }
            });
        } catch (Throwable error) {
            entities.close();
            throw error;
        }
    }

    private static TestHostShip host(GameTestHelper helper, GameTestEntities entities, double x) {
        TestHostShip host = entities.add(new TestHostShip(ModEntities.BB_KONGOU.get(), helper.getLevel()));
        host.setNoAi(true);
        host.setPlayerUID(8181);
        add(helper, host, x);
        return host;
    }

    private static void waitForSynchronousFixture(GameTestHelper helper, Runnable verification) {
        GameTestEntities.whenPositionsTicking(helper, verification,
                new Vec3(1D, 4D, 1D), new Vec3(3D, 4D, 1D));
    }

    private static void waitForSummonFixture(GameTestHelper helper, Runnable verification) {
        GameTestEntities.whenPositionsTicking(helper, verification,
                new Vec3(1D, 4D, 1D), new Vec3(2D, 4D, 1D), new Vec3(10D, 4D, 1D));
    }

    private static EntityAbyssMissile missile(
            GameTestHelper helper, GameTestEntities entities, TestHostShip host, double x) {
        EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
        helper.assertTrue(missile != null, "Could not create missile fixture");
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        missile.initMissile(host, 0, 0, 1F, 0F, (float) position.y,
                (float) position.x, (float) position.y, (float) position.z,
                160, 0F, 0F, 0F, 0F);
        missile.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
        missile.moveTo(position.x, position.y, position.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Could not add missile fixture");
        GameTestEntities.assertRegistered(helper, missile);
        return missile;
    }

    private static void add(GameTestHelper helper, Entity entity, double x) {
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        entity.moveTo(position.x, position.y, position.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity),
                "Could not add entity fixture: " + entity.getType());
        GameTestEntities.assertRegistered(helper, entity);
    }

    private static final class TestHostShip extends EntityBBKongou {
        private Entity testTarget;

        private TestHostShip(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public Entity getEntityTarget() {
            return this.testTarget;
        }

        @Override
        public Entity getHostEntity() {
            return this;
        }

        private void setTestTarget(Entity target) {
            this.testTarget = target;
        }
    }
}
