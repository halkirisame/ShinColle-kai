package com.lulan.shincolle.equipdata;

/**
 * Explicit access to the authoritative server snapshot and the separately synchronized client snapshot.
 */
public final class EquipDataRegistry {

    private EquipDataRegistry() {
    }

    /** Game-logic authority loaded from server datapacks. */
    public static EquipDataSnapshot server() {
        return EquipDataLoader.currentServerSnapshot();
    }

    /** Display-only copy. It remains empty until the S2C synchronization layer installs a snapshot. */
    public static EquipDataSnapshot client() {
        return ClientEquipData.current();
    }
}
