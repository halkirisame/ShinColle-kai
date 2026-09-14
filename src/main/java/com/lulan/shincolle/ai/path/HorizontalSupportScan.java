package com.lulan.shincolle.ai.path;

/** Minecraft-independent horizontal support scan, internal to ship navigation. */
final class HorizontalSupportScan {

    private HorizontalSupportScan() {
    }

    @FunctionalInterface
    interface CellSupport {
        boolean test(int x, int z);
    }

    static boolean hasSupportBetween(double startX, double startZ, double targetX, double targetZ,
                                     CellSupport support) {
        int x = (int) Math.floor(startX);
        int z = (int) Math.floor(startZ);
        int endX = (int) Math.floor(targetX);
        int endZ = (int) Math.floor(targetZ);
        double dx = targetX - startX;
        double dz = targetZ - startZ;
        int stepX = Double.compare(dx, 0D);
        int stepZ = Double.compare(dz, 0D);
        while (true) {
            if (!support.test(x, z)) {
                return false;
            }
            if (x == endX && z == endZ) {
                return true;
            }
            // Recompute from the boundary instead of accumulating rounding error.
            // At an integer endpoint, numerator == denominator gives exactly t = 1.
            double nextX = stepX == 0 ? Double.POSITIVE_INFINITY
                    : (stepX > 0 ? x + 1D - startX : startX - x) / Math.abs(dx);
            double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                    : (stepZ > 0 ? z + 1D - startZ : startZ - z) / Math.abs(dz);
            if (Math.min(nextX, nextZ) >= 1D) {
                // The closed endpoint still needs support; never step beyond the segment.
                return support.test(endX, endZ);
            }
            if (nextX < nextZ) {
                x += stepX;
            } else {
                z += stepZ;
            }
        }
    }
}
