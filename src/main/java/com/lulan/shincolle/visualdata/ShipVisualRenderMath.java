package com.lulan.shincolle.visualdata;

/** Drawing-only arithmetic. Entity dimensions and synchronized state are not changed here. */
public final class ShipVisualRenderMath {
    private ShipVisualRenderMath() { }

    public static int scaleLevel(int level) {
        return Math.max(0, Math.min(3, level));
    }

    public static float scale(float base, int level) {
        return base * (1 + scaleLevel(level));
    }

    public static float shadow(float base, int level) {
        return base + scaleLevel(level) * 0.4F;
    }

    public static boolean distant(double distanceSquared, float distance) {
        return distanceSquared > (double) distance * distance;
    }
}
