package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.AttackPlan;
import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.HoldFireReason;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.DebugProfiler;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Set;

/**
 * Range attack goal (cannon fire).
 * Ported from EntityAIShipRangeAttack (setMutexBits: 1)
 */
public class ShipRangeAttackGoal extends Goal {
    private static final int STUCK_RESET_THRESHOLD = -40;
    private static final Set<WeaponChannel> CANNONS = EnumSet.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY);

    private final IShipCannonAttack host;
    private final Mob entity;
    private Entity target;
    private int nextRepathTick;
    private String lastDiagnosticState;
    private final ShipCombatMover combatMover = new ShipCombatMover();

    public ShipRangeAttackGoal(IShipCannonAttack host) {
        this.host = host;
        this.entity = (Mob) host;
        // Original setMutexBits(1): movement only, the look goals stay free.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        ShipCombatGate.carry(host, WeaponChannel.LIGHT, WeaponChannel.HEAVY);
    }

    @Override
    public boolean canUse() {
        return ShipCombatGate.active(this.entity) && this.canUseNew();
    }

    /**
     * Whether the ship may fire and at what comes from the combat gate. The crane and the
     * ammunition stop only the cannons, so they stay here.
     */
    private boolean canUseNew() {
        ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(this.entity);
        Set<HoldFireReason> reasons = engagement.holdReasons();
        if (reasons.contains(HoldFireReason.FIRING_BLOCKED)) return false;
        ProfilerFiller profiler = DebugProfiler.push(this.entity.level(), "shincolle.ai.range_attack.can_use");
        try {
            if (reasons.contains(HoldFireReason.SITTING) || ShipMovementGate.craneBusy(this.host)) {
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.blocked.sit_or_crane");
                this.logDiagnosticState("blocked:sit_or_crane");
                return false;
            }
            if (reasons.contains(HoldFireReason.ON_SHIP_MOUNT)) {
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.blocked.mount_controls_attack");
                this.logDiagnosticState("blocked:mount_controls_attack");
                return false;
            }
            if (engagement.engaged() && this.canUseAnyRangedAttack()) {
                this.target = engagement.target();
                DebugProfiler.count(profiler, "shincolle.ai.range_attack.can_use.success");
                this.logDiagnosticState("ready");
                return true;
            }
            DebugProfiler.count(profiler, "shincolle.ai.range_attack.can_use.no_valid_target_or_ammo");
            this.logDiagnosticState(engagement.engaged() ? "blocked:no_valid_ammo" : "blocked:no_valid_target");
            return false;
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    @Override
    public void start() {
        this.nextRepathTick = this.entity.tickCount;
    }

    @Override
    public boolean canContinueToUse() {
        // follow the lock at once, even mid-path
        return ShipCombatGate.active(this.entity) && this.canUseNew();
    }

    @Override
    public void stop() {
        // Upstream's resetTask() clears target/aim only - it deliberately
        // leaves the current path alone. Cancelling navigation here made
        // ships jerk to a halt every time the goal reset.
        this.target = null;
        this.combatMover.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ShipCombatGate.active(this.entity)) this.tickNew();
    }

    /**
     * Move and look only; the fire control goal fires. The stuck reset keeps its place here:
     * when neither cannon has fired for 40 ticks past its ready tick, both wait a short delay again.
     */
    private void tickNew() {
        if (this.target == null) return;
        boolean onSight = this.entity.getSensing().hasLineOfSight(this.target);
        if (!onSight && this.host.getStateFlag(ID.F.OnSightChase)) {
            this.logDiagnosticState("stopped:lost_sight");
            this.stop();
            return;
        }
        int now = this.entity.tickCount;
        AttackPlan plan = ShipCombatGate.plan(this.entity, ShipCombatGate.engagement(this.entity));
        if (this.combatMover.apply(this.entity, this.target, plan.cannon().stopForFire(), 1.0D,
                now >= this.nextRepathTick, true)) {
            this.nextRepathTick = now + 32;
        }
        this.entity.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        ShipCombatState state = ShipCombatGate.state(this.entity);
        if (state != null && CombatTimingReducer.stuck(state.timing(now), CANNONS, now, -STUCK_RESET_THRESHOLD)) {
            this.logDiagnosticState("stopped:stuck_reset");
            state.setTiming(CombatTimingReducer.onStuckReset(state.timing(now), CANNONS, now));
            this.stop();
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
}
