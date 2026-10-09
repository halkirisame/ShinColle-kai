package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementDecision;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Watch closest entity goal.
 * Ported from EntityAIShipWatchClosest (setMutexBits: 0)
 */
public class ShipWatchClosestGoal extends Goal {

    private final Mob entity;
    private final Class<? extends LivingEntity> watchedClass;
    private final float maxDistance;
    private final float chance;
    private Entity closestEntity;
    private int lookTime;

    public ShipWatchClosestGoal(Mob entity, Class<? extends LivingEntity> watchedClass, float maxDistance, float chance) {
        this.entity = entity;
        this.watchedClass = watchedClass;
        this.maxDistance = maxDistance;
        this.chance = chance;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.entity, ActionKind.MOVEMENT)) return false;
        if (ShipMovementGate.active() && ShipMovementGate.decision(this.entity) != null) {
            if (!ShipMovementGate.allows(this.entity, MovementActivity.IDLE_LOOK)) {
                return false;
            }
        } else if (this.entity instanceof IShipAttackBase ship) {
            if (ship.getStateFlag(ID.F.NoFuel)
                    || this.entity.getVehicle() instanceof BasicEntityShip) {
                return false;
            }
        }

        Player nearestPlayer = this.entity.level().getNearestPlayer(this.entity, this.maxDistance);
        if (nearestPlayer != null && (nearestPlayer.isPassenger() || nearestPlayer.isInvisible())) {
            return false;
        }

        if (this.entity.getRandom().nextFloat() >= this.chance) {
            return false;
        }

        TargetingConditions conditions = TargetingConditions.forNonCombat().range(this.maxDistance);
        if (this.watchedClass == Player.class) {
            this.closestEntity = this.entity.level().getNearestPlayer(
                    conditions, this.entity,
                    this.entity.getX(), this.entity.getEyeY(), this.entity.getZ());
        } else {
            this.closestEntity = this.entity.level().getNearestEntity(
                    this.entity.level().getEntitiesOfClass(this.watchedClass,
                            this.entity.getBoundingBox().inflate(this.maxDistance, 3.0D, this.maxDistance)),
                    conditions, this.entity,
                    this.entity.getX(), this.entity.getEyeY(), this.entity.getZ());
        }

        return this.closestEntity != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (ShipActionGate.blocked(this.entity, ActionKind.MOVEMENT)) return false;
        if (this.idleLookForbidden()) return false;
        if (this.closestEntity == null || !this.closestEntity.isAlive()) return false;
        if (this.entity.distanceToSqr(this.closestEntity) > (double) (this.maxDistance * this.maxDistance))
            return false;
        return this.lookTime > 0;
    }

    @Override
    public void start() {
        this.lookTime = 40 + this.entity.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        this.closestEntity = null;
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.entity, ActionKind.MOVEMENT)) return;
        if (this.idleLookForbidden()) return;
        if (this.closestEntity != null && this.closestEntity.isAlive()) {
            this.entity.getLookControl().setLookAt(
                    this.closestEntity.getX(),
                    this.closestEntity.getEyeY(),
                    this.closestEntity.getZ());
            --this.lookTime;
        }
    }

    /** NEW: an engaged ship keeps its head on its target, so it must not look around (LEGACY has no decision). */
    private boolean idleLookForbidden() {
        MovementDecision decision = ShipMovementGate.active() ? ShipMovementGate.decision(this.entity) : null;
        return decision != null && !decision.allows(MovementActivity.IDLE_LOOK);
    }
}
