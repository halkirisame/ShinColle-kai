package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipHeadLookTest {
    private static final float REST = 0.25F;

    @Test
    void clipThatLeavesTheHeadAloneDoesNotAccumulateTheLook() {
        float bone = REST;
        for (int frame = 0; frame < 1000; frame++) {
            bone = ShipHeadLook.compose(bone, REST, 0.5F, false);
        }
        assertEquals(REST + 0.5F, bone, 0.0F);
    }

    @Test
    void sameInputsGiveTheSameResultWhenAppliedAgain() {
        float first = ShipHeadLook.compose(REST, REST, 0.3F, false);
        float second = ShipHeadLook.compose(first, REST, 0.3F, false);
        assertEquals(first, second, 0.0F);
    }

    @Test
    void followsTheLookAsItChanges() {
        float bone = REST;
        for (float look : new float[]{0.4F, -0.7F, 0F, 1.1F}) {
            bone = ShipHeadLook.compose(bone, REST, look, false);
            assertEquals(REST + look, bone, 0.0F);
        }
    }

    @Test
    void clipRotationOfTheHeadIsKeptAndTheLookIsAddedOnTopOfIt() {
        for (float clip : new float[]{0.0F, 0.2F, -0.3F, 0.2F}) {
            assertEquals(clip + 0.5F, ShipHeadLook.compose(clip, REST, 0.5F, true), 0.0F);
        }
    }

    @Test
    void staticClipHoldsItsAngleWhileTheLookIsExactlyZero() {
        float clip = 0.71F;
        for (int frame = 0; frame < 6; frame++) {
            // the animation engine rewrites the bone to the clip's angle every frame
            assertEquals(clip, ShipHeadLook.compose(clip, REST, 0F, true), 0.0F, "frame " + frame);
        }
    }

    @Test
    void headReturnsToRestPlusLookOnceTheClipStopsDrivingIt() {
        float stale = 0.9F + 0.5F;
        assertEquals(REST + 0.5F, ShipHeadLook.compose(stale, REST, 0.5F, false), 0.0F);
    }
}
