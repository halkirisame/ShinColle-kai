package com.lulan.shincolle.ai.registry;

/**
 * Element of the internal ship AI registry. Creates the per-ship running instance of one AI;
 * mutable state is never shared between ships.
 */
public interface ShipAiType {
    void install(GenericShipHost host);
}
