package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.*;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.DebugProfiler;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Range target acquisition goal.
 * Ported from EntityAIShipRangeTarget (setMutexBits: 1)
 * <p>
 * Target priority: AntiAir > AntiSub > PVPFirst > normal target
 */
public class ShipRangeTargetGoal extends Goal {

    protected final IShipAttackBase host;
    protected final Mob entity;
    protected final java.util.function.Predicate<Entity> targetSelector;
    protected BasicEntityShip hostShip;
    protected Entity targetEntity;
    protected int range;
    /** earliest entity tick at which the next target scan may run */
    private int nextScanTick;

    public ShipRangeTargetGoal(IShipAttackBase host) {
        this.host = host;
        this.entity = (Mob) host;
        // [PORT] 1.10.2 targetTasks mutex -> 1.20.1 TARGET control flag.
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));

        if (host instanceof BasicEntityShip) {
            this.hostShip = (BasicEntityShip) host;
            this.targetSelector = new TargetHelper.Selector(this.entity);
        } else if (host instanceof BasicEntityShipHostile) {
            this.hostShip = null;
            this.targetSelector = new TargetHelper.SelectorForHostile(this.entity);
        } else {
            this.hostShip = null;
            this.targetSelector = new TargetHelper.Selector(this.entity);
        }

        updateRange();
    }

    private static boolean hasNoTargets(List<LivingEntity> targets) {
        return targets == null || targets.isEmpty();
    }

    @Override
    public boolean canUse() {
        ProfilerFiller profiler = DebugProfiler.push(this.entity.level(), "shincolle.ai.range_target.can_use");
        try {
            if (this.host.getIsSitting() || this.host.getStateMinor(ID.M.CraneState) > 0) {
                DebugProfiler.count(profiler, "shincolle.ai.range_target.blocked.sit_or_crane");
                return false;
            }

            // Upstream (1.10.2 EntityAIShipRangeTarget) throttles acquisition to
            // one scan per 8 ticks; without it this ran a full nearby-entity
            // scan every tick per ship.
            //
            // This must NOT be written as `tickCount % 8 != 0`: since 1.14,
            // Mob#serverAiStep only runs goalSelector/targetSelector.tick()
            // (the pass that calls canUse()) on every *other* tick, with the
            // parity set by (serverTickCount + entityId). An exact-modulo gate
            // can therefore land permanently out of phase with the ticks
            // canUse() is actually invoked on, leaving roughly half of all
            // ships unable to ever acquire a target. Track the next allowed
            // scan tick instead, which is correct for any call cadence.
            int now = this.host.getTickExisted();
            if (now < this.nextScanTick) {
                DebugProfiler.count(profiler, "shincolle.ai.range_target.blocked.scan_cooldown");
                return false;
            }
            this.nextScanTick = now + 8;

            updateRange();

            // Upstream box is (range, range * 0.75, range) - flattened
            // vertically, symmetric horizontally. The port had
            // (range, range * 2, range * 2), which both over-reached upward and
            // made detection reach twice as far along Z as along X, so whether
            // a ship noticed you depended on which side you approached from.
            AABB searchBox = this.entity.getBoundingBox()
                    .inflate(this.range, this.range * 0.75D, this.range);
            List<LivingEntity> targets = null;
            String targetTier = "Normal";

            // Priority-based target selection for friendly ships
            if (this.hostShip != null) {
                // 1. Anti-Air: target flying entities first
                if (this.hostShip.getStateFlag(ID.F.AntiAir)) {
                    targets = findTargetsByType(searchBox, IShipFlyable.class);
                    // also search for vanilla flying mobs
                    List<LivingEntity> flyingTargets = findTargetsByType(searchBox, FlyingMob.class);
                    targets = unionLists(targets, flyingTargets);
                    if (!hasNoTargets(targets)) {
                        targetTier = "AntiAir";
                    }
                }

                // 2. Anti-Sub: target invisible/submarine entities
                if (targets == null || targets.isEmpty()) {
                    if (this.hostShip.getStateFlag(ID.F.AntiSS)) {
                        targets = findTargetsByType(searchBox, IShipInvisible.class);
                        if (!hasNoTargets(targets)) {
                            targetTier = "AntiSub";
                        }
                    }
                }

                // 3. PVP First: target other player's ships
                if (targets == null || targets.isEmpty()) {
                    if (this.hostShip.getStateFlag(ID.F.PVPFirst)) {
                        targets = findTargetsByType(searchBox, BasicEntityShip.class);
                        if (!hasNoTargets(targets)) {
                            targetTier = "PVPFirst";
                        }
                    }
                }
            }

            // 4. Normal: any valid target
            if (targets == null || targets.isEmpty()) {
                targets = this.entity.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                        this::isValidTarget);
            }

            if (!targets.isEmpty()) {
                // sort by distance
                targets.sort(Comparator.comparingDouble(this.entity::distanceToSqr));

                // pick nearest, or random from top 3
                if (targets.size() > 2) {
                    this.targetEntity = targets.get(this.entity.getRandom().nextInt(3));
                } else {
                    this.targetEntity = targets.get(0);
                }
                DebugProfiler.count(profiler, "shincolle.ai.range_target.can_use.success");
                LogHelper.diag("DIAG: target select ship=" + this.entity
                        + " tier=" + targetTier + " target=" + this.targetEntity);
                LogHelper.debug("DEBUG: range target AI: " + this.entity
                        + " acquired target=" + this.targetEntity
                        + " dist=" + Math.sqrt(this.entity.distanceToSqr(this.targetEntity))
                        + " range=" + this.range + " candidates=" + targets.size());
                return true;
            }

            DebugProfiler.count(profiler, "shincolle.ai.range_target.can_use.no_target");
            LogHelper.debug("DEBUG: range target AI: " + this.entity
                    + " no target found in range=" + this.range);
            return false;
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    /**
     * Find targets that implement a specific interface/class within the search box.
     * Also applies the target selector predicate.
     */
    private <T> List<LivingEntity> findTargetsByType(AABB searchBox, Class<T> targetType) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity e : this.entity.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                this::isValidTarget)) {
            if (targetType.isInstance(e)) {
                result.add(e);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Union two lists, returning a combined non-null list.
     */
    private List<LivingEntity> unionLists(List<LivingEntity> a, List<LivingEntity> b) {
        if (a == null || a.isEmpty())
            return b;
        if (b == null || b.isEmpty())
            return a;
        List<LivingEntity> result = new ArrayList<>(a);
        for (LivingEntity e : b) {
            if (!result.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public void start() {
        if (this.host != null) {
            this.entity.setTarget((LivingEntity) this.targetEntity);
        }
    }

    @Override
    public void stop() {
        // Only clear the target acquired by this goal. A command or another
        // target goal may have replaced it before stop() runs.
        if (this.entity.getTarget() == this.targetEntity) {
            this.entity.setTarget(null);
        }
        this.targetEntity = null;
    }

    @Override
    public boolean canContinueToUse() {
        Entity target = this.entity.getTarget();

        if (!(target instanceof LivingEntity) || !target.isAlive()) {
            LogHelper.debug("DEBUG: range target AI: " + this.entity
                    + " lost target: target null or dead");
            return false;
        }

        // Plain range check, as upstream. An earlier fix widened this into a
        // hysteresis band to stop boundary-distance targets flickering, but
        // that flicker was a symptom of the missing 8-tick acquisition
        // throttle in canUse(); with the throttle restored this matches
        // upstream and keeps engagement range honest.
        double d0 = this.range * this.range;
        if (this.entity.distanceToSqr(target) > d0) {
            LogHelper.debug("DEBUG: range target AI: " + this.entity
                    + " lost target=" + target + ": out of range ("
                    + Math.sqrt(this.entity.distanceToSqr(target)) + " > " + this.range + ")");
            return false;
        }

        // don't attack invincible players
        return !(target instanceof Player player) || !player.getAbilities().invulnerable;
    }

    /**
     * Check if an entity is a valid target.
     * Delegates to TargetHelper.Selector or SelectorForHostile.
     */
    protected boolean isValidTarget(LivingEntity candidate) {
        return this.targetSelector.test(candidate);
    }

    private void updateRange() {
        this.range = Math.round(this.host.getAttrs().getAttackRange());
        if (this.range < 2) {
            this.range = Math.max(2, this.host.getStateMinor(ID.M.FollowMax) + 2);
        }
    }
}
