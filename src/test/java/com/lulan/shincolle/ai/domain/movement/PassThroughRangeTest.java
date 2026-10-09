package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.waypoint.WaypointTraversal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The distances a guard keeps at a waypoint the route goes on from. */
class PassThroughRangeTest {
    private static final float WIDTH = 0.6F;

    private static FollowRange range(boolean formation, boolean pickItem, int followMin, int followMax, float width,
                                     boolean passThrough, boolean engaged, boolean pickingItem) {
        return GuardMovePlanner.range(formation, false, pickItem, followMin, followMax, width, passThrough, engaged,
                pickingItem);
    }

    private static FollowRange loose(int followMin, int followMax, float width, boolean pickItem) {
        return GuardMovePlanner.range(false, false, pickItem, followMin, followMax, width);
    }

    @Test
    void awayFromAPassThroughPointTheRangeIsTheSameAsBefore() {
        for (boolean engaged : new boolean[]{false, true}) {
            for (boolean picking : new boolean[]{false, true}) {
                assertEquals(loose(4, 5, WIDTH, false), range(false, false, 4, 5, WIDTH, false, engaged, picking));
                assertEquals(loose(4, 5, WIDTH, true), range(false, true, 4, 5, WIDTH, false, engaged, picking));
                assertEquals(GuardMovePlanner.range(true, false, true, 4, 5, WIDTH),
                        range(true, true, 4, 5, WIDTH, false, engaged, picking));
            }
        }
    }

    @Test
    void atAPassThroughPointBothDistancesTightenToTheFormationBlockGuard() {
        assertEquals(new FollowRange(4D, 7D), range(false, false, 4, 5, WIDTH, true, false, false));
        assertEquals(new FollowRange(4D, 7D), range(false, false, 8, 9, WIDTH, true, false, false));
    }

    @Test
    void aWideBodyWithFollowMinTwoStillStopsInsideTheArrivalRange() {
        // 2 + 1.9 * 0.75 = 3.425 blocks, outside the arrival range of three
        assertEquals(4D, range(false, false, 2, 3, 1.9F, true, false, false).minSq());
        assertEquals(4D, range(false, false, 2, 3, 2.5F, true, false, false).minSq());
    }

    @Test
    void aShipWithCloserSettingsKeepsThem() {
        FollowRange settings = loose(1, 2, WIDTH, false);
        assertTrue(settings.minSq() < 4D && settings.maxSq() < 7D);
        assertEquals(settings, range(false, false, 1, 2, WIDTH, true, false, false));
    }

    @Test
    void anEngagedShipKeepsItsStartDistanceButStopsInside() {
        FollowRange settings = loose(4, 6, WIDTH, false);
        assertEquals(new FollowRange(4D, settings.maxSq()), range(false, false, 4, 6, WIDTH, true, true, false));
    }

    @Test
    void aShipPickingAnItemUpKeepsItsStartDistanceWithThePickupRange() {
        FollowRange settings = loose(5, 6, WIDTH, true);
        assertEquals(new FollowRange(4D, settings.maxSq()), range(false, true, 5, 6, WIDTH, true, false, true));
    }

    @Test
    void aShipThatPicksItemsUpButIsNotPickingOneTightensTheStartDistance() {
        assertEquals(new FollowRange(4D, 7D), range(false, true, 5, 6, WIDTH, true, false, false));
    }

    @Test
    void aFormationOnlyChangesWhenItPicksItemsUp() {
        assertEquals(new FollowRange(4D, 7D), range(true, false, 1, 2, WIDTH, true, false, false));
        assertEquals(new FollowRange(4D, 7D), range(true, true, 1, 2, WIDTH, true, false, false));
        assertEquals(new FollowRange(4D, 64D), range(true, true, 1, 2, WIDTH, true, true, false));
    }

    @Test
    void theStartDistanceIsInsideTheArrivalRange() {
        assertTrue(GuardMovePlanner.PASS_THROUGH_MIN_SQ < GuardMovePlanner.PASS_THROUGH_MAX_SQ);
        assertTrue(GuardMovePlanner.PASS_THROUGH_MAX_SQ < WaypointTraversal.ARRIVAL_DISTANCE_SQ);
        for (int followMin = 0; followMin <= 10; followMin++) {
            for (float width : new float[]{0.5F, 0.8F, 1.6F, 1.9F, 2.5F}) {
                FollowRange range = range(false, false, followMin, followMin + 1, width, true, false, false);
                assertTrue(range.maxSq() < WaypointTraversal.ARRIVAL_DISTANCE_SQ, "FollowMin " + followMin);
            }
        }
    }
}
