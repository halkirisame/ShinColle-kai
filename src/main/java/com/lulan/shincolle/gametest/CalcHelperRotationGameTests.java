package com.lulan.shincolle.gametest;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.utility.CalcHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Locks {@link CalcHelper#rotateXZByAxis} to the 1.10.2 rotation direction.
 *
 * <p>The port originally negated the sine terms, which mirrored every result
 * across the Z axis. Every caller passes the original's angle expression, so a
 * single wrong sign here misplaced mount seats and every ship-relative particle
 * at once, and only showed up at yaws other than 0 and 180 degrees.</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CalcHelperRotationGameTests {

    private static final float EPSILON = 1.0E-5F;

    /**
     * Looser bound for the round-trip check. {@link Mth#sin} and {@link Mth#cos}
     * read a lookup table rather than computing exactly, so rotating by an angle
     * and back accumulates table error instead of cancelling it. The other tests
     * compare against the same table and stay within {@link #EPSILON}.
     */
    private static final float ROUND_TRIP_EPSILON = 1.0E-3F;

    private CalcHelperRotationGameTests() {
    }

    /** The helper must reproduce the original formula for arbitrary inputs. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rotationMatchesOriginalFormula(GameTestHelper helper) {
        float[][] cases = {
                {1F, 0F, 0F},
                {1F, 0F, Mth.HALF_PI},
                {0F, 1F, Mth.HALF_PI},
                {0.59F, 0F, 0.7F},
                {-0.4F, 0.3F, 2.1F},
                {1.05F, -1.3F, -1.9F},
        };

        for (float[] input : cases) {
            float z = input[0];
            float x = input[1];
            float rad = input[2];
            float scale = 1.5F;

            // Original 1.10.2 CalcHelper.rotateXZByAxis.
            float expectedZ = (z * Mth.cos(rad) + x * Mth.sin(rad)) * scale;
            float expectedX = (x * Mth.cos(rad) - z * Mth.sin(rad)) * scale;

            float[] actual = CalcHelper.rotateXZByAxis(z, x, rad, scale);
            assertClose(expectedZ, actual[0], "newPos[0] for z=" + z + " x=" + x + " rad=" + rad);
            assertClose(expectedX, actual[1], "newPos[1] for z=" + z + " x=" + x + " rad=" + rad);
        }
        helper.succeed();
    }

    /**
     * A forward-only offset must land on the entity's facing direction.
     * Minecraft's forward vector is (-sin(yaw), cos(yaw)) in (X, Z), and callers
     * read the result as {Z, X}, so this is the property that actually matters
     * for mount seats and ship-relative particles.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void forwardOffsetFollowsEntityFacing(GameTestHelper helper) {
        float distance = 2F;

        for (float yawDegrees = -180F; yawDegrees <= 180F; yawDegrees += 30F) {
            float yaw = yawDegrees * Values.N.DIV_PI_180;
            float[] rotated = CalcHelper.rotateXZByAxis(distance, 0F, yaw, 1F);

            float expectedX = -Mth.sin(yaw) * distance;
            float expectedZ = Mth.cos(yaw) * distance;

            assertClose(expectedZ, rotated[0], "forward Z at yaw " + yawDegrees);
            assertClose(expectedX, rotated[1], "forward X at yaw " + yawDegrees);
        }
        helper.succeed();
    }

    /** Rotating by an angle and back must return the original point. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rotationIsReversible(GameTestHelper helper) {
        float z = 0.75F;
        float x = -1.2F;
        float rad = 1.3F;

        float[] rotated = CalcHelper.rotateXZByAxis(z, x, rad, 1F);
        float[] restored = CalcHelper.rotateXZByAxis(rotated[0], rotated[1], -rad, 1F);

        assertWithin(z, restored[0], ROUND_TRIP_EPSILON, "round-trip Z");
        assertWithin(x, restored[1], ROUND_TRIP_EPSILON, "round-trip X");
        helper.succeed();
    }

    private static void assertClose(float expected, float actual, String what) {
        assertWithin(expected, actual, EPSILON, what);
    }

    private static void assertWithin(float expected, float actual, float tolerance, String what) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }
}
