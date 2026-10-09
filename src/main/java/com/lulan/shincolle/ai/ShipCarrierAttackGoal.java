package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.combat.AttackPlan;
import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.CombatHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Set;

/**
 * Carrier aircraft launch/attack goal.
 * Ported from EntityAIShipCarrierAttack (setMutexBits: 2)
 * <p>
 * Alternates between light and heavy aircraft launches.
 * Attack delay calculated via CombatHelper.getAttackDelay:
 * light aircraft: type 3
 * heavy aircraft: type 4
 */
public class ShipCarrierAttackGoal extends Goal {
    private static final Set<WeaponChannel> AIR = EnumSet.of(WeaponChannel.AIR);

    private final IShipAircraftAttack host;
    private final Mob entity;
    private Entity target;
    private int launchDelay;
    private int launchDelayMax;
    private boolean launchType; // true = light, false = heavy
    private float range;
    private float rangeSq;
    private double distSq;
    private double distX, distY, distZ;
    private int nextAttrTick;
    private int nextRepathTick;
    private final ShipCombatMover combatMover = new ShipCombatMover();

    public ShipCarrierAttackGoal(IShipAircraftAttack host) {
        this.host = host;
        this.entity = (Mob) host;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));

        this.launchDelay = 20;
        this.launchDelayMax = 40;
        this.launchType = false;
        ShipCombatGate.carry(host, WeaponChannel.AIR);
    }

    @Override
    public boolean canUse() {
        if (ShipCombatGate.active(this.entity)) {
            // NEW: whether the ship may fire and at what comes from the combat gate; the crane
            // and the aircraft stop only this weapon, so they stay here
            ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(this.entity);
            if (!engagement.engaged() || ShipMovementGate.craneBusy(this.host) || !this.canLaunchAny()) {
                return false;
            }
            this.target = engagement.target();
            return true;
        }
        if (ShipActionGate.blocked(this.entity, ActionKind.FIRING)) return false;
        if (this.host.getIsSitting() || ShipMovementGate.craneBusy(this.host)) {
            return false;
        }

        if (this.host.getIsRiding()) {
            if (this.entity.getVehicle() instanceof BasicEntityMount) {
                return false;
            }
        }

        Entity target = this.host.getEntityTarget();

        if (target != null && target.isAlive() &&
                ((this.host.getAttackType(ID.F.AtkType_AirLight) && this.host.getStateFlag(ID.F.UseAirLight)
                        && this.host.hasAmmoLight() && this.host.hasAirLight()) ||
                        (this.host.getAttackType(ID.F.AtkType_AirHeavy) && this.host.getStateFlag(ID.F.UseAirHeavy)
                                && this.host.hasAmmoHeavy() && this.host.hasAirHeavy()))) {
            this.target = target;
            return true;
        }

        return false;
    }

    @Override
    public void start() {
        // zero out distance state (original zeroes all distance vars)
        this.distSq = 0D;
        this.distX = 0D;
        this.distY = 0D;
        this.distZ = 0D;
        this.updateAttackParams();
        int now = this.entity.tickCount;
        this.nextAttrTick = now;
        this.nextRepathTick = now;
    }

    @Override
    public boolean canContinueToUse() {
        // NEW: follow the lock at once, even mid-path
        if (ShipCombatGate.active(this.entity)) return this.canUse();
        if (ShipActionGate.blocked(this.entity, ActionKind.FIRING)) return false;
        if (this.target != null && this.target.isAlive() && !this.entity.getNavigation().isDone()) {
            return true;
        }
        return this.canUse();
    }

    @Override
    public void stop() {
        this.target = null;
        this.combatMover.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ShipCombatGate.active(this.entity)) {
            this.tickNew();
            return;
        }
        if (ShipActionGate.blocked(this.entity, ActionKind.FIRING)) return;
        if (this.target == null || this.host == null)
            return;

        boolean onSight = this.entity.getSensing().hasLineOfSight(this.target);

        // on-sight-chase: stop AI if target out of sight and flag set
        if (!onSight) {
            if (this.host.getStateFlag(ID.F.OnSightChase)) {
                this.stop();
                return;
            }
        }

        // update attributes every 64 ticks
        int now = this.entity.tickCount;
        if (now >= this.nextAttrTick) {
            this.nextAttrTick = now + 64;
            this.updateAttackParams();
        }

        // chase / stop logic with cached distance (matching original's two-stage pattern)
        // recalculate distance when out of range
        this.distX = this.target.getX() - this.entity.getX();
        this.distY = this.target.getY() - this.entity.getY();
        this.distZ = this.target.getZ() - this.entity.getZ();
        this.distSq = distX * distX + distY * distY + distZ * distZ;

        boolean hold = this.distSq < this.rangeSq && onSight && !this.host.getStateFlag(ID.F.UseMelee);
        if (ShipMovementGate.active()) {
            // NEW: stay inside the region the movement intent allows
            if (this.combatMover.apply(this.entity, this.target, hold, 1.0D, now >= this.nextRepathTick, true)) {
                this.nextRepathTick = now + 32;
            }
        } else if (hold) {
            // in range now, stop moving
            this.entity.getNavigation().stop();
        } else if (now >= this.nextRepathTick) {
            this.nextRepathTick = now + 32;
            // still out of range, chase every 32 ticks
            this.entity.getNavigation().moveTo(this.target, 1.0D);
        }

        // look at target (original uses target Y + 2D, modern API uses eye height)
        this.entity.getLookControl().setLookAt(this.target, 30.0F, 60.0F);

        this.launchDelay--;

        // handle single ammo type mode
        if (!this.host.getStateFlag(ID.F.UseAirLight)) {
            this.launchType = false;
        } else if (!this.host.getStateFlag(ID.F.UseAirHeavy)) {
            this.launchType = true;
        }

        // launch aircraft when in range, on sight, and delay elapsed
        if (onSight && this.distSq <= this.rangeSq && this.launchDelay <= 0) {
            // light aircraft
            if (this.launchType && this.host.hasAmmoLight() && this.host.hasAirLight()) {
                this.host.attackEntityWithAircraft(this.target);
                this.launchDelay = this.launchDelayMax;
            }

            // heavy aircraft
            if (!this.launchType && this.host.hasAmmoHeavy() && this.host.hasAirHeavy()) {
                this.host.attackEntityWithHeavyAircraft(this.target);
                this.launchDelay = this.launchDelayMax;
            }

            // always toggle type (matching original)
            this.launchType = !this.launchType;
        }

        // reset if stuck too long
        if (this.launchDelay < -80) {
            this.launchDelay = 20;
            this.stop();
        }
    }

    /**
     * NEW: move and look only; the fire control goal launches. When no aircraft has launched for
     * 80 ticks past the ready tick, the launch waits a short delay again.
     */
    private void tickNew() {
        if (this.target == null) return;
        if (!this.entity.getSensing().hasLineOfSight(this.target) && this.host.getStateFlag(ID.F.OnSightChase)) {
            this.stop();
            return;
        }
        int now = this.entity.tickCount;
        AttackPlan plan = ShipCombatGate.plan(this.entity, ShipCombatGate.engagement(this.entity));
        if (this.combatMover.apply(this.entity, this.target, plan.carrier().stopForFire(), 1.0D,
                now >= this.nextRepathTick, true)) {
            this.nextRepathTick = now + 32;
        }
        this.entity.getLookControl().setLookAt(this.target, 30.0F, 60.0F);

        ShipCombatState state = ShipCombatGate.state(this.entity);
        if (state != null && CombatTimingReducer.stuck(state.timing(now), AIR, now, 80)) {
            state.setTiming(CombatTimingReducer.onStuckReset(state.timing(now), AIR, now));
            this.stop();
        }
    }

    private boolean canLaunchAny() {
        return (this.host.getAttackType(ID.F.AtkType_AirLight) && this.host.getStateFlag(ID.F.UseAirLight)
                && this.host.hasAmmoLight() && this.host.hasAirLight())
                || (this.host.getAttackType(ID.F.AtkType_AirHeavy) && this.host.getStateFlag(ID.F.UseAirHeavy)
                && this.host.hasAmmoHeavy() && this.host.hasAirHeavy());
    }

    private void updateAttackParams() {
        float atkSpd = this.host.getAttrs().getAttackSpeed();
        this.launchDelayMax = CombatHelper.getAttackDelay(atkSpd, this.launchType ? 3 : 4);
        this.range = this.host.getAttrs().getAttackRange();
        this.rangeSq = this.range * this.range;
    }
}
