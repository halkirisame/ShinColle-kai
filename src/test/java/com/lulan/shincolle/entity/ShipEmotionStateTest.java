package com.lulan.shincolle.entity;

import com.lulan.shincolle.reference.ID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipEmotionStateTest {

    @Test
    void ownsExpressionsAndReactionCooldown() {
        ShipEmotionState state = new ShipEmotionState();
        state.setPrimaryExpression(ID.Emotion.SHY);
        state.setSecondaryExpression(ID.Emotion.BORED);
        state.setReactionCooldown(2);

        state.tickReactionCooldown();
        state.tickReactionCooldown();
        state.tickReactionCooldown();

        assertEquals(ID.Emotion.SHY, state.primaryExpression());
        assertEquals(ID.Emotion.BORED, state.secondaryExpression());
        assertEquals(0, state.reactionCooldown());
    }

    @Test
    void appliesPurePeriodicAndReactionDecisions() {
        ShipEmotionState state = new ShipEmotionState();
        state.updatePeriodic(ShipEmotionDecision.Policy.FRIENDLY, false, 1F, bound -> 0);

        assertEquals(ID.Emotion.BORED, state.primaryExpression());
        assertEquals(ID.Emotion.BORED, state.secondaryExpression());

        ShipEmotionDecision.Reaction reaction = state.tryReaction(
                ShipEmotionDecision.Policy.FRIENDLY, 2, bound -> 0);
        assertEquals(ShipEmotionDecision.Reaction.DAMAGED, reaction);
        assertEquals(40, state.reactionCooldown());
    }
}
