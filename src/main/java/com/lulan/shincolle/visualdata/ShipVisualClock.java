package com.lulan.shincolle.visualdata;

/**
 * Animation clock, independent of Minecraft client state.
 *
 * <p>GeckoLib adds the frame's partial tick to the animation time only for entity animatables. A ship's
 * animatable is not an entity, so the time it reports is used as is. Reporting a whole tick count makes the
 * animation advance in 20 Hz steps; this adds the sub-tick fraction so the time advances every frame.</p>
 */
public final class ShipVisualClock {
    private ShipVisualClock() { }

    /**
     * @param tick        whole ticks of the entity
     * @param partialTick fraction of the current tick that has elapsed, clamped into [0, 1]
     * @return the continuous time in ticks
     */
    public static double continuousTick(long tick, float partialTick) {
        return tick + Math.min(1.0F, Math.max(0.0F, partialTick));
    }
}
