package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.DebugProfiler;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Range attack goal (cannon fire).
 * Ported from EntityAIShipRangeAttack (setMutexBits: 1)
 */
public class ShipRangeAttackGoal extends Goal {
    private static final int INITIAL_LIGHT_DELAY = 20;
    private static final int INITIAL_HEAVY_DELAY = 40;
    private static final int STUCK_RESET_THRESHOLD = -40;

    private final IShipCannonAttack host;
    private final Mob entity;
    private Entity target;
    private int delayLight;
    private int maxDelayLight;
    private int delayHeavy;
    private int maxDelayHeavy;
    private int onSightTime;
    private float range;
    private float rangeSq;
    private int aimTime;
    private int nextAttrTick;
    private int nextRepathTick;
    private String lastDiagnosticState;

    public ShipRangeAttackGoal(IShipCannonAttack host) {
        this.host = host;
        this.entity = (Mob) host;
        // Original setMutexBits(1): movement only, the look goals stay free.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));

        this.delayLight = INITIAL_LIGHT_DELAY;
        this.delayHeavy = INITIAL_HEAVY_DELAY;
        this.maxDelayLight = INITIAL_LIGHT_DELAY;
        this.maxDelayHeavy = INITIAL_HEAVY_DELAY;
    }

    @Override
    public boolean canUse() {
        ProfilerFiller profiler = DebugProfiler.push(this.entity.level(), "shincolle.ai.range_attack.can_use");
        try {
            if (this.host.getIsSitting() || this.host.getStateMinor(ID.M.CraneState) > 0) {
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.blocked.sit_or_crane");
                this.logDiagnosticState("blocked:sit_or_crane");
                return false;
            }

            if (this.host.getIsRiding()) {
                if (this.entity.getVehicle() instanceof BasicEntityMount) {
                    DebugProfiler.count(profiler, "shincolle.ai.range_attack.blocked.mount_controls_attack");
                    this.logDiagnosticState("blocked:mount_controls_attack");
                    return false;
                }
            }

            LivingEntity target = this.entity.getTarget();
            if (target != null && target.isAlive() &&
                    ((this.host.getAttackType(ID.F.AtkType_Light) && this.host.getStateFlag(ID.F.UseAmmoLight)
                            && this.host.hasAmmoLight()) ||
                            (this.host.getAttackType(ID.F.AtkType_Heavy) && this.host.getStateFlag(ID.F.UseAmmoHeavy)
                                    && this.host.hasAmmoHeavy()))) {
                this.target = target;
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.can_use.success");
                this.logDiagnosticState("ready");
                return true;
            }

            DebugProfiler.count(profiler, "shincolle.ai.range_attack.can_use.no_valid_target_or_ammo");
            if (target == null || !target.isAlive()) {
                this.logDiagnosticState("blocked:no_valid_target");
            } else if (this.host.getStateFlag(ID.F.NoFuel)) {
                this.logDiagnosticState("blocked:no_fuel");
            } else {
                this.logDiagnosticState("blocked:no_valid_ammo");
            }
            if (target == null || !target.isAlive()) {
                LogHelper.debug("DEBUG: range attack AI: " + this.entity
                        + " cannot attack: no target from targetSelector");
            } else {
                LogHelper.debug("DEBUG: range attack AI: " + this.entity
                        + " cannot attack target=" + target
                        + ": atkTypeLight=" + this.host.getAttackType(ID.F.AtkType_Light)
                        + " useAmmoLightFlag=" + this.host.getStateFlag(ID.F.UseAmmoLight)
                        + " hasAmmoLight=" + this.host.hasAmmoLight()
                        + " atkTypeHeavy=" + this.host.getAttackType(ID.F.AtkType_Heavy)
                        + " useAmmoHeavyFlag=" + this.host.getStateFlag(ID.F.UseAmmoHeavy)
                        + " hasAmmoHeavy=" + this.host.hasAmmoHeavy());
            }
            return false;
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    @Override
    public void start() {
        this.updateAttackParms();
        int now = this.entity.tickCount;
        this.nextAttrTick = now;
        this.nextRepathTick = now;

        if (this.delayLight <= this.aimTime) {
            this.delayLight = this.aimTime;
        }
        if (this.delayHeavy <= this.aimTime * 2) {
            this.delayHeavy = this.aimTime * 2;
        }

        LogHelper.debug("DEBUG: range attack AI: " + this.entity
                + " start attack on target=" + this.target
                + " aimTime=" + this.aimTime + "t range=" + this.range
                + " delayLight=" + this.delayLight + "t delayHeavy=" + this.delayHeavy + "t");
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target != null && this.target.isAlive() && !this.entity.getNavigation().isDone()) {
            return true;
        }
        return this.canUse();
    }

    @Override
    public void stop() {
        // Upstream's resetTask() clears target/aim only - it deliberately
        // leaves the current path alone. Cancelling navigation here made
        // ships jerk to a halt every time the goal reset.
        this.target = null;
        this.onSightTime = 0;
    }

    @Override
    public void tick() {
        ProfilerFiller profiler = DebugProfiler.push(this.entity.level(), "shincolle.ai.range_attack.tick");
        try {
            if (this.target == null) {
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.tick.no_target");
                this.logDiagnosticState("stopped:tick_no_target");
                return;
            }

            // update attributes periodically (upstream refreshes every 64 ticks)
            int now = this.entity.tickCount;
            if (now >= this.nextAttrTick) {
                this.nextAttrTick = now + 64;
                this.updateAttackParms();
            }

            this.delayLight--;
            this.delayHeavy--;

            double distSq = this.entity.distanceToSqr(this.target);
            boolean onSight = this.entity.getSensing().hasLineOfSight(this.target);

            if (onSight) {
                ++this.onSightTime;
            } else {
                this.onSightTime = 0;

                if (this.host.getStateFlag(ID.F.OnSightChase)) {
                    DebugProfiler.count(profiler, "shincolle.ai.range_attack.tick.lost_sight_stop");
                    this.logDiagnosticState("stopped:lost_sight");
                    LogHelper.debug("DEBUG: range attack AI: " + this.entity
                            + " stopping: lost line of sight to target=" + this.target
                            + " (OnSightChase flag set)");
                    this.stop();
                    return;
                }
            }
            // Matches the 1.10.2 EntityAIShipRangeAttack: hold only when in range,
            // in sight and not a melee ship; otherwise close in, re-pathing every
            // 32 ticks. engageDistance shrinks the range it settles at, and at its
            // default of 100 this is exactly the original condition.
            double engage = com.lulan.shincolle.handler.ConfigHandler.engageDistance() * 0.01D;
            double holdSq = this.rangeSq * engage * engage;

            if (distSq < holdSq && onSight && !this.host.getStateFlag(ID.F.UseMelee)) {
                this.entity.getNavigation().stop();
            } else if (now >= this.nextRepathTick) {
                this.nextRepathTick = now + 32;
                boolean issued = this.entity.getNavigation().moveTo(this.target, 1.0D);
                LogHelper.debug("DEBUG: range attack AI: " + this.entity
                        + " re-path toward target=" + this.target
                        + " dist=" + Math.sqrt(distSq) + " holdRange=" + Math.sqrt(holdSq)
                        + " onSight=" + onSight + " pathIssued=" + issued);
            }

            this.entity.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            // fire if delay done, on sight, in range, and aimed long enough
            if (onSight && distSq <= this.rangeSq && this.onSightTime >= this.aimTime) {
                // light attack
                if (this.delayLight <= 0 && this.host.useAmmoLight() && this.host.hasAmmoLight()) {
                    DebugProfiler.count(profiler, "shincolle.ai.range_attack.tick.fire_light");
                    LogHelper.diag("DIAG: attack fire ship=" + this.entity
                            + " type=light target=" + this.target);
                    this.host.attackEntityWithAmmo(this.target);
                    this.delayLight = this.maxDelayLight;
                    LogHelper.debug("DEBUG: range attack AI: " + this.entity
                            + " fired light at target=" + this.target
                            + " nextDelay=" + this.maxDelayLight + "t");
                }
                // heavy attack
                if (this.delayHeavy <= 0 && this.host.useAmmoHeavy() && this.host.hasAmmoHeavy()) {
                    DebugProfiler.count(profiler, "shincolle.ai.range_attack.tick.fire_heavy");
                    LogHelper.diag("DIAG: attack fire ship=" + this.entity
                            + " type=heavy target=" + this.target);
                    this.host.attackEntityWithHeavyAmmo(this.target);
                    this.delayHeavy = this.maxDelayHeavy;
                    LogHelper.debug("DEBUG: range attack AI: " + this.entity
                            + " fired heavy at target=" + this.target
                            + " nextDelay=" + this.maxDelayHeavy + "t");
                }
            }

            // reset if stuck too long without hitting
            if (this.delayHeavy < -40 && this.delayLight < -40) {
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.tick.stuck_reset");
                this.logDiagnosticState("stopped:stuck_reset");
                LogHelper.debug("DEBUG: range attack AI: " + this.entity
                        + " stuck reset: no hit for 40+ ticks past delay on target=" + this.target);
                this.delayLight = 20;
                this.delayHeavy = 20;
                this.stop();
            }
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    private boolean isMountedOnShipMount() {
        return this.host.getIsRiding() && this.entity.getVehicle() instanceof BasicEntityMount;
    }

    private boolean canUseAnyRangedAttack() {
        boolean canUseLight = this.host.getAttackType(ID.F.AtkType_Light)
                && this.host.getStateFlag(ID.F.UseAmmoLight)
                && this.host.hasAmmoLight();
        boolean canUseHeavy = this.host.getAttackType(ID.F.AtkType_Heavy)
                && this.host.getStateFlag(ID.F.UseAmmoHeavy)
                && this.host.hasAmmoHeavy();
        return canUseLight || canUseHeavy;
    }

    private void logDiagnosticState(String state) {
        if (!state.equals(this.lastDiagnosticState)) {
            this.lastDiagnosticState = state;
            LogHelper.diag("DIAG: attack state ship=" + this.entity + " reason=" + state);
        }
    }

    private void updateAttackParms() {
        float atkSpd = this.host.getAttrs().getAttackSpeed();
        // attack delay = baseAttackSpeed / attackSpeed + fixedAttackDelay
        this.maxDelayLight = Math.max(5,
                (int) (ConfigHandler.baseAttackSpeed[1] / Math.max(atkSpd, 0.01F))
                        + ConfigHandler.fixedAttackDelay[1]);
        this.maxDelayHeavy = Math.max(10,
                (int) (ConfigHandler.baseAttackSpeed[2] / Math.max(atkSpd, 0.01F))
                        + ConfigHandler.fixedAttackDelay[2]);
        this.aimTime = (int) (20.0F * (150 - this.host.getLevel()) / 150.0F) + 10;
        this.range = this.host.getAttrs().getAttackRange();
        this.rangeSq = this.range * this.range;
    }
}
