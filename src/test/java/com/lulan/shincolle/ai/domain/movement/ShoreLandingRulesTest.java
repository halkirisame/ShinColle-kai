package com.lulan.shincolle.ai.domain.movement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.OptionalDouble;

import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Action.LIFT;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Action.NONE;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.ATTEMPT_LIMIT;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.FLYING;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.LEDGE_TOO_HIGH;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NOT_AFLOAT;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NOT_MOVING;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NO_HEADROOM;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NO_HORIZONTAL_COLLISION;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.NO_LEDGE;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.ON_GROUND;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.PLAYER_CONTROLLED;
import static com.lulan.shincolle.ai.domain.movement.ShoreLandingDecision.Reason.TARGET_NOT_ON_LEDGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoreLandingRulesTest {
    private ShoreFacts facts(ShoreLandingDecision.Reason reason, double top, double wanted, long now, long key) {
        return new ShoreFacts(reason != NOT_MOVING, reason == FLYING, reason == PLAYER_CONTROLLED,
                reason == ON_GROUND, reason != NOT_AFLOAT, 7, reason != NO_HORIZONTAL_COLLISION,
                reason == NO_LEDGE ? OptionalDouble.empty() : OptionalDouble.of(top),
                reason != NO_HEADROOM, wanted, 1, now, key);
    }

    private ShoreFacts valid(long now, long key) {
        return facts(null, 8, 8, now, key);
    }

    @ParameterizedTest
    @EnumSource(ShoreLandingDecision.Reason.class)
    void eachRefusalIsExplained(ShoreLandingDecision.Reason reason) {
        var input = facts(reason, reason == LEDGE_TOO_HIGH ? 9 : 8,
                reason == TARGET_NOT_ON_LEDGE ? 7 : 9, 100, 4);
        var attempt = reason == ATTEMPT_LIMIT ? new ShoreLandingAttempt(4, 20, 120)
                : ShoreLandingAttempt.start(4);
        var result = ShoreLandingRules.decide(input, attempt);
        assertEquals(NONE, result.action());
        assertEquals(EnumSet.of(reason), result.reasons());
    }

    @Test
    void validFactsLiftDeterministically() {
        var input = valid(100, 4);
        var previous = ShoreLandingAttempt.start(4);
        var result = ShoreLandingRules.decide(input, previous);
        assertEquals(LIFT, result.action());
        assertTrue(result.reasons().isEmpty());
        assertEquals(new ShoreLandingAttempt(4, 1, 0), result.next());
        assertEquals(result, ShoreLandingRules.decide(input, previous));
    }

    @Test
    void heightAndTargetBoundariesAreInclusive() {
        var attempt = ShoreLandingAttempt.start(4);
        assertEquals(LIFT, ShoreLandingRules.decide(facts(null, 8.01, 8.01, 100, 4), attempt).action());
        assertTrue(ShoreLandingRules.decide(facts(null, 8.010001, 8.01, 100, 4), attempt)
                .reasons().contains(LEDGE_TOO_HIGH));
        assertEquals(LIFT, ShoreLandingRules.decide(facts(null, 8, 7.5, 100, 4), attempt).action());
        assertTrue(ShoreLandingRules.decide(facts(null, 8, 7.499999, 100, 4), attempt)
                .reasons().contains(TARGET_NOT_ON_LEDGE));
    }

    @Test
    void waterBlockTopMakesOneBlockBankFitNormalShipStep() {
        var input = new ShoreFacts(true, false, false, false, true, 7, true,
                OptionalDouble.of(8), true, 8, 1, 100, 4);
        assertEquals(LIFT, ShoreLandingRules.decide(input, ShoreLandingAttempt.start(4)).action());
    }

    @Test
    void afloatIncludesWholeStepHeightAndToleranceButNotBeyond() {
        assertTrue(ShoreLandingRules.isAfloat(false, false, true, 10, 7, 3));
        assertTrue(ShoreLandingRules.isAfloat(false, false, true, 10.01, 7, 3));
        assertFalse(ShoreLandingRules.isAfloat(false, false, true, 10.01001, 7, 3));
        assertFalse(ShoreLandingRules.isAfloat(false, true, true, 8, 7, 3));
        assertFalse(ShoreLandingRules.isAfloat(false, false, false, 8, 7, 3));
        assertTrue(ShoreLandingRules.isAfloat(true, false, false, 8, 7, 3));
    }

    @Test
    void twentyLiftsThenFortyTickCooldown() {
        var attempt = ShoreLandingAttempt.start(4);
        for (int tick = 100; tick < 120; tick++) {
            var result = ShoreLandingRules.decide(valid(tick, 4), attempt);
            assertEquals(LIFT, result.action());
            attempt = result.next();
        }
        assertEquals(new ShoreLandingAttempt(4, 20, 159), attempt);
        assertEquals(EnumSet.of(ATTEMPT_LIMIT), ShoreLandingRules.decide(valid(120, 4), attempt).reasons());
        assertEquals(NONE, ShoreLandingRules.decide(valid(158, 4), attempt).action());
        assertEquals(new ShoreLandingAttempt(4, 1, 0),
                ShoreLandingRules.decide(valid(159, 4), attempt).next());
    }

    @Test
    void newTargetGroundAndIdleResetAttempt() {
        var blocked = new ShoreLandingAttempt(4, 20, 159);
        assertEquals(new ShoreLandingAttempt(5, 1, 0),
                ShoreLandingRules.decide(valid(120, 5), blocked).next());
        for (var reason : new ShoreLandingDecision.Reason[]{ON_GROUND, NOT_MOVING}) {
            var result = ShoreLandingRules.decide(facts(reason, 8, 8, 120, 4), blocked);
            assertEquals(ShoreLandingAttempt.start(4), result.next());
            assertEquals(EnumSet.of(reason), result.reasons());
        }
    }

    @Test
    void gathersAllMeaningfulRefusalsAndDoesNotExposeMutableReasons() {
        var input = new ShoreFacts(true, true, true, false, true, 7, false,
                OptionalDouble.of(9), false, 7, 1, 100, 4);
        var result = ShoreLandingRules.decide(input, new ShoreLandingAttempt(4, 20, 120));
        var expected = EnumSet.of(FLYING, PLAYER_CONTROLLED, NO_HORIZONTAL_COLLISION,
                TARGET_NOT_ON_LEDGE, LEDGE_TOO_HIGH, NO_HEADROOM, ATTEMPT_LIMIT);
        assertEquals(expected, result.reasons());
        result.reasons().clear();
        assertEquals(expected, result.reasons());
    }
}
