package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipFireControlGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerShimakaze;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerShimakazeMob;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CAttackAnimationPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.utility.BuffHelper;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RensouhouFriendlyFireGameTests {
    private static final ResourceLocation WEAKNESS = new ResourceLocation("minecraft", "weakness");
    private static final Vec3 HOST = new Vec3(2.5D, 2D, 2.5D);
    private static final Vec3 TARGET = new Vec3(10.5D, 2D, 2.5D);

    private RensouhouFriendlyFireGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_rensouhou_ff_matrix")
    public static void bothVariantsRespectEveryOwnerAndDirectionalTeamRule(GameTestHelper helper) {
        synchronous(helper, f -> {
            ProbeShip own = f.ship(f.owner, false);
            ProbeShip other = f.ship(f.stranger, false);
            ProbeWolf pet = f.entities.add(new ProbeWolf(helper.getLevel()));
            pet.setTame(true);
            pet.setOwnerUUID(f.owner.getUUID());
            f.add(pet, TARGET);
            ProbeHostile wild = f.entities.add(new ProbeHostile(helper.getLevel()));
            f.add(wild, TARGET);
            ProbeCow cow = f.entities.add(new ProbeCow(helper.getLevel()));
            f.add(cow, TARGET);
            helper.assertTrue(TeamHelper.getPlayerUID(pet) == f.ownerUid && pet.getOwner() == f.owner,
                    "Pet must resolve its registered owner");
            helper.assertTrue(wild.getPlayerUID() == -100 && TeamHelper.getPlayerUID(cow) == 0,
                    "Wild and unowned target UID fixtures");
            for (boolean small : new boolean[]{false, true}) {
                for (boolean ff : new boolean[]{false, true}) {
                    f.ff(ff);
                    for (float raw : new float[]{100F, 300F}) {
                        f.direct(small, f.owner, f.owner.damage, false, raw);
                        f.direct(small, own, own.damage, false, raw);
                        f.direct(small, pet, pet.damage, false, raw);
                        for (int relation = 0; relation < 4; relation++) {
                            f.relation(relation);
                            f.direct(small, f.stranger, f.stranger.damage, ff, raw);
                            f.direct(small, other, other.damage, ff || relation != 1, raw);
                        }
                        f.direct(small, wild, wild.damage, true, raw);
                        f.direct(small, cow, cow.damage, true, raw);
                    }
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_rensouhou_ff_side_effects")
    public static void rejectedHitPreservesSittingMoraleAndRevengeWithPositiveControl(GameTestHelper helper) {
        synchronous(helper, f -> {
            f.ff(true);
            ProbeShip victim = f.ship(f.owner, false);
            victim.realHurt = true;
            victim.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
            helper.assertTrue(victim.getAttrs().getAttrsBuffed(ID.Attrs.DODGE) == 0F, "No-dodge fixture");
            for (boolean small : new boolean[]{false, true}) {
                victim.damage.reset();
                victim.setEntitySit(true);
                victim.setMorale(6000);
                victim.setEntityRevengeTarget(null);
                helper.assertTrue(victim.isOrderedToSit() && victim.getMorale() == 6000
                        && victim.getEntityRevengeTarget() == null && !victim.isInvulnerable(),
                        "Sitting vulnerable ship fixture");
                EntityRensouhou turret = f.turret(small, victim);
                turret.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, 100F);
                turret.setNumAmmoLight(1);
                float hp = victim.getHealth();
                helper.assertTrue(!turret.attackTarget(victim) && turret.isRemoved()
                        && turret.getNumAmmoLight() == 0, "Rejected last shot must still finish summon");
                helper.assertTrue(victim.damage.calls == 0 && victim.getHealth() == hp
                        && victim.isOrderedToSit() && victim.getMorale() == 6000
                        && victim.getEntityRevengeTarget() == null, "Rejected shot entered ship hurt side effects");
                EntityRensouhou control = f.turret(small, victim);
                victim.invulnerableTime = 0;
                victim.hurt(control.damageSources().mobAttack(control), 1F);
                helper.assertTrue(victim.damage.calls == 1 && !victim.isOrderedToSit()
                        && victim.getMorale() == 5995 && victim.getEntityRevengeTarget() == control,
                        "Positive control must expose standing, morale and revenge side effects");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_rensouhou_ff_compatibility")
    public static void legacyAndNewInputsStillRegisterNewAndProtectOwner(GameTestHelper helper) {
        synchronous(helper, f -> {
            for (ConfigHandler.ShipAiTargetAuthority input : new ConfigHandler.ShipAiTargetAuthority[]{
                    ConfigHandler.ShipAiTargetAuthority.LEGACY, ConfigHandler.ShipAiTargetAuthority.NEW}) {
                ConfigHandler.COMMON.shipAiTargetAuthority.set(input);
                ProbeShip host = f.ship(f.owner, false);
                host.setAIList();
                helper.assertTrue(ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW
                        && host.hasTargetAuthority() && host.newFireControlRegistered(),
                        "Compatibility input must register NEW authority and fire control: " + input);
                for (boolean ff : new boolean[]{false, true}) {
                    f.ff(ff);
                    for (boolean small : new boolean[]{false, true}) {
                        EntityRensouhou turret = f.turret(small, f.owner);
                        turret.setHost(host);
                        helper.assertTrue(!TeamHelper.doFriendlyFire(turret, f.owner), "Owner helper rejection");
                        f.owner.damage.reset();
                        turret.attackTarget(f.owner);
                        helper.assertTrue(f.owner.damage.calls == 0 && turret.getNumAmmoLight() == 5,
                                "Compatibility input bypassed owner protection");
                    }
                }
            }
        });
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "isolated_rensouhou_ff_owner_off")
    public static void ownerCaressCreatesProtectedSixShotSummonWithFireDisabled(GameTestHelper helper) {
        natural(helper, false, true, false, false);
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "isolated_rensouhou_ff_owner_on")
    public static void ownerCaressCreatesProtectedSixShotSummonWithFireEnabled(GameTestHelper helper) {
        natural(helper, true, true, false, false);
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "isolated_rensouhou_ff_stranger_off")
    public static void strangerCaressStillSummonsAndApproachesWithoutHurtingPlayer(GameTestHelper helper) {
        natural(helper, false, false, false, false);
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "isolated_rensouhou_ff_stranger_on")
    public static void strangerCaressStillDealsReducedDamageAndSuccessEffects(GameTestHelper helper) {
        natural(helper, true, false, false, false);
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "isolated_rensouhou_ff_small_lifetime")
    public static void smallVariantNaturallyConsumesSixProtectedShots(GameTestHelper helper) {
        natural(helper, true, true, true, false);
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "isolated_rensouhou_ff_new_enemy")
    public static void newFireControlStillSummonsAndAttacksWildEnemyShipgirl(GameTestHelper helper) {
        natural(helper, false, false, false, true);
    }

    private static void synchronous(GameTestHelper helper, java.util.function.Consumer<Fixture> verify) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (Fixture f = new Fixture(helper)) {
                verify.accept(f);
                helper.succeed();
            }
        }, HOST, TARGET);
    }

    private static void natural(GameTestHelper helper, boolean ff, boolean owner, boolean small, boolean combat) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Fixture f = new Fixture(helper);
            f.ff(ff);
            ProbeShip host = f.ship(f.owner, combat);
            host.moveTo(helper.absoluteVec(HOST));
            f.owner.moveTo(helper.absoluteVec(new Vec3(2.5D, 2D, 5.5D)));
            new NaturalScene(f, host, owner, small, combat).start();
        }, HOST, TARGET);
    }

    private static final class NaturalScene {
        private final Fixture f;
        private final ProbeShip host;
        private final boolean owner;
        private final boolean small;
        private final boolean combat;
        private LivingEntity target;
        private Capture damage;
        private EntityRensouhou turret;
        private boolean entered;
        private int previousAmmo = 6;
        private final List<Long> shots = new ArrayList<>();
        private double initialDistance;
        private float initialHealth;
        private AnimationPackets animation;

        private NaturalScene(Fixture f, ProbeShip host, boolean owner, boolean small, boolean combat) {
            this.f = f;
            this.host = host;
            this.owner = owner;
            this.small = small;
            this.combat = combat;
        }

        private void start() {
            this.f.helper.onEachTick(this::poll);
        }

        private void enter() {
            GameTestHelper h = this.f.helper;
            h.assertTrue(this.host.tickCount >= 24 && this.host.hasTargetAuthority(), "Wait for NEW registration");
            h.assertTrue(this.host.getHostEntity() == this.f.owner, "Natural success-hook source must be real owner");
            this.host.moveTo(h.absoluteVec(HOST));
            this.host.setAmmoLight(4 * this.host.getAmmoConsumption());
            this.host.numRensouhou = 1;
            h.assertTrue(this.host.getOwner() == this.f.owner && this.host.getPlayerUID() == this.f.ownerUid
                    && this.host.getStateMinor(ID.M.NumGrudge) > 0 && !this.host.getStateFlag(ID.F.NoFuel)
                    && this.host.isAlive() && this.host.hasAmmoLight() && this.host.numRensouhou == 1,
                    "Natural host needs registered owner, fuel, ammo and one servant");
            if (this.combat) {
                ProbeHostile enemy = this.f.entities.add(new ProbeHostile(h.getLevel()));
                this.f.add(enemy, TARGET);
                this.target = enemy;
                this.damage = enemy.damage;
                float range = this.host.getAttrs().getAttackRange();
                h.assertTrue(this.host.getMorale() > ID.Morale.L_Normal
                        && this.host.distanceToSqr(enemy) <= range * range
                        && this.host.getSensing().hasLineOfSight(enemy),
                        "Combat fixture must be in the host's actual recalculated range and sight");
                this.host.applyCommandState(new CommandIssuer.Player(this.f.owner.getUUID()),
                        new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(enemy))));
                h.assertTrue(enemy.getPlayerUID() == -100 && this.host.getCommandState().manualAttack().isPresent(),
                        "Natural combat must begin with a real manual command and wild enemy");
            } else {
                ProbePlayer player = this.owner ? this.f.owner : this.f.stranger;
                player.moveTo(h.absoluteVec(new Vec3(5.5D, 2D, 2.5D)));
                this.target = player;
                this.damage = player.damage;
                if (this.small) {
                    this.turret = this.f.turret(true, player);
                    this.turret.setHost(this.host);
                    this.f.add(this.turret, HOST);
                } else {
                    this.host.setMorale(2000);
                    this.host.setEmotesTick(0);
                    ItemStack pointer = new ItemStack(ModItems.POINTER.get());
                    PointerItem.setMode(pointer, 3);
                    player.setItemInHand(InteractionHand.MAIN_HAND, pointer);
                    this.host.interactAt(player, new Vec3(0D, this.host.getBbHeight() * 0.5D, 0D),
                            InteractionHand.MAIN_HAND);
                    this.host.setSensitiveBody(this.host.getBodyIDFromHeight(this.host.getHitHeight()));
                    h.assertTrue(this.host.getEmotesTick() == 0 && this.host.getHitHeight() == 50
                            && this.host.getSensitiveBody() == this.host.getBodyIDFromHeight(50)
                            && BuffHelper.getMoraleLevel(this.host.getMorale() + ConfigHandler.caressBaseMorale())
                            == ID.Morale.Tired && PointerItem.getMode(pointer) == 3,
                            "Caress cooldown, sensitive body, tired morale and pointer mode fixtures");
                    this.host.getRandom().setSeed(this.owner ? 279L : 245L);
                    h.assertTrue(this.host.mobInteract(player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
                            "Real pointer interaction must be consumed");
                    h.assertTrue(this.owner ? this.host.normalReactions == 1 : this.host.strangerReactions == 1,
                            "Deterministic single interaction did not dispatch expected reaction");
                    h.assertTrue(this.host.numRensouhou == 0 && this.host.getAmmoLight() == 0
                            && this.host.getAITarget() == null, "Caress must consume host ammo and clear temporary target");
                }
                player.moveTo(h.absoluteVec(TARGET));
            }
            this.initialHealth = this.target.getHealth();
            h.assertTrue(this.target.isAlive() && !this.target.isInvulnerable(), "Vulnerable living natural target");
            this.entered = true;
        }

        private void poll() {
            if (!this.entered) {
                if (this.host.tickCount < 24) return;
                enter();
            }
            GameTestHelper h = this.f.helper;
            if (this.turret == null) {
                List<EntityRensouhou> found = h.getLevel().getEntitiesOfClass(EntityRensouhou.class,
                        this.host.getBoundingBox().inflate(20D), r -> r.getHost() == this.host);
                if (found.isEmpty()) {
                    h.assertTrue(this.host.tickCount < 180, "No natural turret: hostTick=" + this.host.tickCount
                            + " target=" + this.host.getEntityTarget() + " manual=" + this.host.getManualTarget()
                            + " lock=" + this.host.currentTargetLock() + " ammo=" + this.host.getAmmoLight()
                            + " servants=" + this.host.numRensouhou + " hp=" + this.host.getHealth()
                            + " sitting=" + this.host.isOrderedToSit() + " fuel="
                            + this.host.getStateMinor(ID.M.NumGrudge) + " launches=" + this.host.fireControlLaunches);
                    return;
                }
                h.assertTrue(found.size() == 1, "Expected one naturally created turret");
                this.turret = this.f.entities.add(found.get(0));
                h.assertTrue(this.turret.getType() == ModEntities.RENSOUHOU.get(), "Natural summon kind changed");
                h.assertTrue(this.turret.getNumAmmoLight() == 6, "Natural summon must start with six shots");
                if (this.combat) {
                    h.assertTrue(this.host.fireControlLaunches == 1 && this.host.getEntityTarget() == this.target,
                            "Enemy summon must come through actual NEW fire control and target authority");
                }
            }
            if (this.initialDistance == 0D) {
                this.initialDistance = this.turret.distanceToSqr(this.target);
                h.assertTrue(this.initialDistance > 25D && this.turret.getEntityTarget() == this.target
                        && this.turret.getPlayerUID() == this.f.ownerUid,
                        "Independent summon target and out-of-range approach fixture");
                this.animation = new AnimationPackets(h.getLevel(), this.f.owner, this.turret);
                this.f.owner.connection = this.animation;
                trackAnimation(h, this.turret, this.animation);
            }
            int ammo = this.turret.getNumAmmoLight();
            if (ammo == this.previousAmmo) return;
            h.assertTrue(ammo == this.previousAmmo - 1, "Natural attack must consume exactly one shot");
            this.shots.add(h.getLevel().getGameTime());
            this.previousAmmo = ammo;
            h.assertTrue(this.animation.calls == this.shots.size(), "Each shot must send its existing attack notification");
            boolean allowed = this.combat || (!this.owner && ConfigHandler.friendlyFire());
            h.assertTrue(TeamHelper.doFriendlyFire(this.turret, this.target) == allowed, "Natural helper decision");
            h.assertTrue(this.turret.distanceToSqr(this.target) < this.initialDistance - 1D,
                    "Summon must approach independently before firing");
            h.assertTrue(this.damage.calls == (allowed ? this.shots.size() : 0) && this.damage.otherCalls == 0,
                    "Natural hurt count/source mismatch: shots=" + this.shots.size() + " calls=" + this.damage.calls);
            // Real friendly summons dispatch through the player returned by getHostEntity, not the ship's effect map.
            h.assertTrue(this.damage.effects == 0, "Natural owner-source effect behavior changed");
            if (allowed) {
                float raw = this.turret.getAttrs().getAttackDamage();
                float expected = CombatHelper.applyDamageReduceOnPlayer(this.target, raw);
                h.assertTrue(raw > 0F && Math.abs(this.damage.amount - expected) < 0.001F
                        && this.target.getHealth() < this.initialHealth, "Natural actual ATK/positive hurt/effect");
            } else {
                h.assertTrue(this.target.getHealth() == this.initialHealth, "Protected target health changed");
            }
            if (this.shots.size() > 1) {
                int i = this.shots.size() - 1;
                h.assertTrue(this.shots.get(i) - this.shots.get(i - 1) == 20L, "Existing 20-tick cadence changed");
            }
            if (ammo == 0) {
                h.assertTrue(this.shots.size() == 6 && this.turret.isRemoved() && this.target.isAlive(),
                        "Sixth shot must discard summon, not target");
                this.f.close();
                h.succeed();
            }
        }
    }

    /** Adds a controlled tracking connection; observes the real serialized packet, not a replacement attack method. */
    @SuppressWarnings("unchecked")
    private static void trackAnimation(GameTestHelper helper, EntityRensouhou turret, AnimationPackets connection) {
        try {
            Field entities = ChunkMap.class.getDeclaredField("entityMap");
            entities.setAccessible(true);
            Object tracked = ((Int2ObjectMap<?>) entities.get(helper.getLevel().getChunkSource().chunkMap)).get(turret.getId());
            helper.assertTrue(tracked != null, "Turret must have a vanilla entity tracker");
            Field seenBy = tracked.getClass().getDeclaredField("seenBy");
            seenBy.setAccessible(true);
            Set<ServerPlayerConnection> listeners = (Set<ServerPlayerConnection>) seenBy.get(tracked);
            listeners.add(connection);
            GameTestEntities.onCompletion(helper, () -> listeners.remove(connection), "attack packet observer");
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot attach attack packet observer: " + error);
        }
    }

    private static final class AnimationPackets extends ServerGamePacketListenerImpl {
        private final ClientboundCustomPayloadPacket expected;
        private int calls;

        private AnimationPackets(ServerLevel level, FakePlayer player, EntityRensouhou turret) {
            super(level.getServer(), new Connection(PacketFlow.CLIENTBOUND), player);
            this.expected = (ClientboundCustomPayloadPacket) ModNetworking.CHANNEL.toVanillaPacket(
                    new S2CAttackAnimationPacket(turret.getId(), S2CAttackAnimationPacket.STANDARD_DURATION_TICKS),
                    NetworkDirection.PLAY_TO_CLIENT);
        }

        @Override
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket payload
                    && payload.getIdentifier().equals(this.expected.getIdentifier())
                    && payload.getData().equals(this.expected.getData())) this.calls++;
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            send(packet);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final boolean oldFf = ConfigHandler.friendlyFire();
        private final ConfigHandler.ShipAiTargetAuthority oldAuthority = ConfigHandler.COMMON.shipAiTargetAuthority.get();
        private final ProbePlayer owner;
        private final ProbePlayer stranger;
        private final int ownerUid;
        private final int strangerUid;
        private final ProbeShip host;
        private final TeamData teamA;
        private final TeamData teamB;
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            GameTestEntities.onCompletion(helper, this::close, "rensouhou configuration and player cleanup");
            this.owner = player("turret_owner");
            this.stranger = player("turret_stranger");
            this.ownerUid = TeamHelper.getPlayerUID(this.owner);
            this.strangerUid = TeamHelper.getPlayerUID(this.stranger);
            helper.assertTrue(this.ownerUid > 0 && this.strangerUid > 0 && this.ownerUid != this.strangerUid,
                    "Distinct registered player UIDs");
            this.teamA = new TeamData(this.ownerUid, "turret_A", "A");
            this.teamB = new TeamData(this.strangerUid, "turret_B", "B");
            ServerDataManager.setTeamData(this.teamA);
            ServerDataManager.setTeamData(this.teamB);
            this.host = ship(this.owner, false);
            this.host.probeHookSource = true;
        }

        private ProbePlayer player(String name) {
            ProbePlayer player = new ProbePlayer(this.helper.getLevel(), name);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().invulnerable = false;
            player.setNoGravity(true);
            player.moveTo(this.helper.absoluteVec(TARGET));
            this.helper.getLevel().addNewPlayer(player);
            GameTestEntities.assertRegistered(this.helper, player);
            ServerDataManager.updatePlayerID(player);
            return player;
        }

        private ProbeShip ship(ProbePlayer player, boolean combat) {
            ProbeShip ship = this.entities.add(new ProbeShip(this.helper.getLevel()));
            ship.tame(player);
            ship.setOwnerUUID(player.getUUID());
            ship.setPlayerUID(TeamHelper.getPlayerUID(player));
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.AtkType_Light, true);
            ship.setStateFlag(ID.F.UseAmmoLight, true);
            ship.setStateFlag(ID.F.AtkType_Heavy, false);
            ship.setStateFlag(ID.F.UseAmmoHeavy, false);
            ship.setStateFlag(ID.F.UseMelee, false);
            ship.setStateFlag(ID.F.PassiveAI, false);
            ship.setStateFlag(ID.F.CanFollow, false);
            ship.setStateFlag(ID.F.PickItem, false);
            if (combat) ship.setMorale(6000);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setAmmoLight(1000);
            GameTestEntities.removeGoalsEachTick(this.helper, ship,
                    goal -> !combat || !(goal instanceof ShipFireControlGoal || goal instanceof ShipTargetAuthorityGoal));
            add(ship, HOST);
            this.helper.assertTrue(ship.getOwner() == player && ship.isAlive()
                    && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "Owned fueled ship");
            return ship;
        }

        private void add(Entity entity, Vec3 position) {
            entity.moveTo(this.helper.absoluteVec(position));
            entity.setNoGravity(true);
            if (entity instanceof net.minecraft.world.entity.Mob mob && !(entity instanceof ProbeShip)) {
                mob.setNoAi(!(entity instanceof EntityRensouhou));
            }
            if (entity instanceof ProbeHostile enemy) {
                enemy.setHealth(enemy.getMaxHealth());
                GameTestEntities.removeAllGoalsEachTick(this.helper, enemy);
            }
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(entity), "Failed to add fixture entity");
            GameTestEntities.assertRegistered(this.helper, entity);
        }

        private void ff(boolean enabled) {
            ConfigHandler.COMMON.friendlyFire.set(enabled);
            this.helper.assertTrue(ConfigHandler.friendlyFire() == enabled, "Friendly-fire config fixture");
        }

        // 0 = neutral, 1 = A allies B, 2 = B allies A, 3 = A bans B.
        private void relation(int relation) {
            this.teamA.getTeamAllyList().clear();
            this.teamB.getTeamAllyList().clear();
            this.teamA.getTeamBannedList().clear();
            if (relation == 1) this.teamA.addTeamAlly(this.strangerUid);
            if (relation == 2) this.teamB.addTeamAlly(this.ownerUid);
            if (relation == 3) this.teamA.addTeamBanned(this.strangerUid);
            this.helper.assertTrue(TeamHelper.checkIsAlly(this.ownerUid, this.strangerUid) == (relation == 1)
                    && TeamHelper.checkIsAlly(this.strangerUid, this.ownerUid) == (relation == 2)
                    && TeamHelper.checkIsBanned(this.ownerUid, this.strangerUid) == (relation == 3),
                    "Registered directional team relation fixture " + relation);
        }

        private EntityRensouhou turret(boolean small, Entity target) {
            EntityRensouhou turret = this.entities.add(small
                    ? ModEntities.RENSOUHOU_S.get().create(this.helper.getLevel())
                    : ModEntities.RENSOUHOU.get().create(this.helper.getLevel()));
            this.helper.assertTrue(turret != null, "Failed to create turret variant");
            turret.initAttrs(this.host, target, 0);
            this.helper.assertTrue(turret.getNumAmmoLight() == 6 && turret.getPlayerUID() == this.ownerUid,
                    "Turret host UID and initial ammo");
            return turret;
        }

        private void direct(boolean small, LivingEntity target, Capture capture, boolean allowed, float raw) {
            EntityRensouhou turret = turret(small, target);
            turret.getAttrs().setAttrsBuffed(ID.Attrs.ATK_L, raw);
            turret.setNumAmmoLight(1);
            this.helper.assertTrue(turret.getAttrs().getAttackDamage() == raw && target.isAlive()
                    && !target.isInvulnerable() && TeamHelper.doFriendlyFire(turret, target) == allowed,
                    "Direct ATK, survival and helper fixture");
            capture.reset();
            target.removeAllEffects();
            boolean result = turret.attackTarget(target);
            float expected = target instanceof ProbePlayer ? (raw == 100F ? 25F : 59F) : raw;
            this.helper.assertTrue(result == allowed && capture.calls == (allowed ? 1 : 0)
                    && capture.otherCalls == 0 && (!allowed || capture.amount == expected),
                    "Direct hurt contract: small=" + small + " allowed=" + allowed + " calls=" + capture.calls
                            + " amount=" + capture.amount + " expected=" + expected);
            this.helper.assertTrue(capture.effects == (allowed ? 1 : 0)
                    && target.hasEffect(MobEffects.WEAKNESS) == allowed, "Success hook must match allowed hurt");
            this.helper.assertTrue(turret.isRemoved() && turret.getNumAmmoLight() == 0,
                    "Allowed and rejected last shots must both consume and discard");
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            ConfigHandler.COMMON.friendlyFire.set(this.oldFf);
            ConfigHandler.COMMON.shipAiTargetAuthority.set(this.oldAuthority);
            for (ProbePlayer player : new ProbePlayer[]{this.owner, this.stranger}) {
                if (player == null) continue;
                int uid = TeamHelper.getPlayerUID(player);
                ServerDataManager.removeTeamData(uid);
                ServerDataManager.getAllPlayerWorldData().remove(uid);
                this.helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
            this.entities.close();
        }
    }

    /** Captures input, not vanilla mitigation; successful hits remain positive and nonlethal. */
    private static final class Capture {
        private int calls;
        private int otherCalls;
        private int effects;
        private float amount;

        private boolean hurt(LivingEntity target, DamageSource source, float amount) {
            if (source.getEntity() instanceof EntityRensouhou) {
                this.calls++;
                this.amount = amount;
            } else {
                this.otherCalls++;
            }
            if (amount > 0F) target.setHealth(Math.max(1F, target.getHealth() - 0.01F));
            return amount > 0F;
        }

        private void effect(MobEffectInstance effect) {
            if (effect.getEffect() == MobEffects.WEAKNESS) this.effects++;
        }

        private void reset() {
            this.calls = 0;
            this.otherCalls = 0;
            this.effects = 0;
            this.amount = Float.NaN;
        }
    }

    private static final class ProbeShip extends EntityDestroyerShimakaze {
        private final Capture damage = new Capture();
        private boolean realHurt;
        private boolean probeHookSource;
        private int normalReactions;
        private int strangerReactions;
        private int fireControlLaunches;

        private ProbeShip(ServerLevel level) {
            super(ModEntities.DESTROYER_SHIMAKAZE.get(), level);
        }

        private boolean newFireControlRegistered() {
            return this.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof ShipFireControlGoal);
        }

        @Override
        public Entity getHostEntity() {
            return this.probeHookSource ? this : super.getHostEntity();
        }

        @Override
        public HashMap<ResourceLocation, ShipAttackEffect> getAttackEffectMap() {
            HashMap<ResourceLocation, ShipAttackEffect> effects = new HashMap<>();
            effects.put(WEAKNESS, new ShipAttackEffect(WEAKNESS, 0, 200, 100));
            return effects;
        }

        @Override
        public boolean attackEntityWithAmmo(Entity target) {
            if (StackWalker.getInstance().walk(s -> s.anyMatch(frame -> frame.getClassName()
                    .equals(ShipFireControlGoal.class.getName())))) this.fireControlLaunches++;
            return super.attackEntityWithAmmo(target);
        }

        @Override
        public void reactionNormal() {
            this.normalReactions++;
            super.reactionNormal();
        }

        @Override
        public void reactionStranger() {
            this.strangerReactions++;
            super.reactionStranger();
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (this.realHurt) {
                this.damage.calls++;
                return super.hurt(source, amount);
            }
            return this.damage.hurt(this, source, amount);
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            this.damage.effect(effect);
            return super.addEffect(effect, source);
        }
    }

    private static final class ProbePlayer extends FakePlayer {
        private final Capture damage = new Capture();

        private ProbePlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return this.damage.hurt(this, source, amount);
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            this.damage.effect(effect);
            return super.addEffect(effect, source);
        }
    }

    private static final class ProbeCow extends Cow {
        private final Capture damage = new Capture();

        private ProbeCow(ServerLevel level) {
            super(EntityType.COW, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return this.damage.hurt(this, source, amount);
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            this.damage.effect(effect);
            return super.addEffect(effect, source);
        }
    }

    private static final class ProbeWolf extends Wolf {
        private final Capture damage = new Capture();

        private ProbeWolf(ServerLevel level) {
            super(EntityType.WOLF, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return this.damage.hurt(this, source, amount);
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            this.damage.effect(effect);
            return super.addEffect(effect, source);
        }
    }

    private static final class ProbeHostile extends EntityDestroyerShimakazeMob {
        private final Capture damage = new Capture();

        private ProbeHostile(ServerLevel level) {
            super(ModEntities.DESTROYER_SHIMAKAZE_MOB.get(), level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return this.damage.hurt(this, source, amount);
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            this.damage.effect(effect);
            return super.addEffect(effect, source);
        }
    }
}
