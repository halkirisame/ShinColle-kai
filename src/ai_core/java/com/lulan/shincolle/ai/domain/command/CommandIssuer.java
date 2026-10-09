package com.lulan.shincolle.ai.domain.command;

import java.util.UUID;

public sealed interface CommandIssuer {
    record Player(UUID player) implements CommandIssuer { }
    record Waypoint() implements CommandIssuer { }
    record Ship(ShipTransitionReason reason) implements CommandIssuer { }
}
