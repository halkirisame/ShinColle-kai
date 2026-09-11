package com.lulan.shincolle.entity;

/** Immutable ship level-cap values synchronized from the authoritative server. */
public record ShipLevelCapSummary(int unmarriedCap, int absoluteCap) {

    public static final ShipLevelCapSummary DEFAULT = new ShipLevelCapSummary(
            ShipLevelRules.DEFAULT_UNMARRIED_CAP,
            ShipLevelRules.DEFAULT_ABSOLUTE_CAP);

    public ShipLevelCapSummary {
        if (unmarriedCap < 1 || absoluteCap < 1) {
            throw new IllegalArgumentException("Ship level caps must both be positive");
        }
    }

    public int effectiveUnmarriedCap() {
        return Math.min(unmarriedCap, absoluteCap);
    }

    public boolean isAbsoluteCapReached(int level) {
        return level >= absoluteCap;
    }
}
