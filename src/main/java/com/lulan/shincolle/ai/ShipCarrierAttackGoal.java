package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.AttackPlan;
import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Set;

/**
 * Carrier aircraft launch/attack goal.
 * Ported from EntityAIShipCarrierAttack (setMutexBits: 2)
 * <p>
 * This goal moves the ship and turns it to its target; the fire control goal launches the aircraft
 * on the ship's one timer.
 */
public class ShipCarrierAttackGoal extends Goal {
    private static final Set<WeaponChannel> AIR = EnumSet.of(WeaponChannel.AIR);

    private final IShipAircraftAttack host;
    private final Mob entity;
    private Entity target;
    private int nextRepathTick;
    private final ShipCombatMover combatMover = new ShipCombatMover();

    public ShipCarrierAttackGoal(IShipAircraftAttack host) {
        this.host = host;
        this.entity = (Mob) host;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        ShipCombatGate.carry(host, WeaponChannel.AIR);
    }

    @Override
    public boolean canUse() {
        if (!ShipCombatGate.active(this.entity)) return false;
        // Whether the ship may fire and at what comes from the combat gate; the crane
        // and the aircraft stop only this weapon, so they stay here
        ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(this.entity);
        if (!engagement.engaged() || ShipMovementGate.craneBusy(this.host) || !this.canLaunchAny()) {
            return false;
        }
        this.target = engagement.target();
        return true;
    }

    @Override
    public void start() {
        this.nextRepathTick = this.entity.tickCount;
    }

    @Override
    public boolean canContinueToUse() {
        // follow the lock at once, even mid-path
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
        if (ShipCombatGate.active(this.entity)) this.tickNew();
    }

    /**
     * Move and look only; the fire control goal launches. When no aircraft has launched for
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
}
