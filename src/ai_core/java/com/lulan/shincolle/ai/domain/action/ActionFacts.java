package com.lulan.shincolle.ai.domain.action;

/**
 * @param playerControlled a player steers this body (a mount with a player aboard); its own
 *                         pathing would fight the player's input
 */
public record ActionFacts(boolean noFuel, boolean dead, boolean playerControlled) {
}
