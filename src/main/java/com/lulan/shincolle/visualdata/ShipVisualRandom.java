package com.lulan.shincolle.visualdata;

/** Stateless, independently salted presentation samples; never consumes an entity's random source. */
public final class ShipVisualRandom {
    private ShipVisualRandom() { }

    public static int sample(int shipUid, long cycle, long salt, int bound) {
        long value = cycle * 0x9E3779B97F4A7C15L ^ Integer.toUnsignedLong(shipUid) * 0xD1B54A32D192ED03L ^ salt;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return (int) Long.remainderUnsigned(value ^ (value >>> 31), bound);
    }
}
