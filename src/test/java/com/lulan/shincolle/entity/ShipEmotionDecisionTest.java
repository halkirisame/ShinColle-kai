package com.lulan.shincolle.entity;

import com.lulan.shincolle.reference.ID;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class ShipEmotionDecisionTest {

    @Test
    void forcedExpressionsRespectPriorityWithoutConsumingRandom() {
        ShipEmotionDecision.BoundedRandom unexpectedRandom = bound -> {
            throw new AssertionError("Forced emotion must not consume random bound " + bound);
        };

        ShipEmotionDecision.ExpressionTransition hungry = ShipEmotionDecision.nextExpressions(
                ShipEmotionDecision.Policy.FRIENDLY, true, 0.1F,
                ID.Emotion.NORMAL, ID.Emotion.BORED, unexpectedRandom);
        assertEquals(ID.Emotion.HUNGRY, hungry.primary());
        assertEquals(ID.Emotion.BORED, hungry.secondary());

        ShipEmotionDecision.ExpressionTransition damaged = ShipEmotionDecision.nextExpressions(
                ShipEmotionDecision.Policy.HOSTILE, false, 0.34F,
                ID.Emotion.NORMAL, ID.Emotion.BORED, unexpectedRandom);
        assertEquals(ID.Emotion.T_T, damaged.primary());
        assertEquals(ID.Emotion.BORED, damaged.secondary());
    }

    @Test
    void friendlyPeriodicRollsUseLegacyBoundsAndOrder() {
        SequenceRandom normalRolls = new SequenceRandom(0, 1);
        ShipEmotionDecision.ExpressionTransition fromNormal = ShipEmotionDecision.nextExpressions(
                ShipEmotionDecision.Policy.FRIENDLY, false, 1F,
                ID.Emotion.NORMAL, ID.Emotion.NORMAL, normalRolls);
        assertEquals(ID.Emotion.BORED, fromNormal.primary());
        assertEquals(ID.Emotion.NORMAL, fromNormal.secondary());
        assertIterableEquals(List.of(3, 3), normalRolls.bounds());

        SequenceRandom returnRolls = new SequenceRandom(0, 0);
        ShipEmotionDecision.ExpressionTransition fromOther = ShipEmotionDecision.nextExpressions(
                ShipEmotionDecision.Policy.FRIENDLY, false, 1F,
                ID.Emotion.SHY, ID.Emotion.BORED, returnRolls);
        assertEquals(ID.Emotion.NORMAL, fromOther.primary());
        assertEquals(ID.Emotion.NORMAL, fromOther.secondary());
        assertIterableEquals(List.of(4, 3), returnRolls.bounds());
    }

    @Test
    void hostilePeriodicRollsUseLegacyBoundsAndOrder() {
        SequenceRandom normalRolls = new SequenceRandom(0, 0);
        ShipEmotionDecision.ExpressionTransition fromNormal = ShipEmotionDecision.nextExpressions(
                ShipEmotionDecision.Policy.HOSTILE, false, 1F,
                ID.Emotion.NORMAL, ID.Emotion.NORMAL, normalRolls);
        assertEquals(ID.Emotion.BORED, fromNormal.primary());
        assertEquals(ID.Emotion.BORED, fromNormal.secondary());
        assertIterableEquals(List.of(4, 3), normalRolls.bounds());

        SequenceRandom returnRolls = new SequenceRandom(0, 0);
        ShipEmotionDecision.ExpressionTransition fromOther = ShipEmotionDecision.nextExpressions(
                ShipEmotionDecision.Policy.HOSTILE, false, 1F,
                ID.Emotion.ANGRY, ID.Emotion.BORED, returnRolls);
        assertEquals(ID.Emotion.NORMAL, fromOther.primary());
        assertEquals(ID.Emotion.NORMAL, fromOther.secondary());
        assertIterableEquals(List.of(2, 2), returnRolls.bounds());
    }

    @Test
    void friendlyReactionPolicyPreservesThresholdsAndCooldowns() {
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 0, 0, 7,
                ShipEmotionDecision.Reaction.NORMAL, 50);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 1, 0, 9,
                ShipEmotionDecision.Reaction.STRANGER, 60);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 2, 10, 0,
                ShipEmotionDecision.Reaction.DAMAGED, 40);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 2, 11, 0,
                ShipEmotionDecision.Reaction.NONE, 11);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 3, 0, 6,
                ShipEmotionDecision.Reaction.ATTACK, 60);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 4, 0, 3,
                ShipEmotionDecision.Reaction.IDLE, 20);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 5, 0, 3,
                ShipEmotionDecision.Reaction.COMMAND, 25);
        assertReaction(ShipEmotionDecision.Policy.FRIENDLY, 6, 37, 0,
                ShipEmotionDecision.Reaction.SHOCK, 37);
    }

    @Test
    void hostileReactionPolicyPreservesThresholdsAndCooldowns() {
        assertReaction(ShipEmotionDecision.Policy.HOSTILE, 2, 0, 0,
                ShipEmotionDecision.Reaction.DAMAGED, 40);
        assertReaction(ShipEmotionDecision.Policy.HOSTILE, 2, 1, 0,
                ShipEmotionDecision.Reaction.NONE, 1);
        assertReaction(ShipEmotionDecision.Policy.HOSTILE, 3, 0, 7,
                ShipEmotionDecision.Reaction.ATTACK, 60);
        assertReaction(ShipEmotionDecision.Policy.HOSTILE, 4, 0, 3,
                ShipEmotionDecision.Reaction.IDLE, 20);
        assertReaction(ShipEmotionDecision.Policy.HOSTILE, 6, 37, 0,
                ShipEmotionDecision.Reaction.SHOCK, 37);
    }

    @Test
    void probabilisticReactionConsumesRollBeforeCooldownCheck() {
        SequenceRandom random = new SequenceRandom(0);
        ShipEmotionDecision.ReactionTransition transition = ShipEmotionDecision.nextReaction(
                ShipEmotionDecision.Policy.FRIENDLY, 1, 5, random);

        assertEquals(ShipEmotionDecision.Reaction.NONE, transition.reaction());
        assertEquals(5, transition.cooldown());
        assertIterableEquals(List.of(9), random.bounds());
    }

    @Test
    void failedProbabilityRollPreservesCooldownAndSelectsNoReaction() {
        SequenceRandom random = new SequenceRandom(1);
        ShipEmotionDecision.ReactionTransition transition = ShipEmotionDecision.nextReaction(
                ShipEmotionDecision.Policy.HOSTILE, 3, 0, random);

        assertEquals(ShipEmotionDecision.Reaction.NONE, transition.reaction());
        assertEquals(0, transition.cooldown());
        assertIterableEquals(List.of(7), random.bounds());
    }

    private static void assertReaction(ShipEmotionDecision.Policy policy, int type,
            int currentCooldown, int expectedBound, ShipEmotionDecision.Reaction expectedReaction,
            int expectedCooldown) {
        SequenceRandom random = new SequenceRandom(0);
        ShipEmotionDecision.ReactionTransition transition = ShipEmotionDecision.nextReaction(
                policy, type, currentCooldown, random);

        assertEquals(expectedReaction, transition.reaction());
        assertEquals(expectedCooldown, transition.cooldown());
        if (expectedBound == 0) {
            assertIterableEquals(List.of(), random.bounds());
        } else {
            assertIterableEquals(List.of(expectedBound), random.bounds());
        }
    }

    private static final class SequenceRandom implements ShipEmotionDecision.BoundedRandom {
        private final int[] values;
        private final List<Integer> bounds = new ArrayList<>();
        private int index;

        private SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            this.bounds.add(bound);
            if (this.index >= this.values.length) {
                throw new AssertionError("Missing test random value for bound " + bound);
            }
            return this.values[this.index++];
        }

        private List<Integer> bounds() {
            return this.bounds;
        }
    }
}
