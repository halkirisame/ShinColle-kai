package com.lulan.shincolle.ai.domain.command;

public record CommandStateChange(CommandIssuer issuer, long tick, long serverSequence,
                                 ShipCommandState before, ShipCommandState after) { }
