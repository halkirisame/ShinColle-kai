package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntitySummon;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipKillCreditGameTests {

    private static final Vec3 OWNER_POSITION = new Vec3(6D, 2D, 4D);
    private static final Vec3 SHIP_POSITION = new Vec3(2D, 2D, 4D);
    private static final Vec3 VICTIM_POSITION = new Vec3(4D, 2D, 4D);

    private ShipKillCreditGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_native")
    public static void tameShipPreservesNativeTotalAndCompletesEntityStatOnce(GameTestHelper helper) {
        run(helper, f -> {
            Zombie victim = f.zombie();
            KillCounts before = f.counts(f.owner, victim);
            DamageSource source = f.ship.damageSources().mobAttack(f.ship);
            helper.assertTrue(applyHit(source, victim, 100F), "Fixture: lethal ship hit was rejected");
            f.assertDead(victim);
            helper.assertTrue(victim.getLastDamageSource() == source, "Ship damage source was replaced");
            f.assertDelta(f.owner, victim, before, 1, 0, 1, "Native ship accounting before END");
            f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 1, 1, 1, "Ship accounting after END"));
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_rensouhou")
    public static void rensouhouKillCompletesMissingCountersOnce(GameTestHelper helper) {
        run(helper, f -> summonKill(f, f.summon(false)));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_airplane")
    public static void airplaneKillCompletesMissingCountersOnce(GameTestHelper helper) {
        run(helper, f -> summonKill(f, f.summon(true)));
    }

    private static void summonKill(Fixture f, BasicEntitySummon summon) {
        Zombie victim = f.zombie();
        KillCounts before = f.counts(f.owner, victim);
        DamageSource source = summon.damageSources().mobAttack(summon);
        f.helper.assertTrue(applyHit(source, victim, 100F), "Fixture: lethal summon hit was rejected");
        f.assertDead(victim);
        f.helper.assertTrue(victim.getLastDamageSource() == source, "Summon damage source was replaced");
        f.assertDelta(f.owner, victim, before, 0, 0, 0, "Summon accounting must wait for END");
        f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 1, 1, 1, "Summon accounting after END"));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_mixed")
    public static void summonKillPreservesDifferentNativePlayerRecipient(GameTestHelper helper) {
        run(helper, f -> {
            ServerPlayer playerA = f.player();
            helper.assertTrue(!playerA.getUUID().equals(f.owner.getUUID()), "Fixture: player UUIDs overlap");
            Zombie victim = f.zombie();
            BasicEntitySummon summon = f.summon(false);
            KillCounts beforeA = f.counts(playerA, victim);
            KillCounts beforeB = f.counts(f.owner, victim);
            helper.assertTrue(victim.hurt(playerA.damageSources().playerAttack(playerA), 1F),
                    "Fixture: player A's initial hit was rejected");
            helper.assertTrue(victim.getKillCredit() == playerA, "Fixture: A did not acquire native credit");
            victim.invulnerableTime = 0;
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), victim, 100F),
                    "Fixture: summon killing blow was rejected");
            f.assertDead(victim);
            helper.assertTrue(victim.getKillCredit() == playerA, "Summon replaced native recipient A with owner B");
            f.afterEnd(() -> {
                f.assertDelta(playerA, victim, beforeA, 1, 1, 1, "Existing native recipient A");
                f.assertDelta(f.owner, victim, beforeB, 0, 0, 0, "Summon owner B must not steal vanilla credit");
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_two_victims")
    public static void twoSummonKillsInSameTickEachCountOnce(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntitySummon summon = f.summon(false);
            Zombie first = f.zombie();
            Zombie second = f.zombie();
            KillCounts before = f.counts(f.owner, first);
            long tick = helper.getLevel().getGameTime();
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), first, 100F), "First hit rejected");
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), second, 100F), "Second hit rejected");
            helper.assertTrue(helper.getLevel().getGameTime() == tick, "Fixture: kills were not in one tick");
            f.assertDead(first);
            f.assertDead(second);
            f.afterEnd(() -> f.assertDelta(f.owner, first, before, 2, 2, 2, "Two distinct same-tick deaths"));
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_armor")
    public static void armorSurvivalKeepsOriginalSourceAttackerAndKnockback(GameTestHelper helper) {
        run(helper, f -> survivingZombieHit(f, false, false));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_resistance")
    public static void resistanceSurvivalKeepsOriginalSourceAttackerAndKnockback(GameTestHelper helper) {
        run(helper, f -> survivingZombieHit(f, true, false));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_totem")
    public static void zombieTotemRescueKeepsCombatAndAllKillCountersUnchanged(GameTestHelper helper) {
        run(helper, f -> survivingZombieHit(f, false, true));
    }

    private static void survivingZombieHit(Fixture f, boolean resistance, boolean totem) {
        Zombie control = f.zombie();
        Zombie victim = f.zombie();
        for (Zombie zombie : new Zombie[]{control, victim}) {
            if (resistance) {
                zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                f.helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Fixture: resistance missing");
            }
            if (totem) {
                zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                f.helper.assertTrue(zombie.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "Fixture: totem missing");
            }
        }
        KillCounts before = f.counts(f.owner, victim);
        DamageSource source = f.ship.damageSources().mobAttack(f.ship);
        float amount = totem ? 21F : 20F;
        f.helper.assertTrue(control.hurt(source, amount), "Fixture: original-source control hit rejected");
        f.helper.assertTrue(applyHit(source, victim, amount), "Fixture: tested hit rejected");
        f.helper.assertTrue(control.isAlive() && victim.isAlive(), "A mitigated or totem-rescued hit killed its victim");
        f.helper.assertTrue(Math.abs(victim.getHealth() - control.getHealth()) < 0.0001F,
                "Health differs from original-source control: " + victim.getHealth() + " vs " + control.getHealth());
        f.assertOriginalCombat(victim, control, source);
        if (totem) {
            f.helper.assertTrue(victim.getOffhandItem().isEmpty() && control.getOffhandItem().isEmpty(),
                    "Totem rescue did not consume exactly the equipped totems");
            f.helper.assertTrue(victim.getHealth() == 1F && control.getHealth() == 1F, "Totem rescue health changed");
        }
        f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 0, 0, 0, "Surviving hit"));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_wolf")
    public static void differentOwnerWolfKeepsNonPlayerDamageReduction(GameTestHelper helper) {
        run(helper, f -> {
            ServerPlayer wolfOwner = f.player();
            Wolf control = f.wolf(wolfOwner);
            Wolf victim = f.wolf(wolfOwner);
            KillCounts before = f.counts(f.owner, victim);
            KillCounts beforeWolfOwner = f.counts(wolfOwner, victim);
            DamageSource source = f.ship.damageSources().mobAttack(f.ship);
            helper.assertTrue(control.hurt(source, 9F), "Fixture: wolf control hit rejected");
            helper.assertTrue(applyHit(source, victim, 9F), "Fixture: wolf tested hit rejected");
            helper.assertTrue(control.isAlive() && victim.isAlive() && control.getHealth() == 4F && victim.getHealth() == 4F,
                    "Non-player hit must leave the different-owner wolf at HP4, got " + victim.getHealth());
            f.assertOriginalCombat(victim, control, source);
            f.afterEnd(() -> {
                f.assertDelta(f.owner, victim, before, 0, 0, 0, "Wolf attacker owner");
                f.assertDelta(wolfOwner, victim, beforeWolfOwner, 0, 0, 0, "Wolf owner");
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_death_cancel")
    public static void laterLowestDeathCancellationAtZeroHealthDoesNotCount(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntitySummon summon = f.summon(false);
            Zombie victim = f.zombie();
            KillCounts before = f.counts(f.owner, victim);
            int[] deaths = {0};
            int[] drops = {0};
            f.listen(EventPriority.LOWEST, LivingDeathEvent.class, event -> {
                if (event.getEntity() == victim) {
                    deaths[0]++;
                    event.setCanceled(true);
                }
            });
            f.listen(EventPriority.LOWEST, LivingDropsEvent.class, event -> {
                if (event.getEntity() == victim) {
                    drops[0]++;
                }
            });
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), victim, 100F), "Hit rejected");
            helper.assertTrue(deaths[0] == 1 && drops[0] == 0 && victim.getHealth() == 0F,
                    "Fixture: death must be canceled at HP0 before drops");
            f.afterEnd(() -> {
                helper.assertTrue(victim.getHealth() == 0F && drops[0] == 0, "Canceled death fixture changed");
                f.assertDelta(f.owner, victim, before, 0, 0, 0, "Canceled death at HP0");
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_drops_cancel")
    public static void canceledLivingDropsStillCompletesDeathOnce(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntitySummon summon = f.summon(false);
            Zombie victim = f.zombie();
            KillCounts before = f.counts(f.owner, victim);
            int[] drops = {0};
            f.listen(EventPriority.LOWEST, LivingDropsEvent.class, event -> {
                if (event.getEntity() == victim) {
                    drops[0]++;
                    event.setCanceled(true);
                }
            });
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), victim, 100F), "Hit rejected");
            f.assertDead(victim);
            helper.assertTrue(drops[0] == 1, "Fixture: canceled LivingDrops was not observed");
            f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 1, 1, 1, "Canceled drops, successful death"));
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_duplicate")
    public static void repeatedDropsReceiptBeforeAndAfterEndCountsDeathOnce(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntitySummon summon = f.summon(false);
            Zombie victim = f.zombie();
            KillCounts before = f.counts(f.owner, victim);
            LivingDropsEvent[] receipt = {null};
            f.listen(EventPriority.LOWEST, LivingDropsEvent.class, event -> {
                if (event.getEntity() == victim) {
                    receipt[0] = event;
                }
            });
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), victim, 100F), "Hit rejected");
            f.assertDead(victim);
            helper.assertTrue(receipt[0] != null, "Fixture: normal death produced no drops receipt");
            repost(receipt[0]);
            helper.startSequence().thenIdle(2).thenExecute(() -> {
                f.assertDelta(f.owner, victim, before, 1, 1, 1, "Duplicate receipt before END");
                repost(receipt[0]);
            }).thenIdle(2).thenExecute(() ->
                    f.assertDelta(f.owner, victim, before, 1, 1, 1, "Duplicate receipt after END")).thenSucceed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_revive")
    public static void revivalByLaterDropsListenerBeforeEndDiscardsSupplement(GameTestHelper helper) {
        run(helper, f -> {
            BasicEntitySummon summon = f.summon(false);
            Zombie victim = f.zombie();
            KillCounts before = f.counts(f.owner, victim);
            int[] revivals = {0};
            f.listen(EventPriority.LOWEST, LivingDropsEvent.class, event -> {
                if (event.getEntity() == victim) {
                    revivals[0]++;
                    setDead(victim, false);
                    victim.setHealth(20F);
                    victim.deathTime = 0;
                    event.setCanceled(true);
                }
            });
            helper.assertTrue(applyHit(summon.damageSources().mobAttack(summon), victim, 100F), "Hit rejected");
            helper.assertTrue(revivals[0] == 1 && victim.isAlive() && victim.getHealth() == 20F,
                    "Fixture: later drops listener did not revive the victim");
            f.afterEnd(() -> {
                helper.assertTrue(victim.isAlive(), "Revived victim died again before END verification");
                f.assertDelta(f.owner, victim, before, 0, 0, 0, "Revival before END");
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_player_exclusion")
    public static void playerVictimReceiptIsExcluded(GameTestHelper helper) {
        run(helper, f -> excludedVictimReceipt(f, f.player()));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_ship_exclusion")
    public static void shipVictimReceiptIsExcluded(GameTestHelper helper) {
        run(helper, f -> excludedVictimReceipt(f, f.ship(f.player())));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_summon_exclusion")
    public static void summonVictimReceiptIsExcluded(GameTestHelper helper) {
        run(helper, f -> excludedVictimReceipt(f, f.summon(false)));
    }

    private static void excludedVictimReceipt(Fixture f, LivingEntity victim) {
        BasicEntitySummon attacker = f.summon(true);
        KillCounts before = f.counts(f.owner, victim);
        // Exercise only the accounting boundary; ship/PVP combat policy is not part of this fixture.
        victim.setHealth(0F);
        setDead(victim, true);
        f.assertDead(victim);
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(victim, attacker.damageSources().mobAttack(attacker),
                new ArrayList<>(), 0, false));
        f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 0, 0, 0, "Excluded victim receipt"));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_disconnect")
    public static void ownerDisconnectBeforeEndDiscardsSupplement(GameTestHelper helper) {
        run(helper, f -> {
            Zombie victim = f.zombie();
            BasicEntitySummon summon = f.summon(false);
            KillCounts before = f.counts(f.owner, victim);
            applyHit(summon.damageSources().mobAttack(summon), victim, 100F);
            f.assertDead(victim);
            helper.getLevel().removePlayerImmediately(f.owner, RemovalReason.DISCARDED);
            helper.assertTrue(helper.getLevel().getPlayerByUUID(f.owner.getUUID()) == null, "Fixture: owner still present");
            f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 0, 0, 0, "Disconnected owner"));
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_ownership_change")
    public static void ownershipChangeBeforeEndDoesNotRedirectSupplement(GameTestHelper helper) {
        run(helper, f -> {
            Zombie victim = f.zombie();
            BasicEntitySummon summon = f.summon(false);
            ServerPlayer replacement = f.player();
            KillCounts beforeOwner = f.counts(f.owner, victim);
            KillCounts beforeReplacement = f.counts(replacement, victim);
            applyHit(summon.damageSources().mobAttack(summon), victim, 100F);
            f.assertDead(victim);
            f.ship.setOwnerUUID(replacement.getUUID());
            helper.assertTrue(f.ship.getOwner() == replacement, "Fixture: ownership did not change");
            f.afterEnd(() -> {
                f.assertDelta(f.owner, victim, beforeOwner, 0, 0, 0, "Former owner");
                f.assertDelta(replacement, victim, beforeReplacement, 0, 0, 0, "Replacement owner");
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_score")
    public static void summonKillPreservesDeathScoreWithoutAwardingPlayerExperience(GameTestHelper helper) {
        run(helper, f -> {
            Zombie victim = f.zombie();
            BasicEntitySummon summon = f.summon(false);
            KillCounts before = f.counts(f.owner, victim);
            ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, victim, 7, "f_20897_");
            int deathScore = ObfuscationReflectionHelper.getPrivateValue(LivingEntity.class, victim, "f_20897_");
            helper.assertTrue(deathScore == 7, "Fixture: death score was not set");
            int score = f.owner.getScore();
            int experience = f.owner.totalExperience;
            applyHit(summon.damageSources().mobAttack(summon), victim, 100F);
            f.assertDead(victim);
            f.afterEnd(() -> {
                f.assertDelta(f.owner, victim, before, 1, 1, 1, "Score-bearing death");
                helper.assertTrue(f.owner.getScore() - score == 7, "Captured death score must be awarded exactly once");
                helper.assertTrue(f.owner.totalExperience == experience, "Kill accounting must not award player XP");
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_kill_credit_direct_player")
    public static void directPlayerKillKeepsNativeCountersWithoutDuplication(GameTestHelper helper) {
        run(helper, f -> {
            Zombie victim = f.zombie();
            KillCounts before = f.counts(f.owner, victim);
            applyHit(f.owner.damageSources().playerAttack(f.owner), victim, 100F);
            f.assertDead(victim);
            f.assertDelta(f.owner, victim, before, 1, 1, 1, "Native direct player death");
            f.afterEnd(() -> f.assertDelta(f.owner, victim, before, 1, 1, 1, "Direct player after END"));
        });
    }

    private static boolean applyHit(DamageSource source, LivingEntity victim, float amount) {
        return victim.hurt(source, amount);
    }

    private static void repost(LivingDropsEvent receipt) {
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(receipt.getEntity(), receipt.getSource(),
                new ArrayList<>(receipt.getDrops()), receipt.getLootingLevel(), receipt.isRecentlyHit()));
    }

    private static void setDead(LivingEntity entity, boolean dead) {
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, entity, dead, "f_20890_");
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> test) {
        GameTestEntities.whenPositionsTicking(helper, () -> test.accept(new Fixture(helper)),
                OWNER_POSITION, SHIP_POSITION, VICTIM_POSITION);
    }

    private record KillCounts(int mobs, int players, int kind, int scoreboard) {
    }

    /** FakePlayer suppresses awardStat; restore the normal counter operation, not real-player eligibility. */
    private static final class StatRecordingPlayer extends FakePlayer {
        private StatRecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void awardStat(Stat stat, int amount) {
            this.getStats().increment(this, stat, amount);
            this.getScoreboard().forAllObjectives(stat, this.getScoreboardName(), score -> score.add(amount));
        }
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final Objective objective;
        private final ServerPlayer owner;
        private final BasicEntityShip ship;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            String name = "kc" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            this.objective = helper.getLevel().getScoreboard().addObjective(name, ObjectiveCriteria.KILL_COUNT_ALL,
                    Component.literal(name), ObjectiveCriteria.RenderType.INTEGER);
            GameTestEntities.onCompletion(helper, () -> helper.getLevel().getScoreboard().removeObjective(this.objective),
                    "kill objective cleanup");
            this.owner = this.player();
            this.ship = this.ship(this.owner);
        }

        private ServerPlayer player() {
            UUID uuid = UUID.randomUUID();
            String name = "kc" + uuid.toString().replace("-", "").substring(0, 12);
            ServerPlayer player = new StatRecordingPlayer(this.helper.getLevel(), new GameProfile(uuid, name));
            GameTestEntities.onCompletion(this.helper,
                    () -> {
                        if (!player.isRemoved()) this.helper.getLevel().removePlayerImmediately(player, RemovalReason.DISCARDED);
                    }, "player cleanup");
            player.moveTo(this.helper.absoluteVec(OWNER_POSITION));
            player.setNoGravity(true);
            this.helper.getLevel().addNewPlayer(player);
            GameTestEntities.assertRegistered(this.helper, player);
            this.helper.assertTrue(this.helper.getLevel().getPlayerByUUID(uuid) == player,
                    "Fixture: owner is not registered for ownership resolution");
            return player;
        }

        private BasicEntityShip ship(ServerPlayer player) {
            BasicEntityShip result = this.entities.add(ModEntities.BB_KONGOU.get().create(this.helper.getLevel()));
            this.helper.assertTrue(result != null, "Fixture: failed to create ship");
            result.tame(player);
            result.setStateMinor(ID.M.NumGrudge, 100_000);
            result.setStateFlag(ID.F.NoFuel, false);
            result.setNoAi(true);
            result.setInvulnerable(true);
            this.spawn(result, SHIP_POSITION);
            this.helper.assertTrue(result.isTame() && result.getOwner() == player && result.getGrudge() > 0
                            && !result.getStateFlag(ID.F.NoFuel) && result.isNoAi(),
                    "Fixture: tame ship owner, fuel or no-AI setup failed");
            return result;
        }

        private BasicEntitySummon summon(boolean airplane) {
            BasicEntitySummon summon = this.entities.add(airplane
                    ? ModEntities.AIRPLANE.get().create(this.helper.getLevel())
                    : ModEntities.RENSOUHOU.get().create(this.helper.getLevel()));
            this.helper.assertTrue(summon != null, "Fixture: failed to create summon");
            summon.setHost(this.ship);
            summon.setNoAi(true);
            summon.setInvulnerable(true);
            this.spawn(summon, SHIP_POSITION);
            this.helper.assertTrue(summon.getHost() == this.ship && summon.isNoAi(), "Fixture: summon host/no-AI setup failed");
            return summon;
        }

        private Zombie zombie() {
            Zombie zombie = this.entities.add(EntityType.ZOMBIE.create(this.helper.getLevel()));
            this.helper.assertTrue(zombie != null, "Fixture: failed to create zombie");
            zombie.setNoAi(true);
            zombie.setBaby(false);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(2D);
            zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0D);
            zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0D);
            zombie.setHealth(20F);
            zombie.invulnerableTime = 0;
            this.spawn(zombie, VICTIM_POSITION);
            this.helper.assertTrue(zombie.getHealth() == 20F && zombie.getArmorValue() == 2
                            && zombie.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0D && zombie.isNoAi(),
                    "Fixture: zombie must have HP20, armor2, no knockback resistance and no AI");
            return zombie;
        }

        private Wolf wolf(ServerPlayer player) {
            Wolf wolf = this.entities.add(EntityType.WOLF.create(this.helper.getLevel()));
            this.helper.assertTrue(wolf != null, "Fixture: failed to create wolf");
            wolf.tame(player);
            wolf.setNoAi(true);
            wolf.getAttribute(Attributes.ARMOR).setBaseValue(0D);
            wolf.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0D);
            wolf.setHealth(9F);
            wolf.invulnerableTime = 0;
            this.spawn(wolf, VICTIM_POSITION);
            this.helper.assertTrue(wolf.isTame() && wolf.getOwner() == player && player != this.owner
                            && wolf.getHealth() == 9F && wolf.getArmorValue() == 0 && wolf.isNoAi(),
                    "Fixture: wolf must have a different owner and HP9/armor0/no AI");
            return wolf;
        }

        private void spawn(Mob mob, Vec3 position) {
            mob.moveTo(this.helper.absoluteVec(position));
            mob.setNoGravity(true);
            mob.setDeltaMovement(Vec3.ZERO);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(mob), "Fixture: failed to add entity");
            GameTestEntities.assertRegistered(this.helper, mob);
        }

        private KillCounts counts(ServerPlayer player, LivingEntity victim) {
            return new KillCounts(player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)),
                    player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)),
                    player.getStats().getValue(Stats.ENTITY_KILLED.get(victim.getType())),
                    player.getScoreboard().getOrCreatePlayerScore(player.getScoreboardName(), this.objective).getScore());
        }

        private void assertDelta(ServerPlayer player, LivingEntity victim, KillCounts before,
                                 int mobs, int kind, int scoreboard, String context) {
            KillCounts after = this.counts(player, victim);
            this.helper.assertTrue(after.mobs() - before.mobs() == mobs, context + ": MOB_KILLS delta "
                    + (after.mobs() - before.mobs()) + " != " + mobs);
            this.helper.assertTrue(after.players() == before.players(), context + ": PLAYER_KILLS unexpectedly changed");
            this.helper.assertTrue(after.kind() - before.kind() == kind, context + ": ENTITY_KILLED delta "
                    + (after.kind() - before.kind()) + " != " + kind);
            this.helper.assertTrue(after.scoreboard() - before.scoreboard() == scoreboard, context + ": KILL_COUNT_ALL delta "
                    + (after.scoreboard() - before.scoreboard()) + " != " + scoreboard);
        }

        private void assertDead(LivingEntity victim) {
            boolean dead = ObfuscationReflectionHelper.getPrivateValue(LivingEntity.class, victim, "f_20890_");
            this.helper.assertTrue(dead && !victim.isAlive() && victim.getHealth() == 0F && !victim.isRemoved(),
                    "Fixture: normal death must have dead=true, HP0 and an unremoved victim");
        }

        private void assertOriginalCombat(LivingEntity victim, LivingEntity control, DamageSource source) {
            this.helper.assertTrue(this.ship.isAlive() && this.owner.isAlive(), "Attacker or owner did not survive the fixture");
            this.helper.assertTrue(victim.getLastDamageSource() == source && control.getLastDamageSource() == source,
                    "Surviving hit did not retain the exact original damage source");
            this.helper.assertTrue(source.getEntity() == this.ship && source.getDirectEntity() == this.ship,
                    "Fixture: original source does not identify the ship");
            this.helper.assertTrue(victim.getLastHurtByMob() == this.ship && control.getLastHurtByMob() == this.ship,
                    "Surviving hit changed the retaliation attacker");
            Vec3 actual = victim.getDeltaMovement();
            Vec3 expected = control.getDeltaMovement();
            this.helper.assertTrue(actual.x > 0D && Math.abs(actual.z) < 0.000001D,
                    "Knockback must point away from the ship, not the owner on the opposite side: " + actual);
            this.helper.assertTrue(actual.distanceToSqr(expected) < 0.00000001D,
                    "Knockback differs from original-source control: " + actual + " vs " + expected);
        }

        private <E extends Event> void listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
            GameTestEntities.onCompletion(this.helper, () -> MinecraftForge.EVENT_BUS.unregister(listener), "event listener cleanup");
            MinecraftForge.EVENT_BUS.addListener(priority, true, type, listener);
        }

        private void afterEnd(Runnable assertions) {
            this.helper.startSequence().thenIdle(2).thenExecute(assertions).thenSucceed();
        }
    }
}
