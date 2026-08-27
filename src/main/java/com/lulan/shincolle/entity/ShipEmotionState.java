package com.lulan.shincolle.entity;

/**
 * Owns authoritative ship expression and reaction cooldown state.
 *
 * <p>The server decides periodic transitions and reactions. A client entity
 * keeps the synchronized expression copy used by the legacy renderer; its
 * caress, blink, and head-tilt presentation state deliberately stays outside
 * this slice.</p>
 */
final class ShipEmotionState {

    private int primaryExpression;
    private int secondaryExpression;
    private int reactionCooldown;

    int primaryExpression() {
        return this.primaryExpression;
    }

    void setPrimaryExpression(int value) {
        this.primaryExpression = value;
    }

    int secondaryExpression() {
        return this.secondaryExpression;
    }

    void setSecondaryExpression(int value) {
        this.secondaryExpression = value;
    }

    int reactionCooldown() {
        return this.reactionCooldown;
    }

    void setReactionCooldown(int value) {
        this.reactionCooldown = value;
    }

    void tickReactionCooldown() {
        if (this.reactionCooldown > 0) {
            this.reactionCooldown--;
        }
    }

    void updatePeriodic(ShipEmotionDecision.Policy policy, boolean noFuel, float hpRatio,
            ShipEmotionDecision.BoundedRandom random) {
        ShipEmotionDecision.ExpressionTransition transition = ShipEmotionDecision.nextExpressions(
                policy, noFuel, hpRatio, this.primaryExpression, this.secondaryExpression, random);
        this.primaryExpression = transition.primary();
        this.secondaryExpression = transition.secondary();
    }

    ShipEmotionDecision.Reaction tryReaction(ShipEmotionDecision.Policy policy, int type,
            ShipEmotionDecision.BoundedRandom random) {
        ShipEmotionDecision.ReactionTransition transition = ShipEmotionDecision.nextReaction(
                policy, type, this.reactionCooldown, random);
        this.reactionCooldown = transition.cooldown();
        return transition.reaction();
    }
}
