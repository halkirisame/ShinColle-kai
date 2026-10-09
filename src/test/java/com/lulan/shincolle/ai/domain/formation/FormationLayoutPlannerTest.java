package com.lulan.shincolle.ai.domain.formation;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FormationLayoutPlannerTest {
    @Test void allPatternsSlotsAndDirectionsKeepTheirPositions() {
        int[][][] expected = {
            {{0,0},{-3,0},{-6,0},{-9,0},{-12,0},{-15,0}},
            {{0,0},{0,3},{3,0},{3,3},{-3,0},{-3,3}},
            {{0,0},{5,0},{1,-4},{1,4},{-3,0},{2,0}},
            {{0,0},{-2,-2},{-4,-4},{-6,-6},{-8,-8},{-10,-10}},
            {{0,0},{0,3},{0,-3},{0,6},{0,-6},{0,9}}
        };
        for (FormationPattern pattern : FormationPattern.values()) {
            for (boolean alongX : new boolean[]{true, false}) for (boolean positive : new boolean[]{true, false}) {
                for (int slot = 0; slot < 6; slot++) {
                    int[] pair = expected[pattern.ordinal()][slot];
                    int x = pair[0], z = pair[1];
                    if (pattern == FormationPattern.ECHELON) {
                        if (!positive) { x = -x; z = -z; }
                    } else {
                        if (!positive) x = -x;
                        if (!alongX) { int swap = x; x = z; z = swap; }
                    }
                    assertEquals(new CommandPos(-31 + x, -60, 23 + z), FormationLayoutPlanner.place(pattern,
                            new FormationSlot(slot), new FormationLayoutPlanner.Facing(alongX, positive),
                            new CommandPos(-31, -60, 23)), pattern + " " + slot + " " + alongX + " " + positive);
                }
            }
        }
    }
    @Test void roundingAndDirectionKeepNegativeYAndTieRules() {
        assertEquals(new CommandPos(-1, -59, 0), FormationLayoutPlanner.round(new MovementPoint(-.1, -60, .1)));
        assertEquals(new FormationLayoutPlanner.Facing(false, true), FormationLayoutPlanner.facing(1, 1, 0, 0));
        assertEquals(new FormationLayoutPlanner.Facing(true, false), FormationLayoutPlanner.facing(-2, 1, 0, 0));
    }
    @Test void onlyActiveFollowersLeaveRouteChoiceToFlagshipAndOnlyManualRepeatCancels() {
        assertTrue(FormationLayoutPlanner.mayAdvanceRoute(new FormationProjection.Pending(
                FormationProjection.Reason.MEMBERS_UNRESOLVED)));
        assertFalse(FormationLayoutPlanner.mayAdvanceRoute(new FormationProjection.Active(
                FormationPattern.LINE_AHEAD, new FormationSlot(1), .3F)));
        assertTrue(FormationLayoutPlanner.mayAdvanceRoute(new FormationProjection.Active(
                FormationPattern.LINE_AHEAD, FormationSlot.FLAGSHIP, .3F)));
        CommandPos point = new CommandPos(0, -60, 0);
        assertTrue(FormationLayoutPlanner.repeatReturnsToFollow(point, point, true));
        assertFalse(FormationLayoutPlanner.repeatReturnsToFollow(point, point, false));
        assertFalse(FormationLayoutPlanner.repeatReturnsToFollow(null, point, true));
    }
}
