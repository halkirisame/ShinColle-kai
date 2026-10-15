package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.FleeMovePlanner;
import com.lulan.shincolle.ai.domain.movement.FleeStay;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Flee goal - activates when HP below threshold.
 * Ported from EntityAIShipFlee (setMutexBits: 7)
 */
public class ShipFleeGoal extends Goal {

    private final BasicEntityShip ship;
    private LivingEntity owner;
    /** The planner's re-path count. */
    private MovementState.Flee move = FleeMovePlanner.start();
    /** NEW: whether the ship is getting anywhere on its way back. */
    private StuckState stuck = StuckState.NONE;

    public ShipFleeGoal(BasicEntityShip ship) {
        this.ship = ship;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        return newShouldWalk(false);
    }

    @Override
    public boolean canContinueToUse() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return false;
        return newShouldWalk(true);
    }

    @Override
    public void start() {
        this.owner = resolveOwner();
        this.move = FleeMovePlanner.start();
        this.stuck = StuckState.NONE;
    }

    @Override
    public void stop() {
        this.owner = null;
        ShipMovementExecutor.run(this.ship, ShipMovementExecutor.GOAL_STOPPED);
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.ship, ActionKind.MOVEMENT)) return;
        this.tickNew();
    }

    /**
     * NEW: re-path to the owner, riding its mount if it rides one; a stuck ship recovers, and it
     * teleports only when no path is found or it stays stuck.
     */
    private void tickNew() {
        boolean alive = this.owner != null && this.owner.isAlive();
        MovementBody body = this.ship.isPassenger() && this.ship.getVehicle() instanceof BasicEntityMount
                ? MovementBody.VEHICLE : MovementBody.SELF;
        Optional<TargetHandle> handle = alive ? Optional.of(ShipCommandStateAdapter.handle(this.owner))
                : Optional.empty();
        boolean sameLevel = alive && this.owner.level() == this.ship.level();
        this.stuck = StuckDetector.observe(this.stuck, FleeMovePlanner.travelling(handle, sameLevel),
                ShipMovementGate.point(this.ship), this.ship.tickCount);
        FleeMovePlanner.Facts facts = alive
                ? new FleeMovePlanner.Facts(handle, body,
                        new MovementPoint(this.owner.getX(), this.owner.getY(), this.owner.getZ()),
                        this.ship.distanceToSqr(this.owner), sameLevel, ConfigHandler.canTeleport(), this.stuck)
                : new FleeMovePlanner.Facts(handle, body,
                        new MovementPoint(this.ship.getX(), this.ship.getY(), this.ship.getZ()), 0D, false, false,
                        this.stuck);
        PlannedMove<MovementState.Flee> planned = FleeMovePlanner.tick(this.move, facts);
        this.move = planned.state();
        ShipMovementExecutor.run(this.ship, planned.plan());
    }

    /** NEW: while fleeing, walk back only when the ship has drifted away from its owner. */
    private boolean newShouldWalk(boolean walking) {
        if (!(ShipMovementGate.intent(this.ship) instanceof MovementIntent.Flee)) return false;
        LivingEntity owner = resolveOwner();
        return owner != null && owner.level() == this.ship.level()
                && FleeStay.shouldWalk(walking, this.ship.distanceToSqr(owner));
    }

    private LivingEntity resolveOwner() {
        int uid = this.ship.getPlayerUID();
        if (uid > 0 && !this.ship.level().isClientSide()) {
            ServerPlayer serverPlayer = ServerDataManager.getPlayerByUID(uid);
            if (serverPlayer != null) {
                return serverPlayer;
            }
        }

        return this.ship.getOwner();
    }
}
