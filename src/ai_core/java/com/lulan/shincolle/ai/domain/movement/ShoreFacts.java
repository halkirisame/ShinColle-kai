package com.lulan.shincolle.ai.domain.movement;

import java.util.OptionalDouble;

/** One body's observations for a single movement tick. */
public record ShoreFacts(boolean moving, boolean flying, boolean playerControlled, boolean onGround,
                         boolean afloat, double waterTopY, boolean horizontalCollision,
                         OptionalDouble ledgeTopY, boolean headroomClear, double wantedY, double maxUpStep,
                         long now, long targetKey) {
}
