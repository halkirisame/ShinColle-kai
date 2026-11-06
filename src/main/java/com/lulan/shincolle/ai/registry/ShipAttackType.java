package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.ai.domain.combat.WeaponChannel;

import java.util.Set;

/** Stateless installer and metadata for an entry of the internal ship attack registry. */
public interface ShipAttackType {
    Set<WeaponChannel> channels();

    boolean selectableByDefinition();

    void install(GenericShipHost host);
}
