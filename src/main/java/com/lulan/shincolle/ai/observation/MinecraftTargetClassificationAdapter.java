package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.ClassifiedTargetObservation;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.EntityClassification;
import com.lulan.shincolle.ai.domain.RelationClassification;
import com.lulan.shincolle.ai.domain.TargetClassificationObservation;
import com.lulan.shincolle.ai.domain.TargetEntityClassifier;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetLineOfSightObservation;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateFacts;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.domain.TargetTraitClassification;
import com.lulan.shincolle.ai.domain.TargetTraitClassifier;
import com.lulan.shincolle.ai.domain.TimedObservation;
import com.lulan.shincolle.api.target.TargetTraits;
import com.lulan.shincolle.api.target.TargetTrait;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipInvisible;
import com.lulan.shincolle.entity.IShipOwner;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.TargetHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Single Minecraft fact-capture boundary for combat-target classification. */
public final class MinecraftTargetClassificationAdapter {
    private MinecraftTargetClassificationAdapter() {
    }

    public static TargetClassificationObservation classify(
            Entity source,
            Entity target,
            TargetPredicateKind kind,
            TargetPredicatePolicy policy,
            long observedAtTick,
            TargetObservationProfiler profiler) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(profiler, "profiler");
        profiler.recordClassification();

        TargetHandle handle = handleOf(target);
        boolean hostPresent = source != null;
        boolean targetAlive = target.isAlive();
        boolean sameEntity = hostPresent && source.equals(target);
        boolean player = target instanceof Player;
        boolean playerInvulnerable = player && ((Player) target).getAbilities().invulnerable;
        boolean entityInvulnerable = TargetHelper.isEntityInvulnerable(target);

        boolean invisible = target.isInvisible();
        boolean hostDetectsInvisible = invisible && canDetectInvisible(source);
        boolean invisibleDetectable = !invisible || hostDetectsInvisible;

        boolean lineOfSightRequired = requiresLineOfSight(kind, source);
        boolean measurementRequired = lineOfSightRequired
                && targetAlive
                && !sameEntity
                && !player
                && !entityInvulnerable
                && invisibleDetectable;
        boolean hasLineOfSight = true;
        TargetLineOfSightObservation lineOfSight;
        if (measurementRequired) {
            profiler.recordLineOfSightQuery();
            hasLineOfSight = hasLineOfSight(source, target);
            lineOfSight = TargetLineOfSightObservation.measured(
                    handle, hasLineOfSight, observedAtTick);
        } else {
            lineOfSight = TargetLineOfSightObservation.notMeasured(handle);
        }

        boolean airplane = target instanceof BasicEntityAirplane;
        boolean abyssMissile = target instanceof EntityAbyssMissile;
        boolean submarine = target instanceof IShipInvisible;
        boolean friendlyShip = target instanceof BasicEntityShip;
        boolean mount = target instanceof BasicEntityMount;
        boolean hostileShip = target instanceof BasicEntityShipHostile;
        boolean monsterOrSlime = target instanceof Monster || target instanceof Slime;
        boolean shipOwner = target instanceof IShipOwner;
        Set<TargetTrait> registeredTraits = TargetTraits.traitsFor(target.getType());
        boolean antiAirEligible = airplane || abyssMissile
                || registeredTraits.contains(TargetTrait.ANTI_AIR_ELIGIBLE);
        boolean antiSubmarineEligible = submarine
                || registeredTraits.contains(TargetTrait.ANTI_SUBMARINE_ELIGIBLE);
        boolean commonAutomaticChecksPass = kind == TargetPredicateKind.FRIENDLY_AUTOMATIC
                && targetAlive
                && hostPresent
                && !sameEntity
                && !player
                && !entityInvulnerable
                && invisibleDetectable
                && (!lineOfSightRequired || hasLineOfSight);
        boolean customAttackClassListed = commonAutomaticChecksPass
                && !airplane
                && !submarine
                && !hostileShip
                && !monsterOrSlime
                && !shipOwner
                && isAttackTargetClassListed(source, target);

        TargetPredicateFacts facts = new TargetPredicateFacts(
                hostPresent, true, targetAlive, sameEntity, player, playerInvulnerable,
                entityInvulnerable, invisible, hostDetectsInvisible, lineOfSightRequired,
                hasLineOfSight, airplane, abyssMissile, submarine, friendlyShip, mount,
                hostileShip, monsterOrSlime, shipOwner, customAttackClassListed);
        EntityClassification entity = TargetEntityClassifier.classify(facts);
        RelationClassification relation = captureRequiredRelation(
                kind, source, target, entity, facts, antiAirEligible,
                antiSubmarineEligible, policy, profiler);
        TargetTraitClassification traits = TargetTraitClassifier.classify(
                facts, registeredTraits);
        ClassifiedTargetObservation classified =
                new ClassifiedTargetObservation(entity, relation, traits);
        return new TargetClassificationObservation(
                handle, new TimedObservation<>(classified, observedAtTick), lineOfSight);
    }

    private static RelationClassification captureRequiredRelation(
            TargetPredicateKind kind,
            Entity source,
            Entity target,
            EntityClassification entity,
            TargetPredicateFacts facts,
            boolean antiAirEligible,
            boolean antiSubmarineEligible,
            TargetPredicatePolicy policy,
            TargetObservationProfiler profiler) {
        if (!entity.valid()) {
            return new RelationClassification(false, false, false);
        }
        boolean sameOwner = false;
        boolean allied = false;
        boolean banned = false;
        switch (kind) {
            case FRIENDLY_AUTOMATIC -> {
                if (entity.player()) {
                    if (!entity.playerInvulnerable()) {
                        switch (policy.shipAttackPlayer()) {
                            case 1 -> banned = banned(source, target, profiler);
                            case 2 -> allied = allied(source, target, profiler);
                            case 3 -> sameOwner = sameOwner(source, target, profiler);
                            default -> {
                            }
                        }
                    }
                } else if (!entity.entityInvulnerable()
                        && entity.invisibleDetectable()
                        && entity.lineOfSightEligible()) {
                    // Legacy predicates use concrete airplane/submarine types, while
                    // Stage 2 uses registered traits. Capture the union of both
                    // consumers' required relations without repeating any lookup.
                    boolean legacySpecialTarget = facts.airplane() || facts.submarine();
                    boolean legacySpecialNeedsBanned = facts.airplane()
                            ? policy.antiAir() : facts.submarine() && policy.antiSubmarine();
                    boolean stageTwoNeedsBanned = antiAirEligible
                            ? policy.antiAir() : antiSubmarineEligible && policy.antiSubmarine();
                    boolean legacyPvpTarget = !legacySpecialTarget
                            && (facts.friendlyShip() || facts.mount()) && policy.pvpFirst();
                    if (legacySpecialNeedsBanned || stageTwoNeedsBanned || legacyPvpTarget) {
                        banned = banned(source, target, profiler);
                    }
                    if (legacyPvpTarget) {
                        if (!banned && facts.shipOwner()) {
                            allied = allied(source, target, profiler);
                        }
                    } else if (!legacySpecialTarget
                            && !facts.hostileShip()
                            && !facts.monsterOrSlime()
                            && (facts.customAttackClassListed() || facts.shipOwner())) {
                        allied = allied(source, target, profiler);
                    }
                }
            }
            case FRIENDLY_REVENGE -> {
                if (!(entity.player() && entity.playerInvulnerable())
                        && !entity.entityInvulnerable()
                        && entity.invisibleDetectable()) {
                    if (facts.shipOwner()) {
                        allied = allied(source, target, profiler);
                    } else {
                        sameOwner = sameOwner(source, target, profiler);
                    }
                }
            }
            case HOSTILE_AUTOMATIC -> {
                if (!entity.player()
                        && !entity.entityInvulnerable()
                        && !entity.invisible()
                        && !facts.hostileShip()
                        && !(facts.friendlyShip() || facts.mount())
                        && facts.shipOwner()) {
                    sameOwner = sameOwner(source, target, profiler);
                }
            }
            case HOSTILE_REVENGE -> {
                if (!entity.player()
                        && !entity.entityInvulnerable()
                        && !entity.invisible()
                        && !facts.hostileShip()
                        && !facts.friendlyShip()) {
                    sameOwner = sameOwner(source, target, profiler);
                }
            }
            default -> throw new IllegalStateException("Unsupported target predicate kind: " + kind);
        }
        return new RelationClassification(sameOwner, allied, banned);
    }

    private static boolean sameOwner(
            Entity source, Entity target, TargetObservationProfiler profiler) {
        profiler.recordRelationLookup();
        return TargetHelper.checkSameOwner(source, target);
    }

    private static boolean allied(
            Entity source, Entity target, TargetObservationProfiler profiler) {
        profiler.recordRelationLookup();
        return TargetHelper.checkIsAlly(source, target);
    }

    private static boolean banned(
            Entity source, Entity target, TargetObservationProfiler profiler) {
        profiler.recordRelationLookup();
        return TargetHelper.checkIsBanned(source, target);
    }

    private static boolean canDetectInvisible(Entity source) {
        Entity detector = source instanceof BasicEntityShip ? source
                : source instanceof IShipOwner owner ? owner.getHostEntity() : null;
        return detector instanceof BasicEntityShip ship
                && (ship.getStateMinor(ID.M.LevelFlare) >= 1
                || ship.getStateMinor(ID.M.LevelSearchlight) >= 1);
    }

    private static boolean requiresLineOfSight(TargetPredicateKind kind, Entity source) {
        if (kind != TargetPredicateKind.FRIENDLY_AUTOMATIC) {
            return false;
        }
        if (source instanceof BasicEntityShip ship) {
            return ship.getStateFlag(ID.F.OnSightChase);
        }
        return source instanceof Mob;
    }

    private static boolean hasLineOfSight(Entity source, Entity target) {
        return source instanceof Mob mob && mob.getSensing().hasLineOfSight(target);
    }

    private static boolean isAttackTargetClassListed(Entity source, Entity target) {
        if (!(source instanceof IShipAttackBase host)) {
            return false;
        }
        Map<Integer, String> classes = ServerDataManager.getPlayerTargetClass(host.getPlayerUID());
        return classes != null && classes.containsKey(target.getClass().getSimpleName().hashCode());
    }

    private static TargetHandle handleOf(Entity target) {
        ResourceLocation dimension = target.level().dimension().location();
        return new TargetHandle(target.getUUID(),
                new DimensionKey(dimension.getNamespace(), dimension.getPath()));
    }
}
