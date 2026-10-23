package com.lulan.shincolle.ai.domain;

/**
 * Decides whether a friendly ship may take a player as its revenge target.
 *
 * <p>Retaliation against a player is opt-in: the ship's own permission or the
 * permission of any fleet it belongs to is enough. Targets that are not players
 * are never restricted by this rule.</p>
 */
public final class PlayerRetaliationRule {

    private PlayerRetaliationRule() {
    }

    public static boolean allows(boolean targetIsPlayer, boolean shipPermitted, boolean fleetPermitted) {
        return !targetIsPlayer || shipPermitted || fleetPermitted;
    }
}
