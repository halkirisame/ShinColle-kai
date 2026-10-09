package com.lulan.shincolle.ai.domain.command;

import java.util.Optional;

/** The id a ship keeps for its owner's team lists; always above zero. */
public record ShipUid(int value) {
    public ShipUid {
        if (value <= 0) {
            throw new IllegalArgumentException("Ship uid must be above zero: " + value);
        }
    }

    /** A team slot stores zero or less while it holds no ship. */
    public static Optional<ShipUid> fromLegacy(int raw) {
        return raw <= 0 ? Optional.empty() : Optional.of(new ShipUid(raw));
    }
}
