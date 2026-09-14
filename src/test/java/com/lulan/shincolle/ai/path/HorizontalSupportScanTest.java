package com.lulan.shincolle.ai.path;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalSupportScanTest {

    @Test
    void reportedMountCornerAndShipCenterTerminateWithinSegment() {
        assertEquals(21, checkSupported(4.3, 7.8, 13, -4));
        assertEquals(21, checkSupported(4.3, 7.8, 12.5, -4.5));
    }

    @Test
    void bothWidthsTerminateInSameAndOppositeQuadrants() {
        for (float width : new float[]{1.9F, 0.7F}) {
            double offset = (int) (width + 1.0F) * 0.5D;
            for (int xSign : new int[]{-1, 1}) {
                for (int zSign : new int[]{-1, 1}) {
                    checkSupported(4.3, 7.8, xSign * 12 + offset, zSign * 15 + offset);
                    checkSupported(-4.3, -7.8, xSign * 12 + offset, zSign * 15 + offset);
                }
            }
        }
    }

    @Test
    void bothWidthsTerminateOnParallelAxesAndIntegerStarts() {
        for (float width : new float[]{1.9F, 0.7F}) {
            double offset = (int) (width + 1.0F) * 0.5D;
            for (int sign : new int[]{-1, 1}) {
                checkSupported(4 + offset, 7 + offset, sign * 12 + offset, 7 + offset);
                checkSupported(4 + offset, 7 + offset, 4 + offset, sign * 12 + offset);
            }
        }
    }

    @Test
    void bothWidthsCheckSameCellAndZeroLengthOnce() {
        for (float width : new float[]{1.9F, 0.7F}) {
            double offset = (int) (width + 1.0F) * 0.5D;
            assertEquals(1, checkSupported(offset + 0.1, offset + 0.2, offset, offset));
            assertEquals(1, checkSupported(offset, offset, offset, offset));
        }
    }

    @Test
    void missingSupportAtStartInteriorOrCornerEndpointRejectsShortcut() {
        for (int[] hole : new int[][]{{4, 7}, {8, 2}, {13, -4}}) {
            int[] visits = {0};
            assertFalse(HorizontalSupportScan.hasSupportBetween(4.3, 7.8, 13, -4, (x, z) -> {
                assertTrue(++visits[0] <= 22, "Scan must remain bounded even when support is missing");
                return x != hole[0] || z != hole[1];
            }));
        }
    }

    @Test
    void cornerDoesNotQueryCellsBeyondEndpoint() {
        checkSupported(4.3, 7.8, 13, -4);
        checkSupported(13, -4, 4.3, 7.8);
        checkSupported(0.5, 0.5, 4, -3);
        checkSupported(0.5, 0.5, -3, 4);
    }

    @Test
    void seededSamplesForBothWidthsStayBounded() {
        Random random = new Random(9302L);
        for (float width : new float[]{1.9F, 0.7F}) {
            double offset = (int) (width + 1.0F) * 0.5D;
            long start = System.nanoTime();
            int maxVisits = 0;
            for (int sample = 0; sample < 200000; sample++) {
                maxVisits = Math.max(maxVisits, checkSupported(random.nextDouble() * 40 - 20,
                        random.nextDouble() * 40 - 20, random.nextInt(41) - 20 + offset,
                        random.nextInt(41) - 20 + offset));
            }
            System.out.printf("width=%.1f samples=200000 maxVisits=%d elapsedMs=%.3f%n",
                    width, maxVisits, (System.nanoTime() - start) / 1000000D);
        }
    }

    private static int checkSupported(double startX, double startZ, double endX, double endZ) {
        int minX = (int) Math.floor(Math.min(startX, endX));
        int maxX = (int) Math.floor(Math.max(startX, endX));
        int minZ = (int) Math.floor(Math.min(startZ, endZ));
        int maxZ = (int) Math.floor(Math.max(startZ, endZ));
        int limit = maxX - minX + maxZ - minZ + 2;
        int[] visits = {0};
        assertTrue(HorizontalSupportScan.hasSupportBetween(startX, startZ, endX, endZ, (x, z) -> {
            // Throw inside the callback: an old infinite loop fails without a stuck worker/server.
            assertTrue(++visits[0] <= limit, "Scan exceeded the segment's cell budget");
            assertTrue(x >= minX && x <= maxX && z >= minZ && z <= maxZ,
                    "Scan queried a cell beyond the segment endpoints: " + x + "," + z);
            return true;
        }));
        return visits[0];
    }
}
