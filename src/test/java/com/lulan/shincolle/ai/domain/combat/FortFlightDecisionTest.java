package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.combat.FortFlightDecision.Action;
import com.lulan.shincolle.ai.domain.combat.FortFlightDecision.Completion;
import com.lulan.shincolle.ai.domain.combat.FortFlightDecision.Facts;
import com.lulan.shincolle.ai.domain.combat.FortFlightDecision.Reason;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FortFlightDecisionTest {
    private static Facts facts(int age, boolean returning, boolean host, boolean target, double distance) {
        return new Facts(age, returning, host, target, distance, Completion.ACTIVE);
    }

    private static void expect(Action action, Set<Reason> reasons, Facts facts) {
        var result = FortFlightDecision.decide(facts);
        assertEquals(action, result.action(), facts.toString());
        assertEquals(reasons, result.reasons(), facts.toString());
    }

    @Test
    void fortFlightDecisionUsesOriginalBoundaries() {
        double epsilon = 1E-9;
        expect(Action.DETONATE, Set.of(Reason.IN_BLAST_RANGE), facts(3, false, true, true, 6D));
        expect(Action.WAIT, Set.of(Reason.EN_ROUTE), facts(3, false, true, true, 6D + epsilon));
        // A path is asked for on the 16th tick only, and only beyond four squared blocks.
        expect(Action.WAIT, Set.of(Reason.EN_ROUTE), facts(15, false, true, true, 9D));
        expect(Action.PATH, Set.of(Reason.PATH_DUE), facts(16, false, true, true, 9D));
        expect(Action.WAIT, Set.of(Reason.EN_ROUTE), facts(17, false, true, true, 9D));
        expect(Action.PATH, Set.of(Reason.PATH_DUE), facts(32, false, true, true, 6D + epsilon));
        // At four or less it is already in blast range, so it never asks for a path there.
        expect(Action.DETONATE, Set.of(Reason.IN_BLAST_RANGE), facts(16, false, true, true, 4D));
        // The flight lasts 500 ticks.
        expect(Action.WAIT, Set.of(Reason.EN_ROUTE), facts(499, false, true, true, 9D));
        expect(Action.DETONATE, Set.of(Reason.AGE_LIMIT), facts(500, false, true, true, 9D));
        expect(Action.DETONATE, Set.of(Reason.RETURNING), facts(3, true, true, true, 9D));
        expect(Action.DETONATE, Set.of(Reason.TARGET_LOST), facts(3, false, true, false, 9D));
        expect(Action.DETONATE, Set.of(Reason.AGE_LIMIT, Reason.RETURNING, Reason.TARGET_LOST),
                facts(600, true, true, false, 1D));
    }

    @Test
    void invalidHostDiscardsWithoutExplosionBeforeAnythingElse() {
        expect(Action.DISCARD, Set.of(Reason.HOST_INVALID), facts(3, false, false, true, 1D));
        expect(Action.DISCARD, Set.of(Reason.HOST_INVALID), facts(500, true, false, false, 1D));
    }

    @Test
    void finishedFortNeverActsAgain() {
        for (boolean host : new boolean[]{true, false}) {
            expect(Action.WAIT, Set.of(Reason.ALREADY_FINISHED),
                    new Facts(500, true, host, false, 0D, Completion.FINISHED));
        }
    }

    @Test
    void fortAndCarrierProfilesKeepCannonAndAircraftChannelsSeparate() {
        var profile = FortAttributeProfile.of(new FortAttributeProfile.Host(400F, 12F, 60F, 20F, 2F, .5F, .1F));
        assertEquals(40F, profile.health());
        // Both light channels carry the cannon value, both heavy channels the cannon heavy value, once.
        assertEquals(12F, profile.lightDamage());
        assertEquals(12F, profile.lightAirDamage());
        assertEquals(60F, profile.heavyDamage());
        assertEquals(60F, profile.heavyAirDamage());
        assertEquals(10F, profile.defense());
        assertEquals(2F, profile.attackSpeed());
        assertEquals(.5F * .1F + .3F, profile.moveSpeed());
        assertEquals(6F, profile.hit());
        assertEquals(.4F, profile.dodge(), 1E-6F);
        assertEquals(64D, profile.followRange());
        assertEquals(.5D, profile.knockbackResistance());
    }
}
