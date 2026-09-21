package com.lulan.shincolle.handler;

/** Pure client-side rules shared by ship voice and timekeeping settings. */
public final class ShipSoundSettings {

    private ShipSoundSettings() {
    }

    /**
     * Returns the configured volume while the sound group is enabled, or silence
     * while it is disabled.
     */
    public static float effectiveVolume(boolean enabled, double configuredVolume, float incomingVolume) {
        return enabled ? (float) (configuredVolume * incomingVolume) : 0.0F;
    }

    public static int toPercent(double configuredVolume) {
        return Math.max(0, Math.min(100, (int) Math.round(configuredVolume * 100.0D)));
    }

    public static double fromPercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Sound volume percent out of range: " + percent);
        }
        return percent / 100.0D;
    }
}
