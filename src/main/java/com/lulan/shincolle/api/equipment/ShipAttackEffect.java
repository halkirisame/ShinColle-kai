package com.lulan.shincolle.api.equipment;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable description of one MobEffect applied after a ship attack hits.
 *
 * @param effectId      registered MobEffect identifier
 * @param amplifier     zero-based effect amplifier
 * @param durationTicks duration in game ticks
 * @param chancePercent application chance from 0 through 100
 */
public record ShipAttackEffect(ResourceLocation effectId, int amplifier, int durationTicks,
                               int chancePercent) {

    public static final int MAX_AMPLIFIER = 255;
    public static final int MAX_DURATION_TICKS = 1_000_000;

    public ShipAttackEffect {
        Objects.requireNonNull(effectId, "effectId");
        if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
            throw new IllegalArgumentException("amplifier must be between 0 and " + MAX_AMPLIFIER);
        }
        if (durationTicks < 0 || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("durationTicks must be between 0 and " + MAX_DURATION_TICKS);
        }
        if (chancePercent < 0 || chancePercent > 100) {
            throw new IllegalArgumentException("chancePercent must be between 0 and 100");
        }
    }
}
