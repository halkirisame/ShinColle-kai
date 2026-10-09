package com.lulan.shincolle.ai.domain.command;

import java.util.Optional;

public final class RecipientValidator {
    private RecipientValidator() { }

    public static Optional<CommandRejectReason> validate(CommandKind kind, RecipientObservation observation) {
        if (!observation.resolved()) return Optional.of(CommandRejectReason.NOT_FOUND);
        if (!observation.owned()) return Optional.of(CommandRejectReason.NOT_OWNED);
        if (!observation.sameDimension()) return Optional.of(CommandRejectReason.OTHER_DIMENSION);
        if (kind != CommandKind.TOGGLE_SIT) {
            if (observation.distanceSqrToSender() > 4096D) return Optional.of(CommandRejectReason.OUT_OF_RANGE);
            if (observation.noFuel()) return Optional.of(CommandRejectReason.NO_FUEL);
        }
        return Optional.empty();
    }
}
