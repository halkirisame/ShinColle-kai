package com.lulan.shincolle.equipdata;

import com.lulan.shincolle.ShinColle;

/** Holds the display-only equipment snapshot received from the active server. */
public final class ClientEquipData {

    private static volatile EquipDataSnapshot snapshot = EquipDataSnapshot.EMPTY;

    private ClientEquipData() {
    }

    public static EquipDataSnapshot current() {
        return snapshot;
    }

    public static void install(EquipDataSnapshot newSnapshot) {
        snapshot = newSnapshot;
        ShinColle.LOGGER.info("Synchronized {} ship equipment definitions from server",
                newSnapshot.byId().size());
    }

    public static void clear() {
        if (snapshot != EquipDataSnapshot.EMPTY) {
            snapshot = EquipDataSnapshot.EMPTY;
            ShinColle.LOGGER.info("Cleared synchronized ship equipment definitions");
        }
    }
}
