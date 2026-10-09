package com.lulan.shincolle.ai.domain.command;

import java.util.Optional;

public final class CommandStateReducer {
    private CommandStateReducer() { }

    public static ShipCommandState apply(ShipCommandState state, CommandStateOp op) {
        if (op instanceof CommandStateOp.EndMovement) {
            return new ShipCommandState(new MovementOrder.Follow(), state.sitting(), state.manualAttack());
        }
        if (op instanceof CommandStateOp.StandUp) {
            return new ShipCommandState(state.movement(), false, state.manualAttack());
        }
        if (op instanceof CommandStateOp.ClearManualAttack) {
            return new ShipCommandState(state.movement(), state.sitting(), Optional.empty());
        }
        ShipCommand command = ((CommandStateOp.Apply) op).command();
        if (command instanceof ShipCommand.Attack attack) {
            return new ShipCommandState(state.movement(), false, Optional.of(attack.target()));
        }
        if (command instanceof ShipCommand.CancelAttack) {
            return new ShipCommandState(state.movement(), false, Optional.empty());
        }
        if (command instanceof ShipCommand.GuardEntity guard) {
            return new ShipCommandState(new MovementOrder.GuardEntity(guard.target()), false, state.manualAttack());
        }
        if (command instanceof ShipCommand.Move move) {
            return new ShipCommandState(new MovementOrder.MoveTo(move.dimension(), move.position(),
                    move.releaseOnArrival()), false, state.manualAttack());
        }
        if (command instanceof ShipCommand.GuardPosition guard) {
            return new ShipCommandState(new MovementOrder.GuardPosition(guard.dimension(), guard.position(),
                    guard.releaseOnArrival()), false, state.manualAttack());
        }
        if (command instanceof ShipCommand.Follow) {
            return new ShipCommandState(new MovementOrder.Follow(), state.sitting(), state.manualAttack());
        }
        if (command instanceof ShipCommand.SetSitting sit) {
            return new ShipCommandState(state.movement(), sit.sitting(),
                    sit.sitting() ? Optional.empty() : state.manualAttack());
        }
        throw new IllegalArgumentException("Formation movement needs per-ship positions");
    }
}
