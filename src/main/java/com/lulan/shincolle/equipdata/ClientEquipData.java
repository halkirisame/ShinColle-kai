package com.lulan.shincolle.equipdata;

import com.lulan.shincolle.ShinColle;

import java.util.concurrent.CopyOnWriteArrayList;

/** Holds the display-only equipment snapshot received from the active server. */
public final class ClientEquipData {

    private static volatile EquipDataSnapshot snapshot = EquipDataSnapshot.EMPTY;
    private static final CopyOnWriteArrayList<Runnable> INSTALL_LISTENERS = new CopyOnWriteArrayList<>();

    private ClientEquipData() {
    }

    public static EquipDataSnapshot current() {
        return snapshot;
    }

    public static void install(EquipDataSnapshot newSnapshot) {
        snapshot = newSnapshot;
        ShinColle.LOGGER.info("Synchronized {} ship equipment definitions from server",
                newSnapshot.byId().size());
        notifyInstallListeners();
    }

    private static void notifyInstallListeners() {
        for (Runnable listener : INSTALL_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                ShinColle.LOGGER.warn("Equipment client snapshot listener failed: {}", exception.toString());
            }
        }
    }

    public static void addInstallListener(Runnable listener) {
        INSTALL_LISTENERS.addIfAbsent(listener);
    }

    public static void removeInstallListener(Runnable listener) {
        INSTALL_LISTENERS.remove(listener);
    }

    public static void clear() {
        if (snapshot == EquipDataSnapshot.EMPTY) {
            return;
        }

        snapshot = EquipDataSnapshot.EMPTY;
        ShinColle.LOGGER.info("Cleared synchronized ship equipment definitions");
        notifyInstallListeners();
    }
}
