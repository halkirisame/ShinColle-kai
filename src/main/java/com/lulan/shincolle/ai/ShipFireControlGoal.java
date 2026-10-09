package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.AttackPlan;
import com.lulan.shincolle.ai.domain.combat.CombatDecision;
import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.CombatTimingState;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Under NEW, the only goal that fires. It holds no flags, so no movement goal stops it: a ship
 * fires while it follows, flees or guards. The attack goals only move and look. Every host with
 * an attack goal registers it whatever the mode; it works only while the combat gate is active.
 */
public class ShipFireControlGoal extends Goal {
    private final Mob entity;
    private final IShipCannonAttack host;

    public ShipFireControlGoal(IShipCannonAttack host) {
        this.host = host;
        this.entity = (Mob) host;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    /** Runs while the host engages; holding fire (out of fuel, sitting, no target) stops it. */
    @Override
    public boolean canUse() {
        return ShipCombatGate.active(this.entity) && ShipCombatGate.state(this.entity) != null
                && ShipCombatGate.engagement(this.entity).engaged();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        ShipCombatState state = ShipCombatGate.state(this.entity);
        if (state != null) state.dropAim();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ShipCombatState state = ShipCombatGate.state(this.entity);
        int now = this.entity.tickCount;
        ShipSkillAttackGate.observe(this.entity);
        if (ShipSkillAttackGate.running(this.entity)) {
            state.setTiming(CombatTimingReducer.pauseForSkill(state.timing(now)));
            return;
        }
        ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(this.entity);
        // the goal is checked to continue only every other tick
        if (!engagement.engaged()) {
            state.dropAim();
            return;
        }
        int aimTime = ShipCombatGate.aimTime(this.host);
        CombatTimingState timing = CombatTimingReducer.onIntent(state.timing(now), engagement.intent(), now, aimTime);
        Entity target = engagement.target();
        boolean onSight = this.entity.getSensing().hasLineOfSight(target);
        boolean canAim = CombatTimingReducer.cannonsCanAim(state.loadout(), ShipCombatGate.readiness(this.entity));
        timing = CombatTimingReducer.onCannonSight(timing, canAim, onSight, now, aimTime);
        AttackPlan plan = CombatDecision.decide(
                ShipCombatGate.context(this.entity, engagement, timing, state.loadout(), onSight));
        if (plan.airLaunchDue()) timing = CombatTimingReducer.onAirLaunchDue(timing, plan.airHeavy());
        float speed = Math.max(this.host.getAttrs().getAttackSpeed(), 0.01F);
        for (WeaponChannel channel : plan.fire()) {
            if (ShipSkillAttackGate.running(this.entity)) break;
            int delay = switch (channel) {
                case LIGHT -> {
                    this.host.attackEntityWithAmmo(target);
                    yield Math.max(5, (int) (ConfigHandler.baseAttackSpeed[1] / speed) + ConfigHandler.fixedAttackDelay[1]);
                }
                case HEAVY -> {
                    this.host.attackEntityWithHeavyAmmo(target);
                    yield Math.max(10, (int) (ConfigHandler.baseAttackSpeed[2] / speed) + ConfigHandler.fixedAttackDelay[2]);
                }
                case AIR -> {
                    IShipAircraftAttack carrier = (IShipAircraftAttack) this.host;
                    if (plan.airHeavy()) carrier.attackEntityWithHeavyAircraft(target);
                    else carrier.attackEntityWithAircraft(target);
                    yield CombatHelper.getAttackDelay(this.host.getAttrs().getAttackSpeed(), plan.airHeavy() ? 4 : 3);
                }
                case MELEE -> {
                    if (!this.entity.getMainHandItem().isEmpty()) this.entity.swing(InteractionHand.MAIN_HAND);
                    this.entity.doHurtTarget(target);
                    yield CombatHelper.getAttackDelay(this.host.getAttrs().getAttackSpeed(), 0);
                }
            };
            LogHelper.diag("DIAG: attack fire ship=" + this.entity + " type=" + channel + " target=" + target);
            timing = CombatTimingReducer.onFired(timing, channel, delay, now);
        }
        state.setTiming(timing);
    }
}
