package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.CombatHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Melee attack on collide goal.
 * Ported from EntityAIShipAttackOnCollide (setMutexBits: 4)
 * <p>
 * Attack delay is dynamic, using CombatHelper.getAttackDelay(aspd, 0).
 * Melee range is width^2 * 16 (matching original formula).
 * Pathfinds every 32 ticks, only when out of melee range.
 */
public class ShipAttackOnCollideGoal extends Goal {

    private final IShipAttackBase host;
    private final Mob entity;
    private final double speed;
    private Entity target;
    private int delayAttack;
    private int delayMax;
    private int nextRepathTick;
    private final ShipCombatMover combatMover = new ShipCombatMover();

    public ShipAttackOnCollideGoal(IShipAttackBase host, double speed) {
        this.host = host;
        this.entity = (Mob) host;
        this.speed = speed;
        this.delayMax = 20;
        this.delayAttack = 20;
        // Original setMutexBits(4): jump only.
        this.setFlags(EnumSet.of(Goal.Flag.JUMP));
        ShipCombatGate.carry(host, WeaponChannel.MELEE);
    }

    @Override
    public boolean canUse() {
        if (ShipCombatGate.active(this.entity)) {
            // NEW: whether the ship may fire and at what comes from the combat gate. Riding
            // anything stops only melee, so it stays here; the crane does not stop melee.
            if (meleeOffUnderNew() || this.entity.isPassenger()) return false;
            ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(this.entity);
            this.target = engagement.target();
            return engagement.engaged();
        }
        if (ShipActionGate.blocked(this.entity, ActionKind.FIRING)) return false;
        if (meleeOffUnderNew()) return false;
        // check riding and sitting first (original order)
        if (this.entity.isPassenger() || this.host.getIsSitting())
            return false;

        this.target = this.host.getEntityTarget();
        return this.target != null && this.target.isAlive();

    }

    /**
     * NEW keeps this goal registered, so it follows the melee flag itself. The check does not
     * depend on the mode: a goal registered under NEW must still obey the flag after a switch
     * to LEGACY, and LEGACY only registers it while the flag is on.
     */
    private boolean meleeOffUnderNew() {
        return this.host != null && !this.host.getStateFlag(ID.F.UseMelee);
    }

    @Override
    public void start() {
        this.nextRepathTick = this.entity.tickCount;
        this.combatMover.reset();
    }

    @Override
    public boolean canContinueToUse() {
        // NEW: follow the lock at once, even mid-path
        if (ShipCombatGate.active(this.entity)) return this.canUse();
        if (ShipActionGate.blocked(this.entity, ActionKind.FIRING)) return false;
        if (this.host == null) return false;
        if (meleeOffUnderNew()) return false;
        if (this.target != null && this.target.isAlive() && !this.entity.getNavigation().isDone()) {
            return true;
        }
        return this.canUse();
    }

    @Override
    public void stop() {
        this.target = null;
        if (ShipMovementGate.active()) {
            ShipMovementExecutor.run(this.entity, ShipMovementExecutor.GOAL_STOPPED);
            return;
        }
        this.entity.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.entity, ActionKind.FIRING)) return;
        if (this.target == null || !this.target.isAlive()) {
            this.stop();
            return;
        }

        // look at target
        this.entity.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // calculate melee range: width^2 * 16 (matching original)
        double distAttack = this.entity.getBbWidth() * this.entity.getBbWidth() * 16F;
        double distTarget = this.entity.distanceToSqr(
                this.target.getX(), this.target.getBoundingBox().minY, this.target.getZ());

        // every 32 ticks: update attack delay and pathfind conditionally
        int now = this.entity.tickCount;
        boolean due = now >= this.nextRepathTick;
        if (ShipCombatGate.active(this.entity)) {
            // NEW: move only; the fire control goal strikes
            this.combatMover.apply(this.entity, this.target, distTarget <= distAttack, this.speed, due, false);
            if (due) this.nextRepathTick = now + 32;
            return;
        }
        if (ShipMovementGate.active()) {
            // NEW: stay inside the region the movement intent allows
            this.combatMover.apply(this.entity, this.target, distTarget <= distAttack, this.speed, due, false);
        }
        if (due) {
            this.nextRepathTick = now + 32;
            // dynamically recalculate attack delay from ship stats
            this.delayMax = CombatHelper.getAttackDelay(
                    this.host.getAttrs().getAttackSpeed(), 0);

            // only pathfind when out of melee range; clear path when in range
            if (ShipMovementGate.active()) {
                // the combat mover above already moved or stopped
            } else if (distTarget > distAttack) {
                this.entity.getNavigation().moveTo(this.target, this.speed);
            } else {
                this.entity.getNavigation().stop();
            }
        }

        // decrement attack delay
        this.delayAttack--;

        // attack when in range and delay elapsed
        if (distTarget <= distAttack && this.delayAttack <= 0) {
            this.delayAttack = this.delayMax;

            // arm swing animation
            if (!this.entity.getMainHandItem().isEmpty()) {
                this.entity.swing(InteractionHand.MAIN_HAND);
            }

            this.entity.doHurtTarget(this.target);
        }
    }
}
