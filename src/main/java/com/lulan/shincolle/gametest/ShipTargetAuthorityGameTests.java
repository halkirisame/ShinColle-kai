package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipRangeTargetGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.entity.other.EntityAirplaneTMob;
import com.lulan.shincolle.entity.battleship.EntityBBKongou;
import com.lulan.shincolle.entity.carrier.EntityCarrierAkagiMob;
import com.lulan.shincolle.entity.battleship.EntityBBKirishimaMob;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipTargetAuthorityGameTests {
    private ShipTargetAuthorityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_manual_defers_revenge")
    public static void manualDefersRevengeUntilReleased(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendly(helper, entities, 1D, 7101);
            BasicEntityShipHostile manual = hostile(helper, entities, 3D);
            BasicEntityShipHostile attacker = hostile(helper, entities, 5D);
            ShipTargetAuthorityGoal goal = authorityGoal(ship);

            ship.setManualTarget(manual);
            tick(goal, ship, 100);
            queueRevenge(ship, attacker, 102);
            tick(goal, ship, 102);
            check(ship.getTarget() == manual, "Revenge replaced the manual target");
            check(ship.getEntityRevengeTarget() == attacker, "Deferred revenge was consumed");

            ship.setManualTarget(null);
            tick(goal, ship, 104);
            check(ship.getTarget() == attacker, "Revenge did not fire after manual release");
            check(ship.getEntityRevengeTarget() == null, "Fired revenge was not consumed");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_auto_lock_is_sticky")
    public static void autoLockStaysUntilInvalidAndThenRescans(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendly(helper, entities, 1D, 7102);
            BasicEntityShipHostile first = hostile(helper, entities, 3D);
            BasicEntityShipHostile second = hostile(helper, entities, 5D);
            ShipTargetAuthorityGoal goal = authorityGoal(ship);

            tick(goal, ship, 100);
            Entity selected = ship.getTarget();
            check(selected == first || selected == second, "AUTO did not acquire a hostile ship");
            Entity survivor = selected == first ? second : first;
            for (int tick = 102; tick < 108; tick += 2) {
                tick(goal, ship, tick);
                check(ship.getTarget() == selected, "AUTO changed a valid target");
            }
            ((BasicEntityShipHostile) selected).setHealth(0F);
            tick(goal, ship, 108);
            check(ship.getTarget() == survivor, "AUTO did not rescan after invalidation and cooldown");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_passive_revenge_only")
    public static void passiveShipSkipsAutoButAcceptsRevenge(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendlyUnconfigured(helper, entities, 1D, 7103);
            ship.setStateFlag(ID.F.PassiveAI, true);
            rebuildAuthority(ship);
            BasicEntityShipHostile attacker = hostile(helper, entities, 3D);
            ShipTargetAuthorityGoal goal = authorityGoal(ship);

            tick(goal, ship, 100);
            check(ship.getTarget() == null, "Passive ship acquired an automatic target");
            queueRevenge(ship, attacker, 102);
            tick(goal, ship, 102);
            check(ship.getTarget() == attacker, "Passive ship rejected revenge");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_sit_and_external_write")
    public static void sittingClearsLockAndExternalWriteIsIgnored(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendly(helper, entities, 1D, 7104);
            BasicEntityShipHostile target = hostile(helper, entities, 3D);
            ShipTargetAuthorityGoal goal = authorityGoal(ship);
            tick(goal, ship, 100);
            check(ship.getTarget() == target, "AUTO fixture did not acquire its target");

            ship.setEntitySit(true);
            check(ship.getTarget() == null && goal.currentLock().isEmpty(), "Sitting did not clear the lock");
            ship.setEntitySit(false);
            ship.setEntityTarget(target);
            check(ship.getTarget() == null && goal.currentLock().isEmpty(),
                    "External non-null write changed NEW authority");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 110,
            batch = "isolated_target_authority_hostile_intercepts_missile")
    public static void hostileAutoInterceptsMissileAndRescans(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            try {
                TestAuthorityHostile ship = entities.add(new TestAuthorityHostile(
                        ModEntities.BB_KIRISHIMA_MOB.get(), helper.getLevel()));
                prepareHostileAttacker(ship);
                add(helper, ship, 1D);
                EntityAbyssMissile missile = missile(helper, entities, ship, 4D, 7105);
                BasicEntityShip[] survivor = {null};
                ship.setNoAi(false);

                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(ship.getTarget() == null && ship.getEntityTarget() == missile,
                            "NEW hostile did not hold a non-living lock with null vanilla projection");
                });
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(ship.getEntityTarget() == missile,
                            "Living missile lock was dropped on the next authority tick");
                });
                helper.runAfterDelay(65, () -> {
                    survivor[0] = friendlyUnconfigured(helper, entities, 12D, 7106);
                });
                helper.runAfterDelay(88, () -> {
                    try {
                        helper.assertTrue(!missile.isAlive(), "Hostile did not shoot down the missile");
                        helper.assertTrue(ship.getEntityTarget() == survivor[0],
                                "Hostile did not acquire another target after the missile was destroyed");
                        helper.succeed();
                    } finally {
                        entities.close();
                    }
                });
            } catch (Throwable error) {
                entities.close();
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_friendly_anti_air_off_missile")
    public static void friendlyAntiAirOffDoesNotTargetMissile(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendly(helper, entities, 1D, 7112);
            missile(helper, entities, ship, 3D, -2);
            tick(authorityGoal(ship), ship, 100);
            helper.assertTrue(ship.getEntityTarget() == null, "AntiAir OFF acquired a hostile missile");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_friendly_anti_air_owner_rules")
    public static void friendlyAntiAirTargetsEnemyMissileButNotOwnMissile(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendlyUnconfigured(helper, entities, 1D, 7113);
            ship.setStateFlag(ID.F.AntiAir, true);
            rebuildAuthority(ship);
            EntityAbyssMissile own = missile(helper, entities, ship, 2D, 7113);
            BasicEntityShipHostile enemyHost = hostile(helper, entities, 12D);
            EntityAbyssMissile enemy = hostileMissile(helper, entities, enemyHost, 4D);
            enemyHost.discard();
            tick(authorityGoal(ship), ship, 100);
            helper.assertTrue(ship.getEntityTarget() == enemy && ship.getTarget() == null,
                    "AntiAir ON did not distinguish enemy missile from own missile");
            helper.assertTrue(own.isAlive(), "Own missile unexpectedly disappeared");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 75,
            batch = "isolated_target_authority_carrier_launches_for_missile")
    public static void carrierLaunchesAircraftForNonLivingLock(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            try {
                TestAuthorityCarrier carrier = entities.add(new TestAuthorityCarrier(
                        ModEntities.CV_AKAGI_MOB.get(), helper.getLevel()));
                carrier.setNoAi(true);
                carrier.setStateFlag(ID.F.NoFuel, false);
                carrier.setStateFlag(ID.F.AtkType_AirLight, false);
                carrier.setStateFlag(ID.F.AtkType_AirHeavy, true);
                carrier.setStateFlag(ID.F.UseAirHeavy, true);
                carrier.setAmmoHeavy(1000);
                carrier.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
                add(helper, carrier, 1D);
                EntityAbyssMissile missile = missile(helper, entities, carrier, 14D, 7114);
                carrier.setNoAi(false);

                helper.runAfterDelay(25, () -> helper.assertTrue(carrier.getEntityTarget() == missile,
                        "Carrier did not lock the missile"));
                helper.runAfterDelay(42, () -> {
                    try {
                        List<BasicEntityAirplane> planes = helper.getLevel().getEntitiesOfClass(
                                BasicEntityAirplane.class, carrier.getBoundingBox().inflate(20D));
                        helper.assertTrue(!planes.isEmpty(), "Carrier did not launch aircraft for missile lock");
                        helper.assertTrue(planes.stream().anyMatch(Entity::isAlive),
                                "Launched aircraft despawned for a non-living target");
                        helper.succeed();
                    } finally {
                        entities.close();
                    }
                });
            } catch (Throwable error) {
                entities.close();
                throw error;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_legacy_hostile_ignores_missile")
    public static void legacyHostileIgnoresSameMissile(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShipHostile ship = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
                ship.setNoAi(true);
                add(helper, ship, 1D);
                EntityAbyssMissile missile = missile(helper, entities, ship, 4D, 7115);
                ShipRangeTargetGoal legacy = new ShipRangeTargetGoal(ship);
                if (legacy.canUse()) {
                    legacy.start();
                }
                helper.assertTrue(ship.getEntityTarget() != missile,
                        "LEGACY hostile acquired a non-living missile");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 65,
            batch = "isolated_target_authority_new_aircraft_reacquires_missile")
    public static void newAircraftReacquiresHostileMissile(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> verifyAircraftReacquisition(helper, true));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 65,
            batch = "isolated_target_authority_legacy_aircraft_ignores_missile")
    public static void legacyAircraftDoesNotReacquireMissile(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> {
            ShipAiAuthorityOverride.useUntilComplete(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
            verifyAircraftReacquisition(helper, false);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 65,
            batch = "isolated_target_authority_hostile_aircraft_excludes_self")
    public static void hostileAircraftNeverReacquiresItself(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            try {
                TestAuthorityHostile host = entities.add(new TestAuthorityHostile(
                        ModEntities.BB_KIRISHIMA_MOB.get(), helper.getLevel()));
                host.setNoAi(true);
                host.setStateMinor(ID.M.CraneState, 1);
                add(helper, host, 1D);
                rebuildAuthority(host);
                EntityAirplaneTMob plane = entities.add(ModEntities.AIRPLANE_T_MOB.get().create(helper.getLevel()));
                helper.assertTrue(plane != null, "Hostile aircraft creation failed");
                plane.initAttrs(host, null, 1, (float) host.getY());
                plane.setNoAi(false);
                add(helper, plane, 12D);

                helper.runAfterDelay(34, () -> {
                    try {
                        helper.assertTrue(plane.isAlive(), "Hostile aircraft disappeared before reacquisition");
                        helper.assertTrue(host.getEntityTarget() == null,
                                "Host fixture acquired the aircraft instead of leaving reacquisition to the aircraft");
                        helper.assertTrue(plane.getEntityTarget() != plane,
                                "Hostile aircraft selected itself as a target");
                        helper.succeed();
                    } finally {
                        entities.close();
                    }
                });
            } catch (Throwable error) {
                entities.close();
                throw error;
            }
        });
    }

    private static void verifyAircraftReacquisition(GameTestHelper helper, boolean modern) {
        GameTestEntities entities = GameTestEntities.open(helper);
        try {
            BasicEntityShip host = modern ? authorityFriendly(helper, entities, 1D, 7116)
                    : friendlyUnconfigured(helper, entities, 1D, 7116);
            ServerPlayer owner = player(helper, entities, 1D, 7116);
            host.setOwnerUUID(owner.getUUID());
            host.setStateFlag(ID.F.AntiAir, true);
            host.setStateMinor(ID.M.NumGrudge, 100_000);
            host.setStateFlag(ID.F.NoFuel, false);
            if (modern) {
                rebuildAuthority(host);
            }
            BasicEntityShipHostile enemyHost = hostile(helper, entities, 22D);
            EntityAbyssMissile missile = hostileMissile(helper, entities, enemyHost, 20D);
            enemyHost.discard();
            EntityAirplane plane = entities.add(ModEntities.AIRPLANE.get().create(helper.getLevel()));
            check(plane != null, "Aircraft creation failed");
            plane.initAttrs(host, missile, 1, (float) host.getY());
            plane.setNoAi(false);
            add(helper, plane, 2D);

            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertTrue(plane.isAlive(), "Aircraft disappeared before losing its target");
                    plane.setEntityTarget(null);
                } catch (Throwable error) {
                    entities.close();
                    throw error;
                }
            });

            helper.runAfterDelay(34, () -> {
                try {
                    helper.assertTrue(plane.isAlive(), "Aircraft disappeared before reacquisition");
                    helper.assertTrue(modern ? plane.getEntityTarget() == missile
                                    : plane.getEntityTarget() != missile,
                            "Aircraft reacquisition ignored the host authority mode");
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
            batch = "isolated_target_authority_rebuild_releases_target_flag")
    public static void rebuildWhileRunningReleasesTargetFlag(GameTestHelper helper) {
        waitForFixtureChunks(helper, () -> verifyRebuildWhileRunningReleasesTargetFlag(helper));
    }

    private static void verifyRebuildWhileRunningReleasesTargetFlag(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        BasicEntityShip ship;
        BasicEntityShipHostile target;
        ShipTargetAuthorityGoal oldGoal;
        try {
            ship = authorityFriendly(helper, entities, 1D, 7110);
            rebuildAuthority(ship);
            ship.setNoAi(false);
            target = hostile(helper, entities, 5D);
            oldGoal = authorityGoal(ship);
        } catch (Throwable error) {
            entities.close();
            throw error;
        }

        ShipTargetAuthorityGoal[] rebuiltGoal = {null};
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(authorityRunning(ship, oldGoal),
                    "Authority goal was not running before rebuild");
            rebuildAuthority(ship);
            rebuiltGoal[0] = authorityGoal(ship);
        });
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(authorityRunning(ship, rebuiltGoal[0]),
                        "Rebuilt authority goal did not start");
                helper.assertTrue(ship.getTarget() == target,
                        "Rebuilt authority goal did not acquire its target");
                helper.succeed();
            } finally {
                entities.close();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_revenge_lock_is_sticky")
    public static void revengeLockDefersLaterAttackerUntilInvalid(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip ship = friendly(helper, entities, 1D, 7111);
            BasicEntityShipHostile first = hostile(helper, entities, 3D);
            BasicEntityShipHostile second = hostile(helper, entities, 5D);
            ShipTargetAuthorityGoal goal = authorityGoal(ship);

            queueRevenge(ship, first, 100);
            tick(goal, ship, 100);
            check(ship.getTarget() == first, "First revenge target was not acquired");

            queueRevenge(ship, second, 102);
            tick(goal, ship, 102);
            check(ship.getTarget() == first, "Later attacker replaced a valid revenge target");
            check(ship.getEntityRevengeTarget() == second, "Deferred revenge was consumed");

            first.setHealth(0F);
            tick(goal, ship, 104);
            check(ship.getTarget() == second, "Deferred revenge did not fire after invalidation");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_hostile_auto_relations")
    public static void hostileAutoTargetsFriendlyAndPlayerButNotHostile(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShipHostile versusFriendly = hostile(helper, entities, 1D);
            BasicEntityShip friendly = friendly(helper, entities, 3D, 7106);
            tick(authorityGoal(versusFriendly), versusFriendly, 100);
            check(versusFriendly.getTarget() == friendly, "Hostile did not target a friendly ship");

            versusFriendly.discard();
            friendly.discard();
            BasicEntityShipHostile versusPlayer = hostile(helper, entities, 10D);
            ServerPlayer player = player(helper, entities, 12D, 7107);
            tick(authorityGoal(versusPlayer), versusPlayer, 100);
            check(versusPlayer.getTarget() == player, "Hostile did not target an eligible player");

            versusPlayer.discard();
            player.discard();
            BasicEntityShipHostile source = hostile(helper, entities, 20D);
            hostile(helper, entities, 22D);
            tick(authorityGoal(source), source, 100);
            check(source.getTarget() == null, "Hostile targeted another hostile ship");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_propagated_revenge")
    public static void propagatedRevengeBecomesAuthorityTarget(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            ServerPlayer owner = player(helper, entities, 1D, 7108);
            BasicEntityShip receiver = friendly(helper, entities, 3D, 7108);
            BasicEntityShipHostile attacker = hostile(helper, entities, 5D);
            receiver.setOwnerUUID(owner.getUUID());
            owner.tickCount = 100;
            receiver.tickCount = 100;

            MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(
                    owner, owner.damageSources().mobAttack(attacker), 1F));
            check(receiver.getEntityRevengeTarget() == attacker, "Receiver missed propagated revenge");
            tick(authorityGoal(receiver), receiver, receiver.getEntityRevengeTime());
            check(receiver.getTarget() == attacker, "Propagated revenge did not become the authority target");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_target_authority_legacy_auto_parity")
    public static void legacyAndNewAutoChooseSameTargetWithSameRandom(GameTestHelper helper) {
        withNewAuthority(helper, entities -> {
            BasicEntityShip legacyShip = friendlyUnconfigured(helper, entities, 1D, 7109);
            BasicEntityShip newShip = friendly(helper, entities, 1D, 7109);
            hostile(helper, entities, 3D);
            hostile(helper, entities, 5D);
            hostile(helper, entities, 7D);
            legacyShip.tickCount = 100;
            newShip.tickCount = 100;

            helper.getLevel().random.setSeed(987654321L);
            ShipRangeTargetGoal legacy = new ShipRangeTargetGoal(legacyShip);
            check(legacy.canUse(), "LEGACY AUTO did not find its candidate pool");
            legacy.start();
            Entity legacyTarget = legacyShip.getTarget();

            helper.getLevel().random.setSeed(987654321L);
            ShipTargetAuthorityGoal modern = authorityGoal(newShip);
            modern.tick();
            check(newShip.getTarget() == legacyTarget, "LEGACY and NEW consumed the same draw differently");
        });
    }

    private static void withNewAuthority(GameTestHelper helper, Verification verification) {
        waitForFixtureChunks(helper, () -> verifyWithNewAuthority(helper, verification));
    }

    private static void verifyWithNewAuthority(GameTestHelper helper, Verification verification) {
        try (ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                GameTestEntities entities = GameTestEntities.open(helper)) {
            verification.run(entities);
            helper.succeed();
        }
    }

    private static void waitForFixtureChunks(GameTestHelper helper, Runnable verification) {
        GameTestEntities.whenPositionsTicking(helper, verification,
                new Vec3(1D, 4D, 1D), new Vec3(12D, 4D, 1D), new Vec3(22D, 4D, 1D));
    }

    private static BasicEntityShip friendly(
            GameTestHelper helper, GameTestEntities entities, double x, int ownerUid) {
        BasicEntityShip ship = friendlyUnconfigured(helper, entities, x, ownerUid);
        rebuildAuthority(ship);
        return ship;
    }

    private static BasicEntityShip friendlyUnconfigured(
            GameTestHelper helper, GameTestEntities entities, double x, int ownerUid) {
        BasicEntityShip ship = (BasicEntityShip) entities.add(
                ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        prepareFriendly(helper, ship, x, ownerUid);
        return ship;
    }

    private static BasicEntityShip authorityFriendly(
            GameTestHelper helper, GameTestEntities entities, double x, int ownerUid) {
        BasicEntityShip ship = entities.add(new TestAuthorityShip(ModEntities.BB_KONGOU.get(), helper.getLevel()));
        prepareFriendly(helper, ship, x, ownerUid);
        return ship;
    }

    private static void prepareFriendly(GameTestHelper helper, BasicEntityShip ship, double x, int ownerUid) {
        check(ship != null, "Friendly ship creation failed");
        ship.setNoAi(true);
        ship.setPlayerUID(ownerUid);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PassiveAI, false);
        ship.setEntitySit(false);
        add(helper, ship, x);
    }

    private static BasicEntityShipHostile hostile(
            GameTestHelper helper, GameTestEntities entities, double x) {
        BasicEntityShipHostile ship = (BasicEntityShipHostile) entities.add(
                ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        check(ship != null, "Hostile ship creation failed");
        ship.setNoAi(true);
        add(helper, ship, x);
        rebuildAuthority(ship);
        return ship;
    }

    private static ServerPlayer player(
            GameTestHelper helper, GameTestEntities entities, double x, int uid) {
        ServerPlayer player = entities.add(FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "authority_" + uid)));
        player.getAbilities().invulnerable = false;
        player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new AssertionError("Missing player capability")).setPlayerUID(uid);
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        player.moveTo(position.x, position.y, position.z);
        helper.getLevel().addNewPlayer(player);
        GameTestEntities.assertRegistered(helper, player);
        return player;
    }

    private static void add(GameTestHelper helper, Entity entity, double x) {
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        entity.moveTo(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(entity), "Entity was not added: " + entity.getType());
        GameTestEntities.assertRegistered(helper, entity);
    }

    private static EntityAbyssMissile missile(GameTestHelper helper, GameTestEntities entities,
                                              IShipAttackBase owner, double x, int uid) {
        EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
        check(missile != null, "Missile creation failed");
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        missile.initMissile(owner, 0, 0, 1F, 0F, (float) position.y,
                (float) position.x, (float) position.y, (float) position.z,
                160, 0F, 0F, 0F, 0F);
        missile.setPlayerUID(uid);
        missile.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
        add(helper, missile, x);
        return missile;
    }

    private static EntityAbyssMissile hostileMissile(GameTestHelper helper, GameTestEntities entities,
                                                      BasicEntityShipHostile owner, double x) {
        EntityAbyssMissile missile = entities.add(ModEntities.ABYSS_MISSILE.get().create(helper.getLevel()));
        check(missile != null, "Hostile missile creation failed");
        Vec3 position = helper.absoluteVec(new Vec3(x, 4D, 1D));
        missile.initMissile(owner, 0, 0, 1F, 0F, (float) position.y,
                (float) position.x, (float) position.y, (float) position.z,
                160, 0F, 0F, 0F, 0F);
        missile.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
        add(helper, missile, x);
        return missile;
    }

    private static void prepareHostileAttacker(BasicEntityShipHostile ship) {
        ship.setNoAi(true);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.AtkType_Light, true);
        ship.setStateFlag(ID.F.AtkType_Heavy, false);
        ship.setStateFlag(ID.F.UseAmmoLight, true);
        ship.setStateFlag(ID.F.UseAmmoHeavy, false);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setAmmoLight(1_000);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 20F);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.MISS, 1F);
        ship.getAttrs().setAttrsBuffed(ID.Attrs.MOV, 0F);
    }

    private static void rebuildAuthority(Mob ship) {
        try {
            Method clear = ship instanceof BasicEntityShip
                    ? BasicEntityShip.class.getDeclaredMethod("clearAITargetTasks")
                    : BasicEntityShipHostile.class.getDeclaredMethod("clearAITargetTasks");
            clear.setAccessible(true);
            clear.invoke(ship);
            if (ship instanceof BasicEntityShip friendly) {
                friendly.setAITargetList();
            } else {
                ((BasicEntityShipHostile) ship).setAITargetList();
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot rebuild target authority", e);
        }
    }

    private static ShipTargetAuthorityGoal authorityGoal(Mob ship) {
        return targetSelector(ship).getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(ShipTargetAuthorityGoal.class::isInstance)
                .map(ShipTargetAuthorityGoal.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("NEW authority goal was not registered"));
    }

    private static boolean authorityRunning(Mob ship, ShipTargetAuthorityGoal goal) {
        return targetSelector(ship).getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() == goal && wrapped.isRunning());
    }

    private static GoalSelector targetSelector(Mob ship) {
        try {
            Field field = Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(ship);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect target selector", e);
        }
    }

    private static void queueRevenge(IShipAttackBase ship, Entity target, int tick) {
        ((Entity) ship).tickCount = tick;
        ship.setEntityRevengeTarget(target);
        ship.setEntityRevengeTime();
    }

    private static void tick(ShipTargetAuthorityGoal goal, Entity ship, int tick) {
        ship.tickCount = tick;
        if (ship instanceof Mob mob) {
            mob.getSensing().tick();
        }
        TargetHelper.updateTarget((IShipAttackBase) ship);
        goal.tick();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Verification {
        void run(GameTestEntities entities);
    }

    private static final class TestAuthorityShip extends EntityBBKongou {
        private TestAuthorityShip(EntityType<? extends EntityBBKongou> type, Level level) {
            super(type, level);
        }

        @Override
        public void setAITargetList() {
            ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(this);
            try {
                Field field = BasicEntityShip.class.getDeclaredField("targetAuthorityGoal");
                field.setAccessible(true);
                field.set(this, goal);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Cannot install target authority fixture", error);
            }
            this.targetSelector.addGoal(1, goal);
        }
    }

    private static final class TestAuthorityHostile extends EntityBBKirishimaMob {
        private TestAuthorityHostile(EntityType<? extends EntityBBKirishimaMob> type, Level level) {
            super(type, level);
        }

        @Override
        public void setAITargetList() {
            ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(this);
            installAuthority(this, BasicEntityShipHostile.class, goal);
            this.targetSelector.addGoal(1, goal);
        }
    }

    private static final class TestAuthorityCarrier extends EntityCarrierAkagiMob {
        private TestAuthorityCarrier(EntityType<? extends EntityCarrierAkagiMob> type, Level level) {
            super(type, level);
        }

        @Override
        public void setAITargetList() {
            ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(this);
            installAuthority(this, BasicEntityShipHostile.class, goal);
            this.targetSelector.addGoal(1, goal);
        }
    }

    private static void installAuthority(Mob ship, Class<?> owner, ShipTargetAuthorityGoal goal) {
        try {
            Field field = owner.getDeclaredField("targetAuthorityGoal");
            field.setAccessible(true);
            field.set(ship, goal);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot install target authority fixture", error);
        }
    }
}
