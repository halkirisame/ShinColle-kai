package com.lulan.shincolle.crafting;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;

/**
 * Applies the configured yield multiplier to shipyard resources.
 */
public final class ResourceYieldPolicy {

    public static final int EASY_MODE_MULTIPLIER = 10;

    private ResourceYieldPolicy() {
    }

    /**
     * Apply the current EasyMode resource yield rule once.
     */
    public static ResourceAmount apply(ResourceAmount base) {
        int multiplier = ConfigHandler.easyMode() ? EASY_MODE_MULTIPLIER : 1;
        return applyMultiplier(base, multiplier);
    }

    /**
     * Apply an explicit multiplier. Public for deterministic GameTest coverage.
     */
    public static ResourceAmount applyMultiplier(ResourceAmount base, int multiplier) {
        if (multiplier == 1) {
            return base;
        }
        return base.times(multiplier);
    }
}
