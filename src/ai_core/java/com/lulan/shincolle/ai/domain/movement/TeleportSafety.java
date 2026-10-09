package com.lulan.shincolle.ai.domain.movement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The one rule every teleport under NEW passes: the same dimension, not too soon after the last,
 * into a loaded chunk inside the world border, and onto a spot with room for the body. No single
 * condition teleports on its own; the planners decide when to try and this decides whether it may.
 */
public final class TeleportSafety {
    /** Entity ticks a body waits between teleports. */
    public static final int COOLDOWN = 100;
    /** How far, horizontally, the landing may move from the destination to find room. */
    static final int SEARCH_RADIUS = 2;

    private TeleportSafety() { }

    /**
     * @param sameDimension the destination is in the body's dimension
     * @param sinceLast     entity ticks since the host's last teleport, {@link Integer#MAX_VALUE} if it never did
     * @param chunkLoaded   the destination's chunk is loaded
     * @param insideBorder  the destination is inside the world border
     * @param freeLanding   the destination or a point beside it has room for the body; true while
     *                      it has not been looked for, which happens only once the rest allow it
     */
    public record Facts(boolean sameDimension, int sinceLast, boolean chunkLoaded, boolean insideBorder,
                        boolean freeLanding) {
    }

    /** Every reason the teleport may not go ahead; empty means it may. */
    public static Set<TeleportDenial> denials(Facts facts) {
        return denials(facts, MovementReason.COMMAND_APPLIED);
    }

    /** Void rescue and validated built-in skill movement may bypass the ordinary interval. */
    public static Set<TeleportDenial> denials(Facts facts, MovementReason reason) {
        EnumSet<TeleportDenial> denied = EnumSet.noneOf(TeleportDenial.class);
        if (!facts.sameDimension()) denied.add(TeleportDenial.OTHER_DIMENSION);
        if (facts.sinceLast() < COOLDOWN && reason != MovementReason.VOID_RESCUE
                && reason != MovementReason.SKILL_ATTACK) denied.add(TeleportDenial.COOLDOWN);
        if (!facts.chunkLoaded()) denied.add(TeleportDenial.CHUNK_NOT_LOADED);
        if (!facts.insideBorder()) denied.add(TeleportDenial.OUTSIDE_WORLD_BORDER);
        if (!facts.freeLanding()) denied.add(TeleportDenial.NO_FREE_SPACE);
        return denied;
    }

    /**
     * The spots tried for room, in order: the destination, then the points one and two blocks
     * away horizontally, nearest first and then by x and z; each at its own height and one block up.
     */
    public static List<MovementPoint> landings(MovementPoint destination) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) offsets.add(new int[]{dx, dz});
        }
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
                .thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
        List<MovementPoint> out = new ArrayList<>();
        for (int[] offset : offsets) {
            for (int dy = 0; dy <= 1; dy++) {
                out.add(new MovementPoint(destination.x() + offset[0], destination.y() + dy,
                        destination.z() + offset[1]));
            }
        }
        return out;
    }
}
