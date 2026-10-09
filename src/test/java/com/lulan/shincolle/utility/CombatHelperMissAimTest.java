package com.lulan.shincolle.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatHelperMissAimTest {
    /** A zombie or a ship: within the width the new formula is built for. */
    private static final double NORMAL_WIDTH = 0.6D;
    private static final float TARGET_X = 100.5F;
    private static final float TARGET_Y = 64F;
    private static final float TARGET_Z = -20.5F;
    /** Half the splash box (0.5 + 3.5 wider, halved) plus half a target's width. */
    private static final double SPLASH_REACH = 4.05D;
    private static final float[] ROLLS = {0F, 0.25F, 0.5F, 0.75F, 0.9999F};
    /** Shooter offsets from the target: close, far, diagonal, on the target's column (a shot from above). */
    private static final double[][] SHOOTERS = {{3D, 0D}, {-40D, 5D}, {12D, -12D}, {0D, 0D}, {0D, 0.005D}, {-1.5D, 3D}};

    @Test
    void missPointIsBesideTheTargetOnTheGroundAndOutsideTheSplash() {
        for (double[] shooter : SHOOTERS) {
            for (float f1 : ROLLS) {
                for (float f2 : ROLLS) {
                    for (float f3 : ROLLS) {
                        double[] aim = aim(shooter, f1, f2, f3);
                        double dx = aim[0] - TARGET_X;
                        double dz = aim[2] - TARGET_Z;
                        String at = " shooter=" + shooter[0] + "," + shooter[1] + " rolls=" + f1 + "," + f2 + "," + f3;

                        double horizontal = Math.sqrt(dx * dx + dz * dz);
                        assertTrue(horizontal >= 6D - 1e-4 && horizontal <= Math.sqrt(50D) + 1e-4,
                                "horizontal distance " + horizontal + at);
                        assertTrue(Math.max(Math.abs(dx), Math.abs(dz)) > SPLASH_REACH,
                                "inside the splash reach: " + dx + "," + dz + at);
                        assertEquals(TARGET_Y, aim[1], "the height must not change" + at);

                        // the offset along the line of fire, from the target towards the shooter
                        double ux = shooter[0];
                        double uz = shooter[1];
                        double length = Math.sqrt(ux * ux + uz * uz);
                        if (length < 0.01D) {
                            ux = 1D;
                            uz = 0D;
                        } else {
                            ux /= length;
                            uz /= length;
                        }
                        double along = dx * ux + dz * uz;
                        assertTrue(Math.abs(along) <= 1D + 1e-4, "along the line of fire " + along + at);
                    }
                }
            }
        }
    }

    @Test
    void sameInputGivesSameAimPoint() {
        assertArrayEquals(aim(SHOOTERS[1], 0.3F, 0.6F, 0.9F), aim(SHOOTERS[1], 0.3F, 0.6F, 0.9F));
    }

    @Test
    void firstRollPicksTheSideAndTheTwoSidesMirrorEachOther() {
        double[] left = aim(SHOOTERS[0], 0.1F, 0.5F, 0.5F);
        double[] right = aim(SHOOTERS[0], 0.9F, 0.5F, 0.5F);
        // the shooter is east of the target, so the sides are north and south, 6.5 blocks off
        assertEquals(TARGET_X, left[0], 1e-4D);
        assertEquals(TARGET_X, right[0], 1e-4D);
        assertEquals(6.5D, Math.abs(left[2] - TARGET_Z), 1e-4D);
        assertEquals(TARGET_Z - (left[2] - TARGET_Z), right[2], 1e-4D);
    }

    /** The earlier formula (one block to five either side and 0 to 5 up) broke the properties above. */
    @Test
    void theEarlierFormulaBreaksTheseProperties() {
        boolean insideSplash = false;
        boolean raised = false;
        for (float f1 : ROLLS) {
            for (float f2 : ROLLS) {
                for (float f3 : ROLLS) {
                    float x = TARGET_X - 5F + f1 * 10F;
                    float y = TARGET_Y + f2 * 5F;
                    float z = TARGET_Z - 5F + f3 * 10F;
                    insideSplash |= Math.max(Math.abs(x - TARGET_X), Math.abs(z - TARGET_Z)) <= SPLASH_REACH;
                    raised |= y != TARGET_Y;
                }
            }
        }
        assertTrue(insideSplash, "some earlier misses landed inside the splash of the target");
        assertTrue(raised, "some earlier misses aimed above the target");
    }

    private static double[] aim(double[] shooter, float f1, float f2, float f3) {
        return aim(shooter, NORMAL_WIDTH, f1, f2, f3);
    }

    private static double[] aim(double[] shooter, double width, float f1, float f2, float f3) {
        return CombatHelper.calcMissAimPoint(TARGET_X, TARGET_Y, TARGET_Z, TARGET_X + shooter[0],
                TARGET_Z + shooter[1], width, f1, f2, f3);
    }

    /** The earlier aim point: x and z within five blocks either side, up to five blocks above. */
    private static double[] earlierAim(float f1, float f2, float f3) {
        return new double[]{TARGET_X - 5F + f1 * 10F, TARGET_Y + f2 * 5F, TARGET_Z - 5F + f3 * 10F};
    }

    private static boolean insideSplash(double[] aim, double width) {
        double reach = 3.75D + width / 2D;
        return Math.abs(aim[0] - TARGET_X) <= reach && Math.abs(aim[2] - TARGET_Z) <= reach;
    }

    @Test
    void aTargetWiderThanTheLimitKeepsTheEarlierFormulaWithTheSameThreeRollsInTheSameOrder() {
        for (double width : new double[]{0.99D, 1.4D, 1.95D, 2.5D}) {
            for (double[] shooter : SHOOTERS) {
                for (float f1 : ROLLS) {
                    for (float f2 : ROLLS) {
                        for (float f3 : ROLLS) {
                            assertArrayEquals(earlierAim(f1, f2, f3), aim(shooter, width, f1, f2, f3),
                                    "width " + width + " rolls=" + f1 + "," + f2 + "," + f3);
                        }
                    }
                }
            }
        }
    }

    @Test
    void theLimitItselfStillUsesTheNewFormulaAndJustAboveItTheEarlierOne() {
        double[] shooter = SHOOTERS[0];
        assertEquals(6.5D, Math.abs(aim(shooter, 0.98D, 0.1F, 0.5F, 0.5F)[2] - TARGET_Z), 1e-4D);
        assertArrayEquals(earlierAim(0.1F, 0.5F, 0.5F), aim(shooter, 0.981D, 0.1F, 0.5F, 0.5F));
        assertEquals(6.5D, Math.abs(aim(shooter, 0D, 0.1F, 0.5F, 0.5F)[2] - TARGET_Z), 1e-4D);
    }

    /** A width of 1.95 (a ravager) with the shooter at 45 degrees and rolls that put the earlier miss outside. */
    @Test
    void aWideTargetIsNotNewlyHitByAMissThatWasOutsideTheSplashBefore() {
        double[] earlier = earlierAim(0F, 0F, 0.5F);
        assertTrue(!insideSplash(earlier, 1.95D), "the input must be a miss outside the splash before");
        double[] diagonal = {10D, 10D};
        assertTrue(!insideSplash(aim(diagonal, 1.95D, 0F, 0F, 0.5F), 1.95D));
        for (double width : new double[]{1.0D, 1.4D, 1.95D, 2.5D}) {
            for (double[] shooter : SHOOTERS) {
                for (float f1 : ROLLS) {
                    for (float f2 : ROLLS) {
                        for (float f3 : ROLLS) {
                            if (!insideSplash(earlierAim(f1, f2, f3), width)) {
                                assertTrue(!insideSplash(aim(shooter, width, f1, f2, f3), width),
                                        "newly inside the splash: width " + width + " rolls=" + f1 + "," + f2 + ","
                                                + f3 + " shooter=" + shooter[0] + "," + shooter[1]);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void anOrdinaryTargetStaysOutsideTheSplashOnTheAxisAndAtFortyFiveDegrees() {
        double[][] shooters = {{10D, 0D}, {0D, -10D}, {10D, 10D}, {-10D, 10D}};
        for (double width : new double[]{0.6D, 0.98D}) {
            for (double[] shooter : shooters) {
                for (float f1 : ROLLS) {
                    for (float f2 : ROLLS) {
                        for (float f3 : ROLLS) {
                            assertTrue(!insideSplash(aim(shooter, width, f1, f2, f3), width),
                                    "width " + width + " inside the splash, rolls=" + f1 + "," + f2 + "," + f3
                                            + " shooter=" + shooter[0] + "," + shooter[1]);
                        }
                    }
                }
            }
        }
    }

    /** Horizontal coordinates where the float spacing is 0.5 and more (2^22 and up), and the far edge of the world. */
    private static final double[] FAR = {0.5D, 4_194_304.5D, 8_000_000.5D, 8_388_608.5D, 16_777_216.5D,
            29_999_984.5D};

    private static double[] farAim(double x, float y, double z, double[] shooter, double width, float f1, float f2,
                                   float f3) {
        return CombatHelper.calcMissAimPoint(x, y, z, x + shooter[0], z + shooter[1], width, f1, f2, f3);
    }

    /**
     * The miss stays outside the splash however far from the origin the target stands: the aim point
     * keeps the double precision of the target's position, so the margin of 4.2426 against a reach of
     * 3.75 + width / 2 (at most 4.24) is not lost to rounding to a float.
     */
    @Test
    void anOrdinaryTargetStaysOutsideTheSplashAtFarWorldCoordinates() {
        double[][] shooters = {{10D, 0D}, {0D, -10D}, {10D, 10D}, {-10D, 10D}, {10D, -10D}, {-10D, -10D}};
        for (double width : new double[]{0.6D, 0.98D}) {
            for (double x : FAR) {
                for (double z : FAR) {
                    for (double[] shooter : shooters) {
                        for (float f1 : ROLLS) {
                            for (float f2 : ROLLS) {
                                for (float f3 : ROLLS) {
                                    double[] aim = farAim(x, TARGET_Y, z, shooter, width, f1, f2, f3);
                                    double reach = 3.75D + width / 2D;
                                    assertTrue(Math.max(Math.abs(aim[0] - x), Math.abs(aim[2] - z)) > reach,
                                            "inside the splash: width " + width + " target " + x + "," + z
                                                    + " shooter " + shooter[0] + "," + shooter[1] + " rolls " + f1
                                                    + "," + f2 + "," + f3 + " aim " + aim[0] + "," + aim[2]);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The height is a float and comes back the same float: unchanged beside a narrow target, and with the
     * earlier float offset for a wide one. A value that is not exact in a double sum shows a double detour.
     */
    @Test
    void theHeightComesBackAsTheFloatItWentInAs() {
        float[] heights = {64F, 64.22000885009766F, 64.22000122070312F, -3.3F, 255.9999F, 1.0e-7F};
        for (float y : heights) {
            for (double width : new double[]{0D, 0.6D, 0.98D}) {
                for (float f2 : ROLLS) {
                    double[] aim = farAim(100.5D, y, -20.5D, SHOOTERS[1], width, 0.25F, f2, 0.75F);
                    assertEquals(y, aim[1], "narrow target keeps the height, width " + width);
                    assertTrue((float) aim[1] == aim[1], "the height is the value of a float");
                }
            }
            for (double width : new double[]{0.99D, 1.95D}) {
                for (float f2 : ROLLS) {
                    double[] aim = farAim(100.5D, y, -20.5D, SHOOTERS[1], width, 0.25F, f2, 0.75F);
                    assertEquals(y + f2 * 5F, aim[1], "wide target adds the offset in float, width " + width);
                    assertTrue((float) aim[1] == aim[1], "the height is the value of a float");
                }
            }
        }
    }

    /** The reported case: width 0.6, target (8,000,000, 8,000,000), shooter ten blocks either way, rolls 0, 0, 0.5. */
    @Test
    void theReportedFarMissIsOutsideTheSplash() {
        double[] aim = farAim(8_000_000D, TARGET_Y, 8_000_000D, new double[]{10D, 10D}, 0.6D, 0F, 0F, 0.5F);
        assertTrue(Math.max(Math.abs(aim[0] - 8_000_000D), Math.abs(aim[2] - 8_000_000D)) > 3.75D + 0.3D,
                "aim " + aim[0] + "," + aim[2]);
    }
}
