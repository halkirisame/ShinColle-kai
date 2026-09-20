package com.lulan.shincolle.api.ship;

import net.minecraft.world.entity.player.Player;

/**
 * Read-only public view of a friendly ship's ShinColle ownership.
 *
 * <p>This query uses the server-authoritative ShinColle owner identity. It does
 * not expose that internal identity or permit ownership changes.</p>
 */
public interface PlayerOwnedShip {

    /** Returns whether the supplied player owns this friendly ship. */
    boolean isOwnedByPlayer(Player player);
}
