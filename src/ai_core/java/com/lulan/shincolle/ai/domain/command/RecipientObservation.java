package com.lulan.shincolle.ai.domain.command;

public record RecipientObservation(boolean resolved, boolean owned, boolean sameDimension,
                                   double distanceSqrToSender, boolean noFuel) { }
