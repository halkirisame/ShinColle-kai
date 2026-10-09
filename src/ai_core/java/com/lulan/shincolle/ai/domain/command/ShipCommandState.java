package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.TargetHandle;
import java.util.Optional;

public record ShipCommandState(MovementOrder movement, boolean sitting, Optional<TargetHandle> manualAttack) {
    public static final ShipCommandState INITIAL = new ShipCommandState(new MovementOrder.Follow(), false,
            Optional.empty());
}
