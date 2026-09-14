package com.lulan.shincolle.client;

import net.minecraftforge.fml.VersionChecker;

/** Converts Forge version-check states to the notification policy input. */
final class UpdateStatusAdapter {

    private UpdateStatusAdapter() {
    }

    /** Returns true only for the two Forge states that draw an outdated badge. */
    static boolean isNotificationTarget(VersionChecker.Status status) {
        return status == VersionChecker.Status.OUTDATED || status == VersionChecker.Status.BETA_OUTDATED;
    }
}
