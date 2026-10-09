package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;

public sealed interface ShipCommand {
    record Attack(TargetHandle target) implements ShipCommand { }
    record CancelAttack() implements ShipCommand { }
    record GuardEntity(TargetHandle target) implements ShipCommand { }
    record Move(DimensionKey dimension, CommandPos position,
                boolean releaseOnArrival) implements ShipCommand { }
    record GuardPosition(DimensionKey dimension, CommandPos position,
                         boolean releaseOnArrival) implements ShipCommand { }
    record FormationMove(DimensionKey dimension, CommandPos position,
                         int formationId) implements ShipCommand { }
    record Follow() implements ShipCommand { }
    record SetSitting(boolean sitting) implements ShipCommand { }
}
