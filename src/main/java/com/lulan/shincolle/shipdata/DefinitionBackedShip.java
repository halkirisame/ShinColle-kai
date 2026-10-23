package com.lulan.shincolle.shipdata;

/** Identity boundary shared by the two data-defined ship entity types. */
public interface DefinitionBackedShip {
    GenericShipState definitionState();

    default String getShipId() {
        return definitionState().shipId();
    }
}
