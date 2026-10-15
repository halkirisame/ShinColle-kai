package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Melee attack on collide goal.
 * Ported from EntityAIShipAttackOnCollide (setMutexBits: 4)
 * <p>
 * This goal moves the ship; the fire control goal strikes on the ship's one timer.
 * Melee range is width^2 * 16 (matching original formula).
 * Pathfinds every 32 ticks, only when out of melee range.
 */
public class ShipAttackOnCollideGoal extends Goal {

    private final IShipAttackBase host;
    private final Mob entity;
    private final double speed;
    private Entity target;
    private int nextRepathTick;
    private final ShipCombatMover combatMover = new ShipCombatMover();

    public ShipAttackOnCollideGoal(IShipAttackBase host, double speed) {
        this.host = host;
        this.entity = (Mob) host;
        this.speed = speed;
        // Original setMutexBits(4): jump only.
        this.setFlags(EnumSet.of(Goal.Flag.JUMP));
        ShipCombatGate.carry(host, WeaponChannel.MELEE);
    }

    @Override
    public boolean canUse() {
        if (!ShipCombatGate.active(this.entity)) return false;
        // Whether the ship may fire and at what comes from the combat gate. Riding
        // anything stops only melee, so it stays here; the crane does not stop melee.
        if (meleeOff() || this.entity.isPassenger()) return false;
        ShipCombatGate.Engagement engagement = ShipCombatGate.engagement(this.entity);
        this.target = engagement.target();
        return engagement.engaged();
    }

    /** The goal stays registered, so it follows the melee flag itself. */
    private boolean meleeOff() {
        return this.host != null && !this.host.getStateFlag(ID.F.UseMelee);
    }

    @Override
    public void start() {
        this.nextRepathTick = this.entity.tickCount;
        this.combatMover.reset();
    }

    @Override
    public boolean canContinueToUse() {
        // follow the lock at once, even mid-path
        return this.canUse();
    }

    @Override
    public void stop() {
        this.target = null;
        ShipMovementExecutor.run(this.entity, ShipMovementExecutor.GOAL_STOPPED);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!ShipCombatGate.active(this.entity)) return;
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

        // every 32 ticks: pathfind conditionally
        int now = this.entity.tickCount;
        boolean due = now >= this.nextRepathTick;
        this.combatMover.apply(this.entity, this.target, distTarget <= distAttack, this.speed, due, false);
        if (due) this.nextRepathTick = now + 32;
    }
}
