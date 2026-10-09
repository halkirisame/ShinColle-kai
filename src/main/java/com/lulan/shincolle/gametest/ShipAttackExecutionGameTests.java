package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.battleship.EntityBBKirishimaMob;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
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

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttackExecutionGameTests {
    private ShipAttackExecutionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_attack_execution_range_goal_accepts_non_living_target")
    public static void rangeAttackGoalAcceptsNonLivingTargetAcrossNaturalTicks(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper,
                () -> verifyRangeAttackGoalAcceptsNonLivingTargetAcrossNaturalTicks(helper),
                new Vec3(2D, 4D, 2D), new Vec3(4D, 4D, 2D));
    }

    private static void verifyRangeAttackGoalAcceptsNonLivingTargetAcrossNaturalTicks(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            TestHostileShip attacker = entities.add(new TestHostileShip(
                    ModEntities.BB_KIRISHIMA_MOB.get(), helper.getLevel()));
            prepareAttacker(attacker);
            add(helper, attacker, 2D, 2D);
            EntityAbyssMissile target = missile(helper, entities, attacker, 4D, 2D, 20F);
            target.setPlayerUID(8101);
            attacker.setTestTarget(target);

            helper.runAfterDelay(70, () -> {
                try {
                    helper.assertTrue(!target.isAlive(),
                            "Naturally ticking range attack goal did not destroy its non-living target");
                    helper.succeed();
                } finally {
                    entities.close();
                }
            });
        } catch (Throwable error) {
            entities.close();
            throw error;
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 40,
            batch = "isolated_attack_execution_new_living_projection_matches_lock")
    public static void newAuthorityEntityTargetMatchesLivingProjection(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper,
                () -> verifyNewAuthorityEntityTargetMatchesLivingProjection(helper),
                new Vec3(2D, 4D, 2D), new Vec3(5D, 4D, 2D));
    }

    private static void verifyNewAuthorityEntityTargetMatchesLivingProjection(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            TestFriendlyShip ship = testFriendly(helper, entities, 2D, 2D, 8201);
            EntityBBKirishimaMob target = hostile(helper, entities, 5D, 2D);
            target.setInvulnerable(true);

            helper.runAfterDelay(30, () -> {
                try {
                    helper.assertTrue(ship.getTarget() == target,
                            "NEW authority did not project its living lock to Mob.target");
                    helper.assertTrue(ship.getEntityTarget() == target,
                            "getEntityTarget did not resolve the same living lock");
                    helper.succeed();
                } finally {
                    entities.close();
                }
            });
        } catch (Throwable error) {
            entities.close();
            throw error;
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_attack_execution_mount_delegates_authority_target")
    public static void mountDelegatesAuthorityTargetAcrossNaturalTicks(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper,
                () -> verifyMountDelegatesAuthorityTargetAcrossNaturalTicks(helper),
                new Vec3(2D, 4D, 8D), new Vec3(5D, 4D, 8D), new Vec3(8D, 4D, 8D));
    }

    private static void verifyMountDelegatesAuthorityTargetAcrossNaturalTicks(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            TestFriendlyShip ship = testFriendly(helper, entities, 2D, 8D, 8301);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 20F);
            ship.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            ship.setAmmoLight(1_000);
            EntityBBKirishimaMob target = hostile(helper, entities, 5D, 8D);
            target.setInvulnerable(true);
            BasicEntityMount mount = entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            helper.assertTrue(mount != null, "Could not create mount fixture");
            add(helper, mount, 2D, 8D);
            mount.setHost(ship);
            helper.assertTrue(ship.startRiding(mount, true), "Ship could not ride its mount fixture");

            helper.runAfterDelay(70, () -> {
                try {
                    helper.assertTrue(ship.getEntityTarget() == target && mount.getEntityTarget() == target,
                            "Mount did not expose the host authority target");
                    EntityBBKirishimaMob replacement = hostile(helper, entities, 8D, 8D);
                    replacement.setInvulnerable(true);
                    ShipTargetAuthorityGoal authority = authorityGoal(ship);
                    UUID lockBefore = authority.currentLock().orElseThrow().target().uuid();

                    mount.setEntityTarget(replacement);

                    helper.assertTrue(authority.currentLock().orElseThrow().target().uuid().equals(lockBefore),
                            "Mount external write changed the host authority lock");
                    helper.assertTrue(ship.getEntityTarget() == target && mount.getEntityTarget() == target,
                            "Mount external write replaced the resolved host authority target");
                    helper.succeed();
                } finally {
                    entities.close();
                }
            });
        } catch (Throwable error) {
            entities.close();
            throw error;
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_attack_execution_missile_blast_owner_rules")
    public static void missileBlastChainsOnlyAcrossDifferentOwners(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verifyMissileBlastChainsOnlyAcrossDifferentOwners(helper),
                new Vec3(1D, 4D, 1D), new Vec3(1D, 4D, 12D),
                new Vec3(5D, 4D, 2D), new Vec3(13D, 4D, 2D));
    }

    private static void verifyMissileBlastChainsOnlyAcrossDifferentOwners(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip firstOwner = friendly(helper, entities, 1D, 1D, 8401, true);
            BasicEntityShip secondOwner = friendly(helper, entities, 1D, 12D, 8402, true);
            firstOwner.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            secondOwner.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);

            TestMissile differentBlast = testMissile(
                    helper, entities, secondOwner, 5D, 2D, 20F);
            EntityAbyssMissile differentTarget = missile(
                    helper, entities, firstOwner, 6D, 2D, 20F);
            TestMissile sameBlast = testMissile(
                    helper, entities, firstOwner, 12D, 2D, 20F);
            EntityAbyssMissile sameTarget = missile(
                    helper, entities, firstOwner, 13D, 2D, 20F);
            differentBlast.setInvulnerable(true);
            sameBlast.setInvulnerable(true);

            differentBlast.triggerImpact();
            sameBlast.triggerImpact();

            helper.assertTrue(!differentTarget.isAlive(),
                    "Different-owner missile did not chain-explode from blast damage above eight");
            helper.assertTrue(sameTarget.isAlive(),
                    "Same-owner missile chain-exploded despite owner filtering");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_attack_execution_missile_blast_detonates_once")
    public static void missileBlastDetonatesEachMissileOnce(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verifyMissileBlastDetonatesEachMissileOnce(helper),
                new Vec3(1D, 4D, 1D), new Vec3(1D, 4D, 12D),
                new Vec3(5D, 4D, 2D), new Vec3(6D, 4D, 2D));
    }

    private static void verifyMissileBlastDetonatesEachMissileOnce(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip firstOwner = friendly(helper, entities, 1D, 1D, 8601, true);
            BasicEntityShip secondOwner = friendly(helper, entities, 1D, 12D, 8602, true);
            firstOwner.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
            secondOwner.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);

            CountingMissile first = countingMissile(helper, entities, firstOwner, 5D, 2D);
            CountingMissile second = countingMissile(helper, entities, secondOwner, 6D, 2D);
            first.triggerImpact();

            helper.assertTrue(first.impactCount == 1,
                    "First missile detonated " + first.impactCount + " times instead of once");
            helper.assertTrue(second.impactCount == 1,
                    "Different-owner missile detonated " + second.impactCount + " times instead of once");
            helper.assertTrue(!first.isAlive() && !second.isAlive(),
                    "Different-owner missile did not chain-explode");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_attack_execution_missile_magic_damage_explodes")
    public static void missileExplodesFromMagicDamage(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> verifyMissileExplodesFromMagicDamage(helper),
                new Vec3(1D, 4D, 1D), new Vec3(5D, 4D, 2D));
    }

    private static void verifyMissileExplodesFromMagicDamage(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip owner = friendly(helper, entities, 1D, 1D, 8501, true);
            EntityAbyssMissile missile = missile(helper, entities, owner, 5D, 2D, 1F);

            boolean accepted = missile.hurt(missile.damageSources().magic(), 1F);

            helper.assertTrue(accepted, "Missile rejected magic damage");
            helper.assertTrue(!missile.isAlive(), "Magic damage did not immediately explode the missile");
            helper.succeed();
        }
    }

    private static void prepareAttacker(TestHostileShip attacker) {
        attacker.setNoAi(false);
        attacker.setStateFlag(ID.F.NoFuel, false);
        attacker.setStateFlag(ID.F.AtkType_Light, true);
        attacker.setStateFlag(ID.F.AtkType_Heavy, false);
        attacker.setStateFlag(ID.F.UseAmmoLight, true);
        attacker.setStateFlag(ID.F.UseAmmoHeavy, false);
        attacker.setStateMinor(ID.M.NumGrudge, 100_000);
        attacker.setAmmoLight(1_000);
        attacker.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 20F);
        attacker.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
        attacker.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
        attacker.getAttrs().setAttrsBuffed(ID.Attrs.MOV, 0F);
    }

    private static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities,
                                            double x, double z, int ownerUid, boolean noAi) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create friendly ship fixture");
        ship.setNoAi(noAi);
        ship.setPlayerUID(ownerUid);
        ship.setEntitySit(false);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PassiveAI, false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        add(helper, ship, x, z);
        return ship;
    }

    private static TestFriendlyShip testFriendly(GameTestHelper helper, GameTestEntities entities,
                                                 double x, double z, int ownerUid) {
        TestFriendlyShip ship = entities.add(new TestFriendlyShip(
                ModEntities.BB_KONGOU.get(), helper.getLevel()));
        ship.setNoAi(false);
        ship.setPlayerUID(ownerUid);
        ship.setEntitySit(false);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PassiveAI, false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        add(helper, ship, x, z);
        return ship;
    }

    private static EntityBBKirishimaMob hostile(
            GameTestHelper helper, GameTestEntities entities, double x, double z) {
        EntityBBKirishimaMob hostile = entities.add(
                ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        helper.assertTrue(hostile != null, "Could not create hostile ship fixture");
        hostile.setNoAi(true);
        add(helper, hostile, x, z);
        return hostile;
    }

    private static EntityAbyssMissile missile(GameTestHelper helper, GameTestEntities entities,
                                              IShipAttackBase owner, double x, double z, float attack) {
        EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
        helper.assertTrue(missile != null, "Could not create missile fixture");
        initializeMissile(helper, missile, owner, x, z, attack);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Could not add missile fixture");
        GameTestEntities.assertRegistered(helper, missile);
        return missile;
    }

    private static TestMissile testMissile(GameTestHelper helper, GameTestEntities entities,
                                           IShipAttackBase owner, double x, double z, float attack) {
        TestMissile missile = entities.add(new TestMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
        initializeMissile(helper, missile, owner, x, z, attack);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Could not add test missile fixture");
        GameTestEntities.assertRegistered(helper, missile);
        return missile;
    }

    private static CountingMissile countingMissile(GameTestHelper helper, GameTestEntities entities,
                                                   IShipAttackBase owner, double x, double z) {
        CountingMissile missile = entities.add(new CountingMissile(ModEntities.ABYSS_MISSILE.get(), helper.getLevel()));
        initializeMissile(helper, missile, owner, x, z, 20F);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Could not add counting missile fixture");
        GameTestEntities.assertRegistered(helper, missile);
        return missile;
    }

    private static void initializeMissile(GameTestHelper helper, EntityAbyssMissile missile,
                                          IShipAttackBase owner, double x, double z, float attack) {
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, z));
        missile.initMissile(owner, 0, 0, attack, 0F, (float) position.y,
                (float) position.x, (float) position.y, (float) position.z,
                160, 0F, 0F, 0F, 0F);
        missile.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
        missile.moveTo(position.x, position.y, position.z);
    }

    private static void add(GameTestHelper helper, Entity entity, double x, double z) {
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, z));
        entity.moveTo(position.x, position.y, position.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity),
                "Could not add entity fixture: " + entity.getType());
        GameTestEntities.assertRegistered(helper, entity);
    }

    private static ShipTargetAuthorityGoal authorityGoal(BasicEntityShip ship) {
        try {
            Field field = BasicEntityShip.class.getDeclaredField("targetAuthorityGoal");
            field.setAccessible(true);
            return (ShipTargetAuthorityGoal) field.get(ship);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not read target authority goal", error);
        }
    }

    private static void assignAuthorityGoal(BasicEntityShip ship, ShipTargetAuthorityGoal goal) {
        try {
            Field goalField = BasicEntityShip.class.getDeclaredField("targetAuthorityGoal");
            goalField.setAccessible(true);
            goalField.set(ship, goal);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not install target authority fixture", error);
        }
    }

    private static final class TestHostileShip extends EntityBBKirishimaMob {
        private Entity testTarget;

        private TestHostileShip(EntityType<? extends EntityBBKirishimaMob> type, Level level) {
            super(type, level);
        }

        @Override
        public Entity getEntityTarget() {
            return this.testTarget;
        }

        private void setTestTarget(Entity target) {
            this.testTarget = target;
        }
    }

    private static final class TestFriendlyShip extends EntityBBKongou {
        private TestFriendlyShip(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public void setAITargetList() {
            ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(this);
            assignAuthorityGoal(this, goal);
            this.targetSelector.addGoal(1, goal);
        }
    }

    private static final class TestMissile extends EntityAbyssMissile {
        private TestMissile(EntityType<? extends EntityAbyssMissile> type, Level level) {
            super(type, level);
        }

        private void triggerImpact() {
            this.onImpact(null);
        }
    }

    private static final class CountingMissile extends EntityAbyssMissile {
        private int impactCount;

        private CountingMissile(EntityType<? extends EntityAbyssMissile> type, Level level) {
            super(type, level);
        }

        @Override
        protected void onImpact(Entity target) {
            this.impactCount++;
            // Bound the recursion in the pre-fix test while still recording every attempted impact.
            if (this.impactCount == 1) {
                super.onImpact(target);
            }
        }

        private void triggerImpact() {
            this.onImpact(null);
        }
    }
}
