package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.entity.BasicEntityShip;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Sit goal - locks ship in place when commanded.
 * Ported from EntityAIShipSit (setMutexBits: 7)
 */
public class ShipSitGoal extends Goal {

    private static final MovementPlan SITTING = MovementPlan.of(
            new MovementStep.Stop(MovementBody.SELF, MovementReason.SIT));

    private final BasicEntityShip ship;

    public ShipSitGoal(BasicEntityShip ship) {
        this.ship = ship;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        if (ShipMovementGate.active()) return ShipMovementGate.intent(this.ship) instanceof MovementIntent.Sit;
        return this.ship.isOrderedToSit();
    }

    @Override
    public void start() {
        if (!ShipCommandStateAdapter.isNew()) this.ship.setEntitySit(true);
        this.ship.setJumping(false);
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return;
        if (ShipMovementGate.active()) {
            ShipMovementExecutor.run(this.ship, SITTING);
        } else {
            this.ship.getNavigation().stop();
        }
        if (!ShipCommandStateAdapter.isNew()) this.ship.setManualTarget(null);
        this.ship.setTarget(null);
        this.ship.setEntityTarget(null);
    }

    @Override
    public void stop() {
        if (!ShipCommandStateAdapter.isNew()) this.ship.setEntitySit(false);
    }
}
