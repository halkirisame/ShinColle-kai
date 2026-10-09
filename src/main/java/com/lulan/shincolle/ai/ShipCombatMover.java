package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.CombatMove;
import com.lulan.shincolle.ai.domain.movement.CombatMovePlanner;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Carries out the composed combat move for an attack goal under NEW, through the movement executor.
 * Its state, the last reason and whether the way in is getting anywhere, belongs to the attack goal
 * and starts over with it.
 */
final class ShipCombatMover {
    private MovementState.Combat state = MovementState.Combat.NONE;
    private StuckState stuck = StuckState.NONE;

    void reset() {
        this.state = MovementState.Combat.NONE;
        this.stuck = StuckState.NONE;
    }

    /**
     * @param due           the goal's own re-path time has come
     * @param stopEveryTick a hold stops the navigation on every tick, not only when due
     * @return whether a path was requested
     */
    boolean apply(Mob entity, Entity target, boolean holdForFire, double speed, boolean due,
                  boolean stopEveryTick) {
        CombatMove move = ShipMovementGate.combatMove(entity, target, holdForFire);
        // a move of another kind is another way in, measured afresh
        if (this.state.last().isPresent() && this.state.last().get() != move.reason()) this.stuck = StuckState.NONE;
        this.stuck = StuckDetector.observe(StuckDetector.retryAfterGivingUp(this.stuck),
                CombatMovePlanner.travelling(move), ShipMovementGate.point(entity), entity.tickCount);
        PlannedMove<MovementState.Combat> planned = CombatMovePlanner.plan(this.state, new CombatMovePlanner.Facts(
                move, ShipCommandStateAdapter.handle(target), ShipMovementGate.point(target), speed, due,
                stopEveryTick, this.stuck));
        this.state = planned.state();
        ShipMovementExecutor.run(entity, planned.plan(), move.reason());
        return planned.plan().hasPath();
    }
}
