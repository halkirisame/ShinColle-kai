package com.lulan.shincolle.ai.domain.combat;

/**
 * Attributes of the heavy bombardment fortress, derived once at launch from the launching ship's cannon channels.
 * The aircraft channels of the ship take no part: the fortress fires like a cannon, not like an airplane.
 */
public record FortAttributeProfile(float health, float lightDamage, float lightAirDamage, float heavyDamage,
                                   float heavyAirDamage, float defense, float attackSpeed, float moveSpeed,
                                   float hit, float dodge, double followRange, double knockbackResistance) {
    /**
     * @param cannonLight the ship's light cannon damage as the ship itself reports it
     * @param cannonHeavy the ship's heavy cannon damage as the ship itself reports it, class factor included
     */
    public record Host(float maxHealth, float cannonLight, float cannonHeavy, float defense, float attackSpeed,
                       float moveSpeed, float dodge) { }

    public static FortAttributeProfile of(Host host) {
        return new FortAttributeProfile(host.maxHealth() * .1F, host.cannonLight(), host.cannonLight(),
                host.cannonHeavy(), host.cannonHeavy(), host.defense() * .5F, host.attackSpeed(),
                host.moveSpeed() * .1F + .3F, 6F, host.dodge() + .3F, 64D, .5D);
    }
}
