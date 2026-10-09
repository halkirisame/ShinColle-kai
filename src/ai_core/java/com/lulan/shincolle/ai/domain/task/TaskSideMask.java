package com.lulan.shincolle.ai.domain.task;

/**
 * The task side setting: six faces for each of input, output and fuel (bits 0-17), then whether a match
 * must also agree on metadata (bit 18) and on NBT (bit 20). Other bits are not this version's; they are
 * ignored here and kept in the raw value.
 */
public record TaskSideMask(int raw) {
    public static final int GROUPS = 3;
    public static final int FACES = 6;
    private static final int METADATA_BIT = 18;
    private static final int NBT_BIT = 20;
    private static final int KNOWN = (1 << (GROUPS * FACES)) - 1 | 1 << METADATA_BIT | 1 << NBT_BIT;

    public boolean face(int group, int face) {
        if (group < 0 || group >= GROUPS || face < 0 || face >= FACES) {
            throw new IllegalArgumentException("No such face: group " + group + ", face " + face);
        }
        return (this.raw & 1 << group * FACES + face) != 0;
    }

    public boolean checkMetadata() {
        return (this.raw & 1 << METADATA_BIT) != 0;
    }

    public boolean checkNbt() {
        return (this.raw & 1 << NBT_BIT) != 0;
    }

    public int unknownBits() {
        return this.raw & ~KNOWN;
    }
}
