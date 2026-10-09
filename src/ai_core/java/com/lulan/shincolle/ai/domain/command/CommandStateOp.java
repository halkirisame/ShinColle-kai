package com.lulan.shincolle.ai.domain.command;

public sealed interface CommandStateOp {
    record Apply(ShipCommand command) implements CommandStateOp { }
    record EndMovement() implements CommandStateOp { }
    record StandUp() implements CommandStateOp { }
    record ClearManualAttack() implements CommandStateOp { }
}
