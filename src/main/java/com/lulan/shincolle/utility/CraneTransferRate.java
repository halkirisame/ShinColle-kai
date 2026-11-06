package com.lulan.shincolle.utility;

/** Shared per-cycle pump rate, with no world or inventory side effects. */
public final class CraneTransferRate {
    private CraneTransferRate() { }

    public static int perCycle(int level, int pumps, int enchantments, int base, int additional) {
        if (pumps <= 0) return 0;
        double rate = ((double) Math.max(0, base) * pumps + (double) Math.max(0, additional) * Math.max(0, enchantments))
                * 16D * (Math.max(0, level) * 0.1D + 1D);
        return (int) Math.min(Integer.MAX_VALUE, rate);
    }
}
