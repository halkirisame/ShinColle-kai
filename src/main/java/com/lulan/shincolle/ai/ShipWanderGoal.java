package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Random wander goal.
 * Ported from EntityAIShipWander (setMutexBits: 7)
 */
public class ShipWanderGoal extends Goal {

    private final BasicEntityShip ship;
    private final int rangeXZ;
    private final int rangeY;
    private final double speed;
    private double targetX, targetY, targetZ;

    public ShipWanderGoal(BasicEntityShip ship, int rangeXZ, int rangeY, double speed) {
        this.ship = ship;
        this.rangeXZ = rangeXZ;
        this.rangeY = rangeY;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        if (ShipMovementGate.active()) {
            if (!ShipMovementGate.allows(this.ship, MovementActivity.WANDER))
                return false;
        } else {
            if (this.ship.isOrderedToSit())
                return false;
            if (this.ship.isPassenger())
                return false;
            if (this.ship.fishHook != null || ShipMovementGate.craneBusy(this.ship)
                    || this.ship.getStateFlag(ID.F.NoFuel))
                return false;
        }
        if (this.ship.getRandom().nextInt(180) != 0) {
            return false;
        }
        Vec3 target = DefaultRandomPos.getPos(this.ship, this.rangeXZ, this.rangeY);
        if (target == null) {
            return false;
        }
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        return !ship.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (ShipMovementGate.active()) {
            // the point is drawn in canUse, from the entity random, as before
            ShipMovementExecutor.run(this.ship, MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF,
                    new MovementTarget.Point(new MovementPoint(this.targetX, this.targetY, this.targetZ)), this.speed,
                    MovementReason.WANDER)));
            return;
        }
        this.ship.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }
}
