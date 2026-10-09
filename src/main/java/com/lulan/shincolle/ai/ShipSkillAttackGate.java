package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.combat.skill.SkillDecision;
import com.lulan.shincolle.ai.domain.combat.skill.SkillKind;
import com.lulan.shincolle.ai.domain.combat.skill.SkillPhase;
import com.lulan.shincolle.ai.domain.combat.skill.SkillProfile;
import com.lulan.shincolle.ai.domain.combat.skill.SkillState;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.battleship.EntityBattleshipNagato;
import com.lulan.shincolle.entity.battleship.EntityBattleshipNagatoMob;
import com.lulan.shincolle.entity.cruiser.EntityCLTatsuta;
import com.lulan.shincolle.entity.cruiser.EntityCLTatsutaMob;
import com.lulan.shincolle.entity.cruiser.EntityCLTenryuu;
import com.lulan.shincolle.entity.cruiser.EntityCLTenryuuMob;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.equip.ShipOnHitEffects;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CAttackAnimationPacket;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.network.SkillVisualEffect;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalInt;

/** Collects skill facts, projects model poses, and executes pure sequence plans under NEW. */
public final class ShipSkillAttackGate {
    /** A dedicated particle dispatch, separate from ordinary and legacy particle IDs. */
    public static final byte VISUAL_PACKET_TYPE = 60;

    private ShipSkillAttackGate() { }

    public static Optional<SkillProfile> profile(Entity host) {
        if (!ShipCommandStateAdapter.isNew() || !(host instanceof IShipAttackBase ship)) return Optional.empty();
        SkillKind kind;
        if (host instanceof EntityCLTenryuu || host instanceof EntityCLTenryuuMob) kind = SkillKind.TENRYUU;
        else if (host instanceof EntityCLTatsuta || host instanceof EntityCLTatsutaMob) kind = SkillKind.TATSUTA;
        else if (host instanceof EntityBattleshipNagato || host instanceof EntityBattleshipNagatoMob) kind = SkillKind.NAGATO;
        else return Optional.empty();
        return Optional.of(new SkillProfile(kind, host instanceof BasicEntityShipHostile,
                Math.max(0, ship.getLevel()), Math.max(0, ship.getScaleLevel())));
    }

    public static boolean supported(Entity host) {
        return profile(host).isPresent();
    }

    /** Resets only the transient skill projection after persistent data is read. */
    public static void loaded(Mob host) {
        if (!supported(host)) return;
        execution(host).clear();
        if (!host.level().isClientSide()) {
            ShipMovementExecutor.run(host, MovementPlan.of(new MovementStep.SkillMotion(
                    MovementBody.SELF, new MovementPoint(0D, 0D, 0D), MovementReason.SKILL_ATTACK)));
        }
        ((IShipAttackBase) host).setStateEmotion(ID.S.Phase, 0, false);
    }

    public static Optional<Float> baseDamage(Mob host, int attackType) {
        return profile(host).flatMap(p -> {
            IShipAttackBase ship = (IShipAttackBase) host;
            if (attackType == 0) return Optional.of(ship.getAttrs().getAttackDamage() * p.meleeMultiplier());
            if (p.kind() != SkillKind.NAGATO) {
                if (attackType == 2) return Optional.of(ship.getAttrs().getAttackDamageHeavy() * p.horizontalMultiplier());
                if (attackType == 3) return Optional.of(ship.getAttrs().getAttackDamageHeavy() * p.finalMultiplier());
            }
            return Optional.empty();
        });
    }

    public static boolean ordinaryVisual(Mob host, int attackType) {
        Optional<SkillProfile> profile = profile(host);
        if (profile.isEmpty() || host.level().isClientSide() || attackType > 1) return false;
        SkillProfile p = profile.get();
        if (p.kind() == SkillKind.NAGATO) {
            if (attackType == 1) {
                double scale = p.hostile() ? p.scale() + 1D : 1D;
                visual(host, SkillVisualEffect.NAGATO_SMOKE, host.position(), new Vec3(0.9D * scale, scale, 1.1D * scale));
            }
        } else {
            visual(host, p.kind() == SkillKind.TENRYUU ? SkillVisualEffect.TENRYUU_SWEEP : SkillVisualEffect.TATSUTA_SWEEP,
                    host.position(), Vec3.ZERO);
        }
        animation(host, 50);
        return true;
    }

    public static boolean ordinarySound(Mob host, int attackType) {
        Optional<SkillProfile> p = profile(host);
        if (p.isEmpty() || attackType != 1 || p.get().kind() == SkillKind.NAGATO) return false;
        sound(host, SoundEvents.PLAYER_ATTACK_SWEEP, pitch(host) * 0.85F, 1.2F);
        voice(host);
        return true;
    }

    /** Local pose effects have no server authority and require no packet every tick. */
    public static void clientTick(Mob host) {
        Optional<SkillProfile> p = profile(host);
        if (p.isEmpty() || !host.level().isClientSide()) return;
        int phase = ((IShipAttackBase) host).getStateEmotion(ID.S.Phase);
        if (p.get().kind() == SkillKind.TENRYUU && phase == 3) {
            float h = host.getBbHeight();
            com.lulan.shincolle.utility.ParticleHelper.spawnGradientParticle(host, 2,
                    h * 1.5F, 0.7F, 0.4F, 0F, 1F, 0.7F, 1F, 0.8F, 40F, 1.6F);
            com.lulan.shincolle.utility.ParticleHelper.spawnGradientParticle(host, 2,
                    h * 1.3F, 0.7F, 0.3F, 0F, 1F, 1F, 1F, 1F, 40F, 1.5F);
        } else if (p.get().kind() == SkillKind.NAGATO && (phase == 1 || phase == 3)
                && (host.tickCount & 7) == 0) {
            float size = 0.12F + (p.get().hostile() ? p.get().scale() * 0.1F : 0F);
            com.lulan.shincolle.utility.ParticleHelper.spawnChiParticle(host, size, 0);
        }
    }

    public static void ordinaryHit(Mob host, Entity target, int attackType) {
        if (supported(host)) visual(host, attackType == 0 ? SkillVisualEffect.MELEE_HIT : SkillVisualEffect.LIGHT_HIT,
                target.position(), Vec3.ZERO);
    }

    private static ShipSkillAttackState execution(Entity host) {
        return ShipCombatGate.state(host).skill();
    }

    public static boolean running(Entity host) {
        return supported(host) && execution(host).sequence().filter(SkillState::running).isPresent();
    }

    /** Supplies the pure range decision to authority observations without changing target scans. */
    public static double targetRetentionRange(Entity host, double ordinaryRange, boolean held) {
        if (!supported(host)) return ordinaryRange;
        return execution(host).sequence()
                .map(state -> SkillDecision.targetRetentionRange(state, ordinaryRange, held))
                .orElse(ordinaryRange);
    }

    private static boolean permitted(Mob host) {
        IShipAttackBase ship = (IShipAttackBase) host;
        return !BasicEntityShip.stopAI && host.isAlive() && !host.isRemoved() && !host.isNoAi()
                && !ShipActionGate.blocked(host, ActionKind.FIRING) && !ship.getIsSitting()
                && !ShipMovementGate.craneBusy(ship) && !ship.getIsRiding();
    }

    /** Removes a sequence whose command, life, dimension, or authority no longer allows it. */
    public static void observe(Mob host) {
        ShipCombatState combat = ShipCombatGate.state(host);
        if (combat == null || combat.skill().sequence().isEmpty()) return;
        ShipSkillAttackState skill = combat.skill();
        if (!supported(host) || !permitted(host)
                || skill.dimension.filter(host.level().dimension()::equals).isEmpty()) cancel(host);
    }

    public static void cancel(Mob host) {
        ShipCombatState combat = ShipCombatGate.state(host);
        if (combat == null) return;
        boolean hadState = combat.skill().sequence().isPresent();
        boolean moving = combat.skill().sequence().filter(SkillState::running).isPresent();
        combat.skill().clear();
        if (hadState && !host.level().isClientSide()) ((IShipAttackBase) host).setStateEmotion(ID.S.Phase, 0, true);
        if (moving && !host.level().isClientSide()) {
            ShipMovementExecutor.run(host, MovementPlan.of(new MovementStep.SkillMotion(
                    MovementBody.SELF, new MovementPoint(0D, 0D, 0D), MovementReason.SKILL_ATTACK)));
        }
    }

    /** Called after the normal heavy-ammunition accounting, instead of summoning a missile. */
    public static boolean heavy(Mob host, Entity target) {
        if (running(host)) return false;
        Optional<SkillProfile> profile = profile(host);
        if (profile.isEmpty() || host.level().isClientSide() || !permitted(host)
                || !validTarget(host, target)) return false;
        ShipSkillAttackState runtime = execution(host);
        runtime.dimension = Optional.of(host.level().dimension());
        SkillDecision.Plan plan = SkillDecision.heavy(runtime.sequence(profile.get().kind()), profile.get());
        runtime.setSequence(plan.state());
        for (SkillDecision.Effect effect : plan.effects()) effect(host, target, profile.get(), runtime, effect);
        project(host, runtime);
        return true;
    }

    public static void tick(Mob host) {
        observe(host);
        if (!running(host) || host.level().isClientSide()) return;
        ShipSkillAttackState runtime = execution(host);
        if (runtime.lastTick.isPresent() && runtime.lastTick.getAsInt() == host.tickCount) return;
        runtime.lastTick = OptionalInt.of(host.tickCount);
        SkillProfile profile = profile(host).orElseThrow();
        ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(host);
        Entity target = engagement.target();
        SkillState before = runtime.sequence(profile.kind());
        SkillDecision.Plan plan = SkillDecision.tick(before, profile, permitted(host),
                engagement.engaged() && validTarget(host, target));
        if (plan.state().phase() == SkillPhase.IDLE && !plan.motion()) {
            cancel(host);
            return;
        }
        runtime.setSequence(plan.state());
        for (SkillDecision.Effect effect : plan.effects()) {
            if (!effect(host, target, profile, runtime, effect)) {
                cancel(host);
                return;
            }
        }
        if (plan.motion()) motion(host, runtime.motion);
        if (plan.damage()) damageNearby(host, target, profile, runtime,
                profile.kind() == SkillKind.TENRYUU && before.phase() == SkillPhase.FINAL);
        project(host, runtime);
        if (plan.state().phase() == SkillPhase.IDLE) {
            motion(host, Vec3.ZERO);
            cancel(host);
        }
    }

    private static boolean validTarget(Mob host, Entity target) {
        if (target == null || target == host || !target.isAlive() || target.level() != host.level()
                || TargetHelper.isEntityInvulnerable(target) || TeamHelper.checkSameOwner(host, target)
                || TargetHelper.checkIsAlly(host, target)) return false;
        double range = targetRetentionRange(host, ((IShipAttackBase) host).getAttrs().getAttackRange(),
                ShipCombatGate.engagement(host).target() == target);
        return host.distanceToSqr(target) <= range * range;
    }

    /** The locked primary target remains eligible for an explicit manual command. */
    public static boolean areaTarget(Mob host, Entity target, Entity primary) {
        if (!(target instanceof LivingEntity) || target == host || !target.isAlive() || !target.isPickable()
                || TargetHelper.isEntityInvulnerable(target) || TeamHelper.checkSameOwner(host, target)
                || TargetHelper.checkIsAlly(host, target) || CombatHelper.isFriendlyFire(host, target)) return false;
        if (target == primary) return true;
        return host instanceof BasicEntityShipHostile ? new TargetHelper.SelectorForHostile(host).test(target)
                : new TargetHelper.Selector(host).test(target);
    }

    private static boolean effect(Mob host, Entity target, SkillProfile profile, ShipSkillAttackState runtime,
                                   SkillDecision.Effect effect) {
        switch (effect) {
            case CHARGE_VISUAL -> {
                sound(host, ModSounds.SHIP_AP_P1.get(), 1F);
                visual(host, profile.kind() == SkillKind.TENRYUU ? SkillVisualEffect.TENRYUU_CHARGE
                        : SkillVisualEffect.TATSUTA_CHARGE, host.position(), Vec3.ZERO);
                animation(host, 100);
                voice(host);
            }
            case START_SLASH, START_DROP -> {
                boolean drop = effect == SkillDecision.Effect.START_DROP;
                Vec3 destination;
                if (drop) {
                    destination = new Vec3(Math.floor(target.getX()) + 0.5D,
                            Math.floor(target.getY()) + (int) target.getBbHeight() + 6D,
                            Math.floor(target.getZ()) + 0.5D);
                    if (!teleport(host, destination)) return false;
                } else {
                    boolean moved = false;
                    for (int attempt = 0; attempt < 20 && !moved; attempt++) {
                        double angle = host.getRandom().nextDouble() * Math.PI * 2D;
                        destination = new Vec3(Math.floor(target.getX()) + (int) (Math.cos(angle) * 6D) + 0.5D,
                                Math.floor(target.getY()), Math.floor(target.getZ()) + (int) (Math.sin(angle) * 6D) + 0.5D);
                        moved = teleport(host, destination);
                    }
                    if (!moved) return false;
                }
                runtime.motion = drop ? new Vec3(0D, -Math.abs(host.getY() - target.getY()) * 0.25D, 0D)
                        : target.position().subtract(host.position()).normalize().scale(1.25D);
                if (!drop) face(host, runtime.motion);
            }
            case CAPTURE_CHARGE -> {
                runtime.motion = target.position().subtract(host.position()).scale(0.14D);
                face(host, runtime.motion);
                animation(host, 50);
            }
            case START_SPIN -> {
                runtime.motion = new Vec3(0D, profile.hostile() ? 0.3D + profile.scale() * 0.1D : 0.3D, 0D);
                animation(host, 50);
            }
            case START_FINAL -> {
                Vec3 direction = target.position().subtract(host.position()).add(0D, -1D, 0D).normalize();
                runtime.motion = direction;
                face(host, direction);
                animation(host, 50);
            }
            case CLEAR_HITS -> runtime.hitEntities.clear();
            case SLASH_SOUND -> {
                sound(host, SoundEvents.ENDER_DRAGON_GROWL, pitch(host));
                sound(host, ModSounds.SHIP_JET.get(), pitch(host));
                animation(host, 50);
            }
            case SLASH_TRAIL -> visual(host, SkillVisualEffect.TENRYUU_TRAIL,
                    host.position().add(runtime.motion.x * 2D,
                            host.getBbHeight() * 0.4D + runtime.motion.y * 2.5D, runtime.motion.z * 2D), runtime.motion);
            case FINAL_SOUND -> sound(host, ModSounds.SHIP_AP_ATTACK.get(), pitch(host) * 0.6F, 1.1F);
            case CHARGE_TRAIL -> visual(host, SkillVisualEffect.TATSUTA_TRAIL,
                    host.position().add(runtime.motion.scale(2D)).add(0D, host.getBbHeight() * 0.7D, 0D),
                    runtime.motion.scale(profile.hostile() ? 1.5D + profile.scale() * 0.8D : 1.5D));
            case SPIN_RING -> visual(host, SkillVisualEffect.SPIN_RING, host.position(), Vec3.ZERO);
            case SPIN_SOUND -> {
                sound(host, SoundEvents.PLAYER_ATTACK_SWEEP, pitch(host) * 1.1F);
                sound(host, ModSounds.SHIP_JET.get(), pitch(host));
            }
            case FIRE_GAE_BOLG -> {
                EntityProjectileBeam beam = new EntityProjectileBeam(ModEntities.PROJECTILE_BEAM.get(), host.level());
                beam.initGaeBolg((IShipAttackBase) host, runtime.motion,
                        ((IShipAttackBase) host).getAttrs().getAttackDamageHeavy() * profile.finalMultiplier());
                host.level().addFreshEntity(beam);
            }
            case GAE_BOLG_TRAIL -> {
                visual(host, SkillVisualEffect.GAE_BOLG_TRAIL,
                        host.position().add(runtime.motion.scale(10D)).add(0D, host.getBbHeight() * 0.7D, 0D),
                        runtime.motion.scale(1.5D));
                animation(host, 50);
            }
            case PUNCH_INWARD -> {
                sound(host, ModSounds.SHIP_AP_P1.get(), 1F);
                double scale = profile.hostile() ? profile.scale() + 1D : 1D;
                visual(host, SkillVisualEffect.PUNCH_INWARD, host.position(), new Vec3(2D * scale, scale, 0D));
                animation(host, 50);
            }
            case PUNCH_OUTWARD -> {
                sound(host, ModSounds.SHIP_AP_P2.get(), 1F);
                visual(host, SkillVisualEffect.PUNCH_OUTWARD, host.position(), new Vec3(0.35D, 0.3D, 0D));
                animation(host, 50);
            }
            case PUNCH_FINISH -> punch(host, target, profile);
        }
        return true;
    }

    private static void damageNearby(Mob host, Entity primary, SkillProfile profile, ShipSkillAttackState runtime,
                                     boolean finalAttack) {
        boolean tenryuu = profile.kind() == SkillKind.TENRYUU;
        double horizontal = tenryuu ? profile.hostile() ? 1.5D : 2D : 4D;
        double vertical = tenryuu ? 1.5D : 3D;
        float raw = ((IShipAttackBase) host).getAttrs().getAttackDamageHeavy()
                * (finalAttack ? profile.finalMultiplier() : profile.horizontalMultiplier());
        for (Entity target : host.level().getEntities(host, host.getBoundingBox().inflate(horizontal, vertical, horizontal))) {
            if (!areaTarget(host, target, primary) || !runtime.hitEntities.add(target.getUUID())) continue;
            float damage = CombatHelper.modDamageByAdditionAttrs((IShipAttackBase) host, target, raw, 0);
            damage = CombatHelper.applyCombatRateToDamage((IShipAttackBase) host, target, true, 1F, damage);
            damage = CombatHelper.applyDamageReduceOnPlayer(target, damage);
            if (target.hurt(host.damageSources().mobAttack(host), damage)) {
                ShipOnHitEffects.dispatch(host, target, damage);
                visual(target, SkillVisualEffect.LIGHT_HIT, target.position(), Vec3.ZERO);
                if (host.getRandom().nextBoolean()) sound(host, SoundEvents.GENERIC_EXPLODE, pitch(host));
                boolean ship = target instanceof IShipAttackBase;
                target.setDeltaMovement(target.getDeltaMovement().add(tenryuu ? ship ? 0.02D : 0.05D : 0D,
                        tenryuu ? ship ? 0.2D : 0.4D : ship ? 0.25D : 0.5D,
                        tenryuu ? ship ? 0.02D : 0.05D : 0D));
                target.hurtMarked = true;
                flare(host, target);
            }
        }
    }

    private static void punch(Mob host, Entity target, SkillProfile profile) {
        IShipAttackBase ship = (IShipAttackBase) host;
        Vec3 start = host.position();
        Vec3 direction = target.position().subtract(start).normalize();
        float distance = (float) Math.sqrt(host.distanceToSqr(target));
        float raw = CombatHelper.modDamageByAdditionAttrs(ship, target, ship.getAttrs().getAttackDamageHeavy(), 2);
        float damage = CombatHelper.applyCombatRateToDamage(ship, target, false, distance, raw);
        damage = CombatHelper.applyDamageReduceOnPlayer(target, damage);
        if (!CombatHelper.isFriendlyFire(host, target) && target.hurt(host.damageSources().mobAttack(host), damage)) {
            ShipOnHitEffects.dispatch(host, target, damage);
            flare(host, target);
        }
        visual(host, SkillVisualEffect.PUNCH_FINISH, start, target.position());
        sound(host, ModSounds.SHIP_AP_ATTACK.get(), 1F);
        animation(host, 50);
        teleport(host, new Vec3(target.getX() + direction.x * 2D, target.getY(), target.getZ() + direction.z * 2D));
        double range = profile.hostile() ? 3.5D + profile.scale() * 0.5D : 3.5D;
        float area = damage * 0.5F;
        for (Entity nearby : host.level().getEntities(host, host.getBoundingBox().inflate(range))) {
            if (!areaTarget(host, nearby, target)) continue;
            float splash = CombatHelper.applyCombatRateToDamage(ship, nearby, false, distance, area);
            splash = CombatHelper.applyDamageReduceOnPlayer(nearby, splash);
            if (nearby.hurt(host.damageSources().mobAttack(host), splash)) {
                ShipOnHitEffects.dispatch(host, nearby, splash);
                flare(host, nearby);
            }
        }
    }

    public static void stopPath(Mob host) {
        ShipMovementExecutor.run(host, MovementPlan.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.SKILL_ATTACK)));
    }

    private static void motion(Mob host, Vec3 motion) {
        SkillState sequence = execution(host).sequence().orElseThrow();
        Vec3 velocity = sequence.kind() == SkillKind.TATSUTA && sequence.phase() == SkillPhase.FINAL
                ? new Vec3(0D, 0.1D, 0D) : motion;
        ShipMovementExecutor.run(host, MovementPlan.of(new MovementStep.SkillMotion(MovementBody.SELF,
                new MovementPoint(velocity.x, velocity.y, velocity.z), MovementReason.SKILL_ATTACK)));
    }

    private static boolean teleport(Mob host, Vec3 destination) {
        if (!ConfigHandler.canTeleport() || !ShipMovementGate.skillDestination(host, destination)) return false;
        ShipSkillAttackState runtime = execution(host);
        runtime.teleportRequested = true;
        try {
            return ShipMovementExecutor.tryTeleport(host, new MovementStep.Teleport(MovementBody.SELF,
                    new MovementPoint(destination.x, destination.y, destination.z), MovementReason.SKILL_ATTACK,
                    ShipCommandStateAdapter.handle(host).dimension()));
        } finally {
            runtime.teleportRequested = false;
        }
    }

    public static boolean teleportAuthorized(Mob host) {
        return supported(host) && permitted(host) && execution(host).teleportRequested;
    }

    private static void face(Mob host, Vec3 direction) {
        ShipMovementExecutor.skillFacing(host, direction);
    }

    private static void project(Mob host, ShipSkillAttackState runtime) {
        SkillState state = runtime.sequence().orElseThrow();
        int pose = switch (state.phase()) {
            case IDLE -> 0;
            case READY -> -1;
            case CHARGE, PUNCH_FIRST -> 1;
            case HORIZONTAL, SPIN, PUNCH_SECOND -> 2;
            case FINAL, PUNCH_THIRD -> 3;
        };
        IShipAttackBase ship = (IShipAttackBase) host;
        if (ship.getStateEmotion(ID.S.Phase) != pose) ship.setStateEmotion(ID.S.Phase, pose, true);
    }

    public static void visual(Entity host, SkillVisualEffect effect, Vec3 position, Vec3 vector) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(49));
        try {
            buffer.writeByte(effect.ordinal());
            buffer.writeDouble(position.x).writeDouble(position.y).writeDouble(position.z);
            buffer.writeDouble(vector.x).writeDouble(vector.y).writeDouble(vector.z);
            byte[] payload = new byte[49];
            buffer.getBytes(0, payload);
            ModNetworking.sendToAllTracking(new S2CSpawnParticlePacket(VISUAL_PACKET_TYPE, host.getId(), payload), host);
        } finally {
            buffer.release();
        }
    }

    public static void animation(Entity host, int duration) {
        ModNetworking.sendToAllTracking(new S2CAttackAnimationPacket(host.getId(), duration), host);
    }

    private static float pitch(Mob host) {
        return host instanceof BasicEntityShip ship ? ship.getVoicePitch() : ((BasicEntityShipHostile) host).getVoicePitch();
    }

    private static void sound(Mob host, SoundEvent sound, float pitch) {
        sound(host, sound, pitch, 1F);
    }

    private static void sound(Mob host, SoundEvent sound, float pitch, float scale) {
        host.level().playSound(null, host.blockPosition(), sound, host.getSoundSource(),
                (float) ConfigHandler.volumeAttack() * scale, pitch);
    }

    private static void voice(Mob host) {
        if (host.getRandom().nextInt(10) <= 7) return;
        SoundEvent sound = host instanceof BasicEntityShip ship ? ship.getCustomSound(1, ship) : ModSounds.SHIP_HIT.get();
        sound(host, sound, pitch(host));
    }

    private static void flare(Mob host, Entity target) {
        if (host instanceof BasicEntityShip ship) ship.flareTarget(target);
    }
}
