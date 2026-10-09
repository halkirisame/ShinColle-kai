package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipCombatGate;
import com.lulan.shincolle.ai.ShipCombatHost;
import com.lulan.shincolle.ai.ShipFireControlGoal;
import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.ShipMovementHost;
import com.lulan.shincolle.ai.ShipSkillAttackGate;
import com.lulan.shincolle.ai.ShipSpecialAttackGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.TeleportDenial;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Runs each skill through the real damage, authority, movement, and projectile adapters. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipSpecialAttackGameTests {
    private static final Vec3 HOME = new Vec3(2.5D, 3D, 2.5D);
    private static final Vec3 TARGET = new Vec3(6.5D, 3D, 2.5D);

    private ShipSpecialAttackGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_tenryuu")
    public static void friendlyTenryuuCompletesContinuousSlashes(GameTestHelper helper) {
        continuous(helper, ModEntities.CL_TENRYUU.get(), 2, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_tenryuu_mob")
    public static void hostileTenryuuCompletesContinuousSlashes(GameTestHelper helper) {
        continuous(helper, ModEntities.CL_TENRYUU_MOB.get(), 1, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_tatsuta")
    public static void friendlyTatsutaLaunchesOneGaeBolg(GameTestHelper helper) {
        continuous(helper, ModEntities.CL_TATSUTA.get(), 0, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_tatsuta_mob")
    public static void hostileTatsutaLaunchesOneGaeBolg(GameTestHelper helper) {
        continuous(helper, ModEntities.CL_TATSUTA_MOB.get(), 0, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_nagato")
    public static void friendlyNagatoFinishesOnFourthHeavyAttack(GameTestHelper helper) {
        punch(helper, ModEntities.BB_NAGATO.get());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_nagato_mob")
    public static void hostileNagatoFinishesOnFourthHeavyAttack(GameTestHelper helper) {
        punch(helper, ModEntities.BB_NAGATO_MOB.get());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_interrupt")
    public static void sittingAndLoadingClearSkillAndMotion(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TENRYUU.get(), (host, target) -> {
            var ship = (BasicEntityShip) host;
            start(helper, host, target);
            host.tickCount++;
            ShipSkillAttackGate.tick(host);
            host.setDeltaMovement(1D, 1D, 1D);
            ship.setEntitySit(true);
            ShipSkillAttackGate.observe(host);
            helper.assertTrue(!ShipSkillAttackGate.running(host) && host.getDeltaMovement().equals(Vec3.ZERO),
                    "Sitting must cancel skill motion");
            helper.assertTrue(ship.shipCombatState().skill().sequence().isEmpty(), "Interrupted state must be empty");
            ship.setEntitySit(false);
            host.moveTo(helper.absoluteVec(HOME));
            host.tickCount++;
            ship.setEntityRevengeTarget(target);
            ship.setEntityRevengeTime();
            authority(host).tick();
            start(helper, host, target);
            var saved = new net.minecraft.nbt.CompoundTag();
            ship.addAdditionalSaveData(saved);
            ship.setDeltaMovement(1D, 1D, 1D);
            ship.readAdditionalSaveData(saved);
            helper.assertTrue(ship.shipCombatState().skill().sequence().isEmpty()
                    && ship.getStateEmotion(ID.S.Phase) == 0 && host.getDeltaMovement().equals(Vec3.ZERO),
                    "Loading must not restore an in-flight skill or its velocity");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_scope")
    public static void arbitraryCallerCannotBypassTeleportCooldown(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TENRYUU.get(), (host, target) -> {
            Vec3 before = host.position();
            Vec3 to = before.add(2D, 0D, 0D);
            boolean moved = ShipMovementExecutor.tryTeleport(host, new MovementStep.Teleport(MovementBody.SELF,
                    new MovementPoint(to.x, to.y, to.z), MovementReason.SKILL_ATTACK,
                    ShipCommandStateAdapter.handle(host).dimension()));
            helper.assertTrue(!moved && host.position().equals(before), "Reason alone must not authorize skill teleport");
            helper.assertTrue(((ShipMovementHost) host).shipMovementExecutor().lastDenials()
                    .contains(TeleportDenial.SKILL_NOT_AUTHORIZED), "Must explain unauthorized skill teleport");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_ally")
    public static void skillDamageRejectsSameOwnerEvenAsPrimaryTarget(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TATSUTA.get(), (host, target) -> {
            UUID owner = UUID.randomUUID();
            BasicEntityShip ally = ModEntities.BB_KONGOU.get().create(helper.getLevel());
            ((BasicEntityShip) host).setOwnerUUID(owner);
            ((BasicEntityShip) host).setPlayerUID(1500101);
            ally.setOwnerUUID(owner);
            ally.setPlayerUID(1500101);
            helper.assertTrue(!ShipSkillAttackGate.areaTarget(host, ally, ally), "Explicit primary may not hurt an ally");
            ally.discard();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_beam")
    public static void gaeBolgSpeedAndLifetimeLeaveOrdinaryBeamUnchanged(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TATSUTA.get(), (host, target) -> {
            IShipCannonAttack ship = (IShipCannonAttack) host;
            EntityProjectileBeam gae = ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel());
            EntityProjectileBeam ordinary = ModEntities.PROJECTILE_BEAM.get().create(helper.getLevel());
            gae.initGaeBolg(ship, new Vec3(1D, 0D, 0D), 1F);
            ordinary.initBeam(ship, 1D, 0D, 0D, 1F);
            Vec3 gaeStart = gae.position();
            Vec3 ordinaryStart = ordinary.position();
            beamTick(gae);
            beamTick(ordinary);
            helper.assertTrue(gae.position().distanceTo(gaeStart) == 3D && gae.getBeamType() == 1, "Spear speed/type");
            helper.assertTrue(ordinary.position().distanceTo(ordinaryStart) == 4D, "Ordinary beam speed");
            for (int tick = 1; tick < 8; tick++) beamTick(gae);
            helper.assertTrue(!gae.isRemoved(), "Gae Bolg must survive eight damage ticks");
            beamTick(gae);
            helper.assertTrue(gae.isRemoved(), "Gae Bolg must expire after eight ticks");
            for (int tick = 1; tick < 31; tick++) beamTick(ordinary);
            helper.assertTrue(!ordinary.isRemoved(), "Ordinary beam must retain 31-tick life");
            beamTick(ordinary);
            helper.assertTrue(ordinary.isRemoved(), "Ordinary beam must expire after 31 ticks");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_legacy")
    public static void legacyDoesNotAcquireNewSkillStateOrMultipliers(GameTestHelper helper) {
        try (var mode = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
            for (EntityType<? extends Mob> type : java.util.List.<EntityType<? extends Mob>>of(ModEntities.CL_TENRYUU.get(),
                    ModEntities.CL_TENRYUU_MOB.get(), ModEntities.CL_TATSUTA.get(), ModEntities.CL_TATSUTA_MOB.get(),
                    ModEntities.BB_NAGATO.get(), ModEntities.BB_NAGATO_MOB.get())) {
                Mob host = type.create(helper.getLevel());
                helper.assertTrue(!ShipSkillAttackGate.supported(host)
                        && ShipSkillAttackGate.baseDamage(host, 0).isEmpty(), "LEGACY must retain existing paths");
                host.discard();
            }
        }
        helper.succeed();
    }

    private static void continuous(GameTestHelper helper, EntityType<? extends Mob> type, int slashes, boolean beam) {
        fixture(helper, type, (host, target) -> {
            IShipCannonAttack ship = (IShipCannonAttack) host;
            float initialRange = ship.getAttrs().getAttackRange();
            helper.assertTrue(host instanceof BasicEntityShipHostile || initialRange < 8F,
                    "Friendly completion fixture must use initial attack range");
            start(helper, host, target);
            int slashCount = 0;
            ShipSpecialAttackGoal goal = new ShipSpecialAttackGoal(host);
            ShipFireControlGoal fire = new ShipFireControlGoal(ship);
            helper.assertTrue(goal.canUse(), "Continuous skill goal must be reachable");
            goal.start();
            int previous = ship.getStateEmotion(ID.S.Phase);
            java.util.List<String> transitions = new java.util.ArrayList<>();
            for (int tick = 0; tick < 160 && ShipSkillAttackGate.running(host); tick++) {
                host.tickCount++;
                authority(host).tick();
                helper.assertTrue(ShipCombatGate.engagement(host).target() == target, "Skill must retain its current lock");
                var state = ((ShipCombatHost) host).shipCombatState();
                int heavyReady = state.timing(host.tickCount).readyAt(com.lulan.shincolle.ai.domain.combat.WeaponChannel.HEAVY);
                fire.tick();
                helper.assertTrue(state.timing(host.tickCount).readyAt(com.lulan.shincolle.ai.domain.combat.WeaponChannel.HEAVY)
                        == heavyReady + 1, "Ordinary heavy clock must pause during skill");
                goal.tick();
                int phase = ship.getStateEmotion(ID.S.Phase);
                if (phase != previous) transitions.add(tick + ":" + phase + ":" + host.position()
                        + ":range=" + ship.getAttrs().getAttackRange() + ":denials="
                        + ((ShipMovementHost) host).shipMovementExecutor().lastDenials());
                if (!beam && phase == 2 && previous != 2) slashCount++;
                previous = phase;
            }
            helper.assertTrue(!ShipSkillAttackGate.running(host) && ship.getStateEmotion(ID.S.Phase) == 0,
                    "Sequence must complete");
            helper.assertTrue(host.getDeltaMovement().equals(Vec3.ZERO), "Finished sequence must release velocity");
            helper.assertTrue(ship.getAttrs().getAttackRange() == initialRange, "Skill must not mutate ordinary attack range");
            target.moveTo(host.position().add(Math.ceil(initialRange) + 4D, 0D, 0D));
            authority(host).tick();
            helper.assertTrue(!ShipCombatGate.engagement(host).engaged(), "Finished skill must restore ordinary lock range");
            helper.assertTrue(slashCount == slashes, "Unexpected slash count: " + slashCount + " " + transitions);
            var beams = helper.getLevel().getEntities(host, host.getBoundingBox().inflate(40D), e -> e instanceof EntityProjectileBeam);
            helper.assertTrue(beams.size() == (beam ? 1 : 0), "Finisher beam count: " + beams.size());
            beams.forEach(net.minecraft.world.entity.Entity::discard);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_range")
    public static void tenryuuRetainsSlashLockWithinTenAndCancelsBeyond(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TENRYUU.get(), (host, target) -> {
            IShipCannonAttack ship = (IShipCannonAttack) host;
            ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 4F);
            start(helper, host, target);
            host.tickCount++;
            ShipSkillAttackGate.tick(host);
            helper.assertTrue(host.distanceToSqr(target) > 16D, "Slash fixture must put target outside ordinary range");
            host.tickCount++;
            authority(host).tick();
            ShipSkillAttackGate.tick(host);
            helper.assertTrue(ShipSkillAttackGate.running(host) && ShipCombatGate.engagement(host).target() == target,
                    "Slash must retain its current target within ten blocks");
            target.moveTo(host.position().add(10.01D, 0D, 0D));
            host.tickCount++;
            authority(host).tick();
            ShipSkillAttackGate.tick(host);
            helper.assertTrue(!ShipSkillAttackGate.running(host) && ship.getStateEmotion(ID.S.Phase) == 0
                    && host.getDeltaMovement().equals(Vec3.ZERO), "Distant lock must cancel and release motion");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_tatsuta_range")
    public static void friendlyTatsutaRetainsWithinEightAndCancelsBeyond(GameTestHelper helper) {
        retentionBoundary(helper, ModEntities.CL_TATSUTA.get());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_tatsuta_mob_range")
    public static void hostileTatsutaRetainsWithinEightAndCancelsBeyond(GameTestHelper helper) {
        retentionBoundary(helper, ModEntities.CL_TATSUTA_MOB.get());
    }

    private static void retentionBoundary(GameTestHelper helper, EntityType<? extends Mob> type) {
        fixture(helper, type, true, (host, target) -> {
            helper.assertTrue(((IShipCannonAttack) host).getAttrs().getAttackRange() == 4F,
                    "Boundary fixture must have ordinary range four before authority construction");
            start(helper, host, target);
            target.moveTo(host.position().add(8D, 0D, 0D));
            host.tickCount++;
            authority(host).tick();
            ShipSkillAttackGate.tick(host);
            helper.assertTrue(ShipSkillAttackGate.running(host) && ShipCombatGate.engagement(host).target() == target,
                    "Tatsuta must retain the current lock at eight blocks");
            target.moveTo(host.position().add(8.01D, 0D, 0D));
            host.tickCount++;
            authority(host).tick();
            ShipSkillAttackGate.tick(host);
            helper.assertTrue(!ShipSkillAttackGate.running(host) && !ShipCombatGate.engagement(host).engaged()
                    && host.getDeltaMovement().equals(Vec3.ZERO), "Beyond eight blocks must cancel the skill and lock");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_skill_manual_range")
    public static void skillRetainsManualLockButCannotAcquireNewDistantManualTarget(GameTestHelper helper) {
        fixture(helper, ModEntities.CL_TENRYUU.get(), (host, target) -> {
            BasicEntityShip ship = (BasicEntityShip) host;
            ship.setManualTarget(target);
            authority(host).tick();
            helper.assertTrue(ship.currentTargetLock().orElseThrow().source()
                    == com.lulan.shincolle.ai.domain.TargetSource.MANUAL, "Fixture must hold a manual lock");
            start(helper, host, target);
            target.moveTo(host.position().add(10D, 0D, 0D));
            authority(host).tick();
            helper.assertTrue(ShipCombatGate.engagement(host).target() == target, "Held manual lock must survive at ten");
            authority(host).clearLock();
            authority(host).tick();
            helper.assertTrue(ship.currentTargetLock().isEmpty(), "New distant manual lock must remain rejected");
            host.tickCount++;
            ShipSkillAttackGate.tick(host);
            helper.assertTrue(!ShipSkillAttackGate.running(host), "Skill without its lock must stop");
        });
    }

    private static void punch(GameTestHelper helper, EntityType<? extends Mob> type) {
        fixture(helper, type, (host, target) -> {
            IShipCannonAttack ship = (IShipCannonAttack) host;
            Vec3 before = host.position();
            for (int phase = 1; phase <= 3; phase++) {
                helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Charge must succeed");
                helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == phase && host.position().equals(before),
                        "Only the fourth heavy attack moves Nagato");
            }
            helper.assertTrue(ship.attackEntityWithHeavyAmmo(target), "Punch must succeed");
            helper.assertTrue(ship.getStateEmotion(ID.S.Phase) == 0 && host.distanceToSqr(target) <= 9D,
                    "Fourth attack must finish and safely move beside target");
        });
    }

    private static void start(GameTestHelper helper, Mob host, Mob target) {
        IShipCannonAttack ship = (IShipCannonAttack) host;
        helper.assertTrue(ship.attackEntityWithHeavyAmmo(target) && ship.getStateEmotion(ID.S.Phase) == -1,
                "First heavy must prepare skill");
        helper.assertTrue(ship.attackEntityWithHeavyAmmo(target) && ShipSkillAttackGate.running(host),
                "Second heavy must start sequence");
    }

    private static void fixture(GameTestHelper helper, EntityType<? extends Mob> type, BiConsumer<Mob, Mob> verify) {
        fixture(helper, type, false, verify);
    }

    private static void fixture(GameTestHelper helper, EntityType<? extends Mob> type,
                                boolean shortRange, BiConsumer<Mob, Mob> verify) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (var mode = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 var entities = GameTestEntities.open(helper)) {
                Mob host = entities.add(type.create(helper.getLevel()));
                host.tickCount = 1;
                IShipCannonAttack ship = (IShipCannonAttack) host;
                if (host instanceof BasicEntityShip friendly) {
                    friendly.setStateMinor(ID.M.NumGrudge, 100_000);
                    friendly.setAmmoHeavy(1_000);
                    friendly.calcShipAttributes(31, false);
                    friendly.setEntitySit(false);
                    if (shortRange) ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 4F);
                    friendly.setAITargetList();
                } else {
                    BasicEntityShipHostile hostile = (BasicEntityShipHostile) host;
                    hostile.calcShipAttributes(31, false);
                    if (shortRange) ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 4F);
                    hostile.setAITargetList();
                }
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setStateFlag(ID.F.PassiveAI, false);
                host.setHealth(host.getMaxHealth());
                host.setInvulnerable(true);
                host.moveTo(helper.absoluteVec(HOME));
                helper.assertTrue(helper.getLevel().addFreshEntity(host), "Host registration");
                Mob target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                target.setNoAi(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5_000D);
                target.setHealth(5_000F);
                target.moveTo(helper.absoluteVec(TARGET));
                helper.assertTrue(helper.getLevel().addFreshEntity(target), "Target registration");
                ship.setEntityRevengeTarget(target);
                ship.setEntityRevengeTime();
                authority(host).tick();
                helper.assertTrue(ShipCombatGate.engagement(host).target() == target, "Fixture must hold the real target lock");
                helper.assertTrue(!host.isNoAi() && !ship.getIsSitting() && !ship.getStateFlag(ID.F.NoFuel),
                        "Fixture must permit firing");
                verify.accept(host, target);
                helper.assertTrue(helper.getLevel().getEntities(host, host.getBoundingBox().inflate(40D),
                        e -> e instanceof EntityAbyssMissile).isEmpty(), "Skill heavy must not also launch an ordinary missile");
            }
            helper.succeed();
        }, HOME, TARGET);
    }

    private static ShipTargetAuthorityGoal authority(Mob host) {
        try {
            Field field = (host instanceof BasicEntityShip ? BasicEntityShip.class : BasicEntityShipHostile.class)
                    .getDeclaredField("targetAuthorityGoal");
            field.setAccessible(true);
            return (ShipTargetAuthorityGoal) field.get(host);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void beamTick(EntityProjectileBeam beam) {
        // The server's entity ticker increments this before invoking Entity.tick().
        beam.tickCount++;
        beam.tick();
    }
}
