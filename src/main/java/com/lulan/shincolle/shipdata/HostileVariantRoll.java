package com.lulan.shincolle.shipdata;

import java.util.function.IntUnaryOperator;

/** One bounded random draw, with the normal slot first. */
public final class HostileVariantRoll {
    private HostileVariantRoll() {
    }

    public static HostileVariants.Variant select(HostileVariants variants, IntUnaryOperator random) {
        int total = variants.totalWeight();
        if (variants == HostileVariants.NONE || total == 0) return null;
        int roll = random.applyAsInt(total) - variants.normalWeight();
        if (roll < 0) return null;
        for (var variant : variants.variants()) {
            roll -= variant.weight();
            if (roll < 0) return variant;
        }
        throw new IllegalArgumentException("Random draw outside hostile variant weights");
    }
}
