package com.lulan.shincolle.client.particle;

/** Movement-specific trail timing, independent of particle and world access. */
final class MissileTrailVisibility {
    private MissileTrailVisibility() {
    }

    static boolean allowsEmission(int movementType, int tickCount, boolean enteredLiquid) {
        return movementType == 2 ? enteredLiquid : tickCount > 2;
    }
}
