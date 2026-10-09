package com.lulan.shincolle.ai.domain.command;

import java.util.Optional;

/**
 * One slot of a team as the dispatcher saw it.
 *
 * @param ship the ship in the slot, empty while the slot holds none
 */
public record SlotObservation(int slot, Optional<ShipUid> ship, boolean selected, boolean known, boolean sunk) { }
