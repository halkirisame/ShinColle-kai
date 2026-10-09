package com.lulan.shincolle.ai.domain.movement;

/**
 * The settings of a ship that the movement decisions read, as they stood when they were read.
 *
 * @param formation the ship moves in a formation
 * @param followMin the distance inside which following and guarding stop
 * @param followMax the distance beyond which they start
 * @param pickItem  the ship may leave its place to pick items up
 */
public record MovementSettings(boolean formation, int followMin, int followMax, boolean pickItem) {
    /** What a host that carries no ship answers. */
    public static final MovementSettings NONE = new MovementSettings(false, 0, 0, false);
}
