package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipOwnerAssistGate;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.domain.OwnerAssistObservation;
import com.lulan.shincolle.ai.domain.RecentOwnerAttack;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.handler.ShipOwnerAssistHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Natural-tick integration checks; each fixture owns its listeners, players and entities. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerAssistGameTests {
    private static final AtomicInteger NEXT_UID = new AtomicInteger(36_100);

    private OwnerAssistGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_melee")
    public static void meleeSuccessReachesPassiveAuthorityAndCombatConsumer(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.success(rig.a);
            rig.after(6, () -> {
                rig.assertAssist(rig.a);
                helper.assertTrue(ShipCombatGate.engagement(rig.ship).target() == rig.a,
                        "Combat consumer did not receive the assistance target");
                helper.assertTrue(rig.ship.getStateFlag(ID.F.PassiveAI), "Fixture must remain defensive");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_arrow")
    public static void attributedArrowCollisionProducesAssist(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.arrow(rig.b, rig.owner);
            rig.after(10, () -> {
                helper.assertTrue(rig.b.getHealth() < rig.b.getMaxHealth(), "Arrow did not actually damage its target");
                rig.assertAssist(rig.b);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_late_cancel")
    public static void laterLowestCancelCannotReplaceSameTickSuccess(GameTestHelper helper) {
        failedAttack(helper, event -> event.setCanceled(true), false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_late_zero")
    public static void laterLowestZeroCannotReplaceSameTickSuccess(GameTestHelper helper) {
        failedAttack(helper, event -> event.setAmount(0F), false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_absorption")
    public static void fullyAbsorbedAttackDoesNotRenewOrReplaceSuccess(GameTestHelper helper) {
        failedAttack(helper, event -> { }, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_invulnerable")
    public static void invulnerableTargetDoesNotReplaceSuccess(GameTestHelper helper) {
        failedAttack(helper, event -> { }, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_owner_assist_later_invulnerability")
    public static void successfulTargetLosesShipAndAircraftPriorityWhenInvulnerable(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.anchors.put(rig.b, rig.origin.add(40D, 0D, 0D));
            rig.pin();
            TrackingAirplane plane = rig.plane(rig.a, 10D);
            rig.success(rig.b);
            rig.after(18, () -> {
                helper.assertTrue(rig.owner.distanceToSqr(rig.ship) > 32D * 32D,
                        "Owner must be outside revenge propagation range");
                rig.assertAssist(rig.b);
                helper.assertTrue(plane.getEntityTarget() == rig.b
                        && plane.priorityTarget().orElseThrow().source() == TargetSource.OWNER_ASSIST,
                        "Aircraft must acquire the successful target before invulnerability");
                RecentOwnerAttack success = rig.latest();
                rig.b.setInvulnerable(true);
                helper.assertTrue(rig.b.isInvulnerable() && rig.b.isAlive(), "Target must become invulnerable while alive");
                rig.after(18, () -> {
                    helper.assertTrue(rig.latest().equals(success), "Invulnerability must not erase or renew the success record");
                    OwnerAssistObservation observation = ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).orElseThrow();
                    helper.assertTrue(!observation.targetEligible() && !observation.valid(),
                            "Invulnerable target retained assistance eligibility");
                    helper.assertTrue(rig.ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.OWNER_ASSIST)
                            .isEmpty(), "Invulnerable target retained ship assistance priority");
                    helper.assertTrue(ShipOwnerAssistGate.aircraft(plane).isEmpty() && plane.priorityTarget().isEmpty(),
                            "Invulnerable target retained aircraft assistance priority");
                    helper.succeed();
                });
            });
        });
    }

    private static void failedAttack(GameTestHelper helper, Consumer<LivingDamageEvent> mutation,
                                     boolean absorption, boolean invulnerable) {
        fixture(helper, rig -> {
            boolean[] observed = {false};
            Consumer<LivingDamageEvent> listener = event -> {
                if (event.getEntity() == rig.b && event.getSource().getEntity() == rig.owner) {
                    observed[0] = true;
                    mutation.accept(event);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, listener);
            GameTestEntities.onCompletion(helper, () -> MinecraftForge.EVENT_BUS.unregister(listener), "damage mutation cleanup");
            rig.success(rig.a);
            if (absorption) {
                rig.b.setAbsorptionAmount(100F);
                helper.assertTrue(rig.b.getAbsorptionAmount() == 100F, "Absorption fixture was not applied");
            }
            rig.b.setInvulnerable(invulnerable);
            helper.assertTrue(rig.b.isInvulnerable() == invulnerable, "Invulnerability fixture was not applied");
            rig.owner.attack(rig.b);
            rig.after(4, () -> {
                helper.assertTrue(observed[0] != invulnerable, "Final damage event path differed from the fixture");
                helper.assertTrue(rig.b.getHealth() == rig.b.getMaxHealth(), "Failed attack unexpectedly dealt health damage");
                helper.assertTrue(rig.latest().target().uuid().equals(rig.a.getUUID()), "Later failure replaced earlier success");
                long occurredAt = rig.latest().occurredAt();
                rig.after(3, () -> {
                    helper.assertTrue(rig.latest().occurredAt() == occurredAt, "Observation renewed the attack timestamp");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_unknown_arrow")
    public static void unknownArrowAndShipProjectileDoNotAttributeRecursively(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.arrow(rig.a, null);
            rig.arrow(rig.b, rig.ship);
            rig.after(10, () -> {
                helper.assertTrue(rig.a.getHealth() < rig.a.getMaxHealth() && rig.b.getHealth() < rig.b.getMaxHealth(),
                        "Non-player arrow controls must really hit");
                helper.assertTrue(ShipOwnerAssistHandler.latest(helper.getLevel(), rig.owner.getUUID()).isEmpty(),
                        "Unknown or ship-owned arrow was attributed to the owner");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_manual")
    public static void manualDefersLatestAssistAndRevengeQueueRemainsUnconsumed(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.ship.setManualTarget(rig.c);
            rig.success(rig.a);
            rig.after(4, () -> {
                rig.success(rig.b);
                rig.after(4, () -> {
                    helper.assertTrue(rig.ship.currentTargetLock().orElseThrow().source() == TargetSource.MANUAL
                            && rig.ship.getEntityTarget() == rig.c, "Assist overwrote MANUAL");
                    rig.ship.setEntityRevengeTarget(rig.a);
                    rig.ship.setEntityRevengeTime();
                    rig.ship.setManualTarget(null);
                    rig.after(4, () -> {
                        rig.assertAssist(rig.b);
                        helper.assertTrue(rig.ship.getEntityRevengeTarget() == rig.a, "Assistance consumed revenge queue");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 270,
            batch = "isolated_owner_assist_expiry")
    public static void serverAgeTwoHundredIsValidAndTwoHundredOneExpires(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.success(rig.a);
            rig.after(5, () -> rig.ship.setEntityRevengeTarget(null));
            rig.after(200, () -> {
                OwnerAssistObservation observation = ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).orElseThrow();
                helper.assertTrue(observation.age() == 200L && observation.valid(), "Age 200 must remain eligible");
            });
            rig.after(201, () -> {
                helper.assertTrue(!ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D)
                        .map(OwnerAssistObservation::valid).orElse(false), "Age 201 must have no assist priority");
            });
            rig.after(205, () -> {
                helper.assertTrue(rig.ship.currentTargetLock().isEmpty(), "Expired assistance remained locked");
                helper.assertTrue(ShipOwnerAssistHandler.latest(helper.getLevel(), rig.owner.getUUID()).isEmpty(),
                        "Expired owner record was not removed");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_distance")
    public static void shipDistanceIncludesSixtyFourWithoutAnOwnerDistributionRadius(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.anchors.put(rig.b, rig.origin.add(64D, 0D, 0D));
            rig.pin();
            rig.success(rig.b);
            rig.after(5, () -> {
                helper.assertTrue(rig.owner.distanceToSqr(rig.ship) > 32D * 32D, "Owner must be outside old propagation radius");
                rig.assertAssist(rig.b);
                rig.anchors.put(rig.b, rig.origin.add(64.01D, 0D, 0D));
                rig.after(5, () -> {
                    helper.assertTrue(rig.ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.OWNER_ASSIST)
                            .isEmpty(), "Beyond sixty-four must drop assist priority");
                    rig.anchors.put(rig.b, rig.origin.add(63.99D, 0D, 0D));
                    rig.after(5, () -> {
                        rig.assertAssist(rig.b);
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_dead_latest")
    public static void latestSuccessfulKillDoesNotResurrectAnOlderAttack(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.success(rig.a);
            rig.b.setHealth(0.1F);
            rig.success(rig.b);
            helper.assertTrue(!rig.b.isAlive(), "Latest successful attack must kill the target");
            rig.after(5, () -> {
                helper.assertTrue(rig.latest().target().uuid().equals(rig.b.getUUID()), "Successful kill did not remain latest");
                helper.assertTrue(!ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).orElseThrow().valid(),
                        "Dead latest target incorrectly provided assistance");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_crane")
    public static void craneConstraintBlocksAssistAcquisitionUntilReleased(GameTestHelper helper) {
        fixture(helper, rig -> {
            CombatFixturePreparation.holdAtCrane(helper, rig.ship);
            rig.success(rig.a);
            rig.after(5, () -> {
                helper.assertTrue(!ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).orElseThrow().valid(),
                        "Crane constraint was bypassed by assistance");
                helper.assertTrue(rig.ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.OWNER_ASSIST)
                        .isEmpty(), "Crane-constrained ship acquired assistance");
                CombatFixturePreparation.releaseCrane(rig.ship);
                rig.after(5, () -> {
                    rig.assertAssist(rig.a);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_other_owner")
    public static void otherOwnersRecordDoesNotReachTheShip(GameTestHelper helper) {
        fixture(helper, rig -> {
            ServerPlayer other = rig.player();
            other.attack(rig.b);
            rig.after(5, () -> {
                helper.assertTrue(ShipOwnerAssistHandler.latest(helper.getLevel(), other.getUUID()).isPresent(),
                        "Control attack was not recorded for its actual owner");
                helper.assertTrue(ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).isEmpty(), "Owner records mixed");
                helper.assertTrue(rig.ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.OWNER_ASSIST)
                        .isEmpty(), "Other owner's success produced assistance");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_player_permission")
    public static void playerAssistanceUsesExistingRetaliationPermission(GameTestHelper helper) {
        fixture(helper, rig -> {
            ServerPlayer victim = rig.preparePlayer(new VulnerableOpponent(helper.getLevel()));
            victim.setGameMode(GameType.SURVIVAL);
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, victim, 0, "f_8921_");
            int spawnProtection = ObfuscationReflectionHelper.getPrivateValue(ServerPlayer.class, victim, "f_8921_");
            helper.assertTrue(spawnProtection == 0 && !victim.isInvulnerable()
                    && !victim.getAbilities().invulnerable && !victim.isCreative() && !victim.isSpectator()
                    && !victim.isInvulnerableTo(rig.owner.damageSources().playerAttack(rig.owner))
                    && victim.canHarmPlayer(rig.owner), "Opponent must permit real player melee damage");
            rig.ship.setStateFlag(ID.F.RetaliatePlayer, false);
            rig.success(victim);
            rig.after(4, () -> {
                helper.assertTrue(!ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).orElseThrow().valid(),
                        "Assistance bypassed player retaliation permission");
                rig.ship.setStateFlag(ID.F.RetaliatePlayer, true);
                helper.assertTrue(rig.ship.getStateFlag(ID.F.RetaliatePlayer), "Retaliation permission was not applied");
                rig.after(4, () -> {
                    rig.assertAssist(victim);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_mod_alliance")
    public static void modAlliedPlayerCannotReceiveShipOrAircraftAssistDespiteRetaliation(GameTestHelper helper) {
        fixture(helper, rig -> {
            ServerPlayer victim = rig.preparePlayer(new VulnerableOpponent(helper.getLevel()));
            victim.setGameMode(GameType.SURVIVAL);
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, victim, 0, "f_8921_");
            int ownerUid = TeamHelper.getPlayerUID(rig.owner);
            int victimUid = TeamHelper.getPlayerUID(victim);
            helper.assertTrue(ownerUid > 0 && victimUid > 0 && ownerUid != victimUid,
                    "Alliance requires distinct valid player identities");
            ServerDataManager.setTeamData(new TeamData(ownerUid, "AssistOwner", "assist_owner"));
            ServerDataManager.setTeamData(new TeamData(victimUid, "AssistAlly", "assist_ally"));
            GameTestEntities.onCompletion(helper, () -> {
                ServerDataManager.removeTeamData(ownerUid);
                ServerDataManager.removeTeamData(victimUid);
            }, "assist alliance cleanup");
            ServerDataManager.teamAddAlly(ownerUid, victimUid);
            rig.ship.setStateFlag(ID.F.RetaliatePlayer, true);
            rig.anchors.put(victim, rig.origin.add(40D, 0D, 0D));
            rig.pin();
            TrackingAirplane plane = rig.plane(rig.a, 10D);
            helper.assertTrue(TeamHelper.checkIsAlly(rig.ship, victim)
                    && rig.owner.getTeam() == null && victim.getTeam() == null
                    && rig.ship.getStateFlag(ID.F.RetaliatePlayer), "MOD-only alliance and retaliation must be applied");
            int spawnProtection = ObfuscationReflectionHelper.getPrivateValue(ServerPlayer.class, victim, "f_8921_");
            helper.assertTrue(spawnProtection == 0 && !victim.isInvulnerable() && !victim.getAbilities().invulnerable
                    && !victim.isCreative() && !victim.isSpectator()
                    && !victim.isInvulnerableTo(rig.owner.damageSources().playerAttack(rig.owner))
                    && victim.canHarmPlayer(rig.owner), "Allied opponent must permit real player melee damage");
            rig.success(victim);
            rig.after(18, () -> {
                helper.assertTrue(victim.getHealth() < victim.getMaxHealth()
                        && rig.latest().target().uuid().equals(victim.getUUID()), "Allied player attack must really succeed");
                helper.assertTrue(TeamHelper.checkIsAlly(rig.ship, victim)
                        && rig.owner.getTeam() == null && victim.getTeam() == null
                        && rig.ship.getStateFlag(ID.F.RetaliatePlayer)
                        && rig.owner.distanceToSqr(rig.ship) > 32D * 32D,
                        "MOD alliance, retaliation and propagation isolation must remain in effect");
                OwnerAssistObservation observation = ShipOwnerAssistGate.observe(rig.ship, rig.ship, 20D).orElseThrow();
                helper.assertTrue(!observation.targetEligible() && !observation.valid(),
                        "MOD-allied player received assistance eligibility despite alliance");
                helper.assertTrue(rig.ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.OWNER_ASSIST)
                        .isEmpty(), "MOD-allied player received ship assistance priority");
                helper.assertTrue(ShipOwnerAssistGate.aircraft(plane).isEmpty() && plane.priorityTarget().isEmpty(),
                        "MOD-allied player received aircraft assistance priority");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_sitting")
    public static void sittingAndFuelConstraintsCannotBeBypassed(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.success(rig.a);
            rig.after(4, () -> {
                rig.assertAssist(rig.a);
                rig.ship.setEntitySit(true);
                helper.assertTrue(rig.ship.getIsSitting(), "Sitting fixture was not applied");
                rig.after(4, () -> {
                    helper.assertTrue(!ShipCombatGate.engagement(rig.ship).engaged(), "Sitting ship could fire");
                    rig.ship.setEntitySit(false);
                    rig.ship.setStateMinor(ID.M.NumGrudge, 0);
                    rig.ship.setStateFlag(ID.F.NoFuel, true);
                    helper.assertTrue(rig.ship.getStateFlag(ID.F.NoFuel), "NoFuel fixture was not applied");
                    rig.after(4, () -> {
                        helper.assertTrue(!ShipCombatGate.engagement(rig.ship).engaged(), "Unfuelled ship could fire");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_logout")
    public static void logoutClearsPendingAndConfirmedRecords(GameTestHelper helper) {
        lifecycle(helper, rig -> MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(rig.owner)));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_dimension")
    public static void ownerDimensionChangeClearsPendingAndConfirmedRecords(GameTestHelper helper) {
        lifecycle(helper, rig -> MinecraftForge.EVENT_BUS.post(
                new PlayerEvent.PlayerChangedDimensionEvent(rig.owner, Level.OVERWORLD, Level.NETHER)));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_death")
    public static void ownerDeathClearsPendingAndConfirmedRecords(GameTestHelper helper) {
        lifecycle(helper, rig -> MinecraftForge.EVENT_BUS.post(
                new LivingDeathEvent(rig.owner, helper.getLevel().damageSources().generic())));
    }

    private static void lifecycle(GameTestHelper helper, Consumer<Rig> reset) {
        fixture(helper, rig -> {
            rig.success(rig.a);
            rig.after(4, () -> {
                rig.assertAssist(rig.a);
                rig.success(rig.b);
                reset.accept(rig);
                rig.ship.setEntityRevengeTarget(null);
                rig.after(4, () -> {
                    helper.assertTrue(ShipOwnerAssistHandler.latest(helper.getLevel(), rig.owner.getUUID()).isEmpty(),
                            "Lifecycle reset left pending or confirmed assistance");
                    helper.assertTrue(rig.ship.currentTargetLock().isEmpty(), "Lifecycle reset left an assist lock");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_ship_load")
    public static void shipLoadDoesNotPersistTheAssistLock(GameTestHelper helper) {
        fixture(helper, rig -> {
            rig.success(rig.a);
            rig.after(4, () -> {
                rig.assertAssist(rig.a);
                CompoundTag saved = new CompoundTag();
                rig.ship.addAdditionalSaveData(saved);
                rig.ship.readAdditionalSaveData(saved);
                helper.assertTrue(rig.ship.currentTargetLock().isEmpty(), "Load retained transient assistance");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 180,
            batch = "isolated_owner_assist_aircraft_switch")
    public static void aircraftSwitchesFromLiveOldTargetAndManualStillWins(GameTestHelper helper) {
        fixture(helper, rig -> {
            TrackingAirplane plane = rig.plane(rig.a, 10D);
            rig.after(24, () -> {
                helper.assertTrue(plane.getEntityTarget() == rig.a && rig.a.isAlive(), "Old aircraft target must be alive");
                rig.success(rig.b);
                rig.after(18, () -> {
                    helper.assertTrue(plane.getEntityTarget() == rig.b, "Aircraft did not switch within a selection cycle");
                    helper.assertTrue(plane.priorityTarget().orElseThrow().source() == TargetSource.OWNER_ASSIST,
                            "Aircraft assistance source was not projected");
                    rig.after(42, () -> {
                        helper.assertTrue(plane.lastAttack == rig.b, "Attack execution kept the old cached target");
                        helper.assertTrue(plane.shortestInterval >= 35, "Target change restarted the attack timer");
                        rig.ship.setManualTarget(rig.c);
                        rig.after(18, () -> {
                            helper.assertTrue(plane.getEntityTarget() == rig.c
                                    && plane.priorityTarget().orElseThrow().source() == TargetSource.MANUAL,
                                    "Aircraft did not prioritize the host's valid MANUAL lock");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_aircraft_distance")
    public static void aircraftUsesItsOwnDistanceRatherThanTheHostDistance(GameTestHelper helper) {
        fixture(helper, rig -> {
            TrackingAirplane plane = rig.plane(rig.a, 80D);
            rig.success(rig.b);
            rig.after(18, () -> {
                rig.assertAssist(rig.b);
                helper.assertTrue(plane.distanceToSqr(rig.b) > 64D * 64D, "Aircraft control must be outside assist range");
                helper.assertTrue(plane.getEntityTarget() == rig.a && plane.priorityTarget().isEmpty(),
                        "Aircraft used host distance for assistance");
                rig.anchors.put(rig.ship, rig.origin.add(80D, 0D, 0D));
                rig.anchors.put(plane, rig.origin.add(10D, 0D, 0D));
                rig.after(18, () -> {
                    helper.assertTrue(rig.ship.distanceToSqr(rig.b) > 64D * 64D, "Host control must now be far away");
                    helper.assertTrue(plane.getEntityTarget() == rig.b, "Aircraft wrongly required host assist distance");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_aircraft_return")
    public static void assistanceCannotReviveReturningEmptyOrExpiredAircraft(GameTestHelper helper) {
        fixture(helper, rig -> {
            TrackingAirplane returning = rig.plane(rig.a, 10D);
            TrackingAirplane empty = rig.plane(rig.a, 11D);
            TrackingAirplane expired = rig.plane(rig.a, 12D);
            returning.returning();
            empty.setNumAmmoLight(0);
            empty.setNumAmmoHeavy(0);
            expired.tickCount = 1200;
            helper.assertTrue(!returning.activeForOwnerAssist() && !empty.canFindTarget() && expired.tickCount == 1200,
                    "Return controls were not applied");
            rig.success(rig.b);
            rig.after(18, () -> {
                for (TrackingAirplane plane : new TrackingAirplane[]{returning, empty, expired}) {
                    helper.assertTrue(!plane.activeForOwnerAssist() && plane.priorityTarget().isEmpty(),
                            "Assistance revived an inactive aircraft");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_aircraft_host_death")
    public static void hostDeathReturnsAircraftResourcesOnlyOnce(GameTestHelper helper) {
        fixture(helper, rig -> {
            TrackingAirplane plane = rig.plane(rig.a, 10D);
            rig.ship.setHealth(0F);
            rig.after(5, () -> {
                helper.assertTrue(plane.isRemoved() && plane.returns == 1, "Host loss did not return resources exactly once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100,
            batch = "isolated_owner_assist_control")
    public static void noRecordDoesNotReplaceLivingAircraftTarget(GameTestHelper helper) {
        fixture(helper, rig -> {
            TrackingAirplane plane = rig.plane(rig.a, 10D);
            rig.after(25, () -> {
                helper.assertTrue(rig.ship.currentTargetLock().isEmpty(), "Passive no-record control acquired a target");
                helper.assertTrue(plane.getEntityTarget() == rig.a && plane.priorityTarget().isEmpty(),
                        "No-record control changed ordinary aircraft retention");
                helper.succeed();
            });
        });
    }

    private static void fixture(GameTestHelper helper, Consumer<Rig> scenario) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Rig rig = new Rig(helper);
            helper.onEachTick(rig::pin);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(rig.ship.tickCount >= 24, "Owner and targets must follow initAI");
                rig.owner = rig.player();
                rig.ship.setOwnerUUID(rig.owner.getUUID());
                rig.ship.setPlayerUID(rig.owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                        .orElseThrow(() -> new GameTestAssertException("Missing owner capability")).getPlayerUID());
                helper.assertTrue(rig.ship.getOwner() == rig.owner && rig.ship.hasTargetAuthority(), "NEW owner must resolve");
                rig.a = rig.target(8D);
                rig.b = rig.target(12D);
                rig.c = rig.target(14D);
                scenario.accept(rig);
            });
        }, new Vec3(0D, 4D, 1D), new Vec3(14D, 4D, 1D), new Vec3(80D, 4D, 1D));
    }

    private static final class Rig {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final Vec3 origin;
        private final Map<Entity, Vec3> anchors = new HashMap<>();
        private final BasicEntityShip ship;
        private ServerPlayer owner;
        private Mob a;
        private Mob b;
        private Mob c;

        private Rig(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.origin = helper.absoluteVec(new Vec3(0D, 4D, 1D));
            this.ship = this.entities.add(ModEntities.CV_AKAGI.get().create(helper.getLevel()));
            helper.assertTrue(this.ship != null, "Could not create carrier");
            this.ship.setStateMinor(ID.M.NumGrudge, 100_000);
            this.ship.setStateFlag(ID.F.NoFuel, false);
            this.ship.setStateFlag(ID.F.PassiveAI, true);
            this.ship.setInvulnerable(true);
            this.ship.calcShipAttributes(31, false);
            this.ship.setHealth(this.ship.getMaxHealth());
            this.ship.setNoGravity(true);
            helper.assertTrue(this.ship.getStateMinor(ID.M.NumGrudge) > 0 && !this.ship.getStateFlag(ID.F.NoFuel)
                    && this.ship.getHealth() == this.ship.getMaxHealth() && this.ship.isInvulnerable(),
                    "Carrier fuel/HP fixture was not applied");
            GameTestEntities.removeGoalsEachTick(helper, this.ship, goal -> !(goal instanceof ShipTargetAuthorityGoal));
            add(this.ship, 0D);
        }

        private void pin() {
            this.anchors.forEach((entity, position) -> {
                if (!entity.isRemoved()) {
                    entity.moveTo(position.x, position.y, position.z);
                    entity.setDeltaMovement(Vec3.ZERO);
                }
            });
        }

        private void add(Entity entity, double x) {
            Vec3 position = this.origin.add(x, 0D, 0D);
            entity.moveTo(position.x, position.y, position.z);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(entity), "Could not add fixture entity");
            GameTestEntities.assertRegistered(this.helper, entity);
            this.anchors.put(entity, position);
        }

        private ServerPlayer player() {
            return preparePlayer(FakePlayerFactory.get(this.helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "assist_" + NEXT_UID.getAndIncrement())));
        }

        private ServerPlayer preparePlayer(ServerPlayer player) {
            player.getAbilities().invulnerable = false;
            player.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new GameTestAssertException("Missing player capability"))
                    .setPlayerUID(NEXT_UID.getAndIncrement());
            player.moveTo(this.origin.add(1D, 0D, 0D));
            this.helper.getLevel().addNewPlayer(player);
            this.helper.assertTrue(player.isAlive() && !player.hasDisconnected()
                    && player.level().getPlayerByUUID(player.getUUID()) == player, "Player must be available");
            this.anchors.put(player, player.position());
            GameTestEntities.onCompletion(this.helper, () -> {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                this.helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }, "owner cleanup");
            return player;
        }

        private Mob target(double x) {
            Mob target = this.entities.add(EntityType.COW.create(this.helper.getLevel()));
            this.helper.assertTrue(target != null, "Could not create successful-attack target");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.setHealth(target.getMaxHealth());
            this.helper.assertTrue(!target.isInvulnerable() && target.getHealth() > 4F, "Damage target must be vulnerable");
            add(target, x);
            return target;
        }

        private void success(LivingEntity target) {
            target.invulnerableTime = 0;
            Vec3 attackPosition = target.position().add(-1D, 0D, 0D);
            this.owner.moveTo(attackPosition);
            this.anchors.put(this.owner, attackPosition);
            float before = target.getHealth();
            this.owner.attack(target);
            this.helper.assertTrue(target.getHealth() < before, "Player melee must actually reduce health");
        }

        private void arrow(Mob target, Entity shooter) {
            Arrow arrow = this.entities.add(new Arrow(this.helper.getLevel(), target.getX() - 2D,
                    target.getY() + 0.8D, target.getZ()));
            AABB flightPath = arrow.getBoundingBox().expandTowards(2D, 0D, 0D);
            Map<BlockPos, BlockState> displaced = new HashMap<>();
            GameTestEntities.onCompletion(this.helper, () -> displaced.forEach((pos, state) ->
                    this.helper.getLevel().setBlock(pos, state, 3)), "arrow flight path cleanup");
            BlockPos.betweenClosedStream(flightPath).forEach(pos -> {
                BlockState state = this.helper.getLevel().getBlockState(pos);
                if (!state.isAir()) {
                    displaced.put(pos.immutable(), state);
                    this.helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            });
            this.helper.assertTrue(!this.helper.getLevel().getBlockCollisions(arrow, flightPath).iterator().hasNext(),
                    "Actual arrow flight path must be clear, including its spawn position");
            arrow.setOwner(shooter);
            arrow.setNoGravity(true);
            arrow.setBaseDamage(4D);
            arrow.shoot(1D, 0D, 0D, 1F, 0F);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(arrow), "Could not add actual arrow");
        }

        private TrackingAirplane plane(Entity target, double x) {
            TrackingAirplane plane = this.entities.add(new TrackingAirplane(this.helper.getLevel()));
            plane.initAttrs(this.ship, target, 1, (float) this.ship.getY());
            plane.setNumAmmoLight(1000);
            plane.setInvulnerable(true);
            plane.getAttrs().setAttrsBuffed(ID.Attrs.SPD, 1000F);
            this.helper.assertTrue(plane.getAttrs().getAttackSpeed() == 1000F, "Aircraft attack speed was not applied");
            add(plane, x);
            return plane;
        }

        private RecentOwnerAttack latest() {
            this.helper.assertTrue(ShipOwnerAssistHandler.latest(this.helper.getLevel(), this.owner.getUUID()).isPresent(),
                    "Expected a confirmed owner attack");
            return ShipOwnerAssistHandler.latest(this.helper.getLevel(), this.owner.getUUID()).orElseThrow();
        }

        private void assertAssist(Entity target) {
            this.helper.assertTrue(this.ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.OWNER_ASSIST)
                    .isPresent() && this.ship.getEntityTarget() == target, "Assistance did not reach target authority");
        }

        private void after(int ticks, Runnable action) {
            this.helper.runAfterDelay(ticks, action);
        }
    }

    /** Enables a real damage recipient without changing the generic, deliberately immune fake players. */
    private static final class VulnerableOpponent extends FakePlayer {
        private VulnerableOpponent(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "assist_opponent"));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return this.isInvulnerable() || this.getAbilities().invulnerable;
        }

        @Override
        public boolean canHarmPlayer(Player player) {
            return this.getTeam() == null && player.getTeam() == null;
        }
    }

    private static final class TrackingAirplane extends EntityAirplane {
        private Entity lastAttack;
        private int lastAttackTick = -1;
        private int shortestInterval = Integer.MAX_VALUE;
        private int returns;

        private TrackingAirplane(Level level) {
            super(ModEntities.AIRPLANE.get(), level);
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            this.lastAttack = target;
            if (this.lastAttackTick >= 0) this.shortestInterval = Math.min(this.shortestInterval, this.tickCount - this.lastAttackTick);
            this.lastAttackTick = this.tickCount;
            return true;
        }

        @Override
        protected void returnSummonResource() {
            this.returns++;
            super.returnSummonResource();
        }

        private void returning() {
            this.backHome = true;
            this.setEntityTarget(null);
        }
    }
}
