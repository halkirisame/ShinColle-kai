package com.lulan.shincolle.ai.domain.command;

import java.util.UUID;

public record AcceptedShipCommand(UUID issuer, long acceptedAtTick, long serverSequence,
                                  ShipCommand command) { }
