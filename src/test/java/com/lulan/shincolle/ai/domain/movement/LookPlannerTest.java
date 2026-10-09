package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LookPlannerTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final TargetHandle OWNER = new TargetHandle(new UUID(0L, 1L), OVERWORLD);
    private static final TargetHandle FOE = new TargetHandle(new UUID(0L, 2L), OVERWORLD);
    private static final LookRequest FOLLOW = new LookRequest(new MovementTarget.Entity(OWNER), 20F, 40F,
            LookReason.FOLLOW_OWNER);

    @Test
    void engagedTargetIsLookedAtAtTheAttackGoalsSpeed() {
        LookRequest chosen = LookPlanner.choose(Optional.of(FOE), FOLLOW);
        assertEquals(new MovementTarget.Entity(FOE), chosen.target());
        assertEquals(LookReason.ENGAGED_TARGET, chosen.reason());
        assertEquals(30F, chosen.yawSpeed());
        assertEquals(30F, chosen.pitchSpeed());
    }

    @Test
    void withoutEngagementTheFallbackIsKept() {
        assertSame(FOLLOW, LookPlanner.choose(Optional.empty(), FOLLOW));
    }

    @Test
    void aPointCanBeLookedAtButNotAroundOne() {
        MovementPoint point = new MovementPoint(1D, 2D, 3D);
        LookRequest guard = new LookRequest(new MovementTarget.Point(point), 30F, 40F, LookReason.GUARD);
        assertSame(guard, LookPlanner.choose(Optional.empty(), guard));
        assertThrows(IllegalArgumentException.class, () -> new LookRequest(new MovementTarget.Around(point),
                30F, 40F, LookReason.GUARD));
    }
}
