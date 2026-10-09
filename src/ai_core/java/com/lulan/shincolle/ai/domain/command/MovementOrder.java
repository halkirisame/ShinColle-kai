package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.Optional;

public sealed interface MovementOrder {
    /** The block a move or a position guard is for; the other orders name none. */
    default Optional<CommandPos> destination() {
        if (this instanceof MoveTo move) return Optional.of(move.position());
        if (this instanceof GuardPosition guard) return Optional.of(guard.position());
        return Optional.empty();
    }

    record Follow() implements MovementOrder { }
    record MoveTo(DimensionKey dimension, CommandPos position, boolean releaseOnArrival)
            implements MovementOrder { }
    record GuardPosition(DimensionKey dimension, CommandPos position, boolean releaseOnArrival)
            implements MovementOrder { }
    record GuardEntity(TargetHandle target) implements MovementOrder { }
}
