package com.lulan.shincolle.ai.domain.movement;

/**
 * The teleport settings of following and guarding.
 *
 * @param enabled    teleporting is allowed at all
 * @param cooldown   goal ticks a timer must exceed before the ship teleports
 * @param distanceSq squared distance beyond which the far timer counts
 */
public record TeleportRule(boolean enabled, int cooldown, int distanceSq) {
}
