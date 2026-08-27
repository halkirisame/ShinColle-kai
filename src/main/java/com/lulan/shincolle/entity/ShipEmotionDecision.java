package com.lulan.shincolle.entity;

import com.lulan.shincolle.reference.ID;

/**
 * Pure emotion transition and reaction-trigger decisions.
 *
 * <p>The caller supplies only values and a bounded random source. Minecraft
 * entity, level, packet, particle, and sound effects remain in adapters.</p>
 */
final class ShipEmotionDecision {

    enum Policy {
        FRIENDLY,
        HOSTILE
    }

    enum Reaction {
        NONE,
        NORMAL,
        STRANGER,
        DAMAGED,
        ATTACK,
        IDLE,
        COMMAND,
        SHOCK
    }

    @FunctionalInterface
    interface BoundedRandom {
        int nextInt(int bound);
    }

    record ExpressionTransition(int primary, int secondary) {
    }

    record ReactionTransition(Reaction reaction, int cooldown) {
    }

    private ShipEmotionDecision() {
    }

    static ExpressionTransition nextExpressions(Policy policy, boolean noFuel, float hpRatio,
            int currentPrimary, int currentSecondary, BoundedRandom random) {
        if (noFuel) {
            return new ExpressionTransition(ID.Emotion.HUNGRY, currentSecondary);
        }
        if (hpRatio < 0.35F) {
            return new ExpressionTransition(ID.Emotion.T_T, currentSecondary);
        }

        // Original 1.10.2 BasicEntityShip#updateEmotionState:
        // if (this.getRNG().nextInt(3) == 0)
        //     this.setStateEmotion(ID.S.Emotion, ID.Emotion.BORED, false);
        // Original hostile policy uses nextInt(4), while return-to-normal uses
        // nextInt(4) for friendly ships and nextInt(2) for hostile ships.
        int primaryBound = currentPrimary == ID.Emotion.NORMAL
                ? (policy == Policy.FRIENDLY ? 3 : 4)
                : (policy == Policy.FRIENDLY ? 4 : 2);
        int nextPrimary = currentPrimary;
        if (random.nextInt(primaryBound) == 0) {
            nextPrimary = currentPrimary == ID.Emotion.NORMAL
                    ? ID.Emotion.BORED : ID.Emotion.NORMAL;
        }

        int secondaryBound = currentSecondary == ID.Emotion.NORMAL
                ? 3 : (policy == Policy.FRIENDLY ? 3 : 2);
        int nextSecondary = currentSecondary;
        if (random.nextInt(secondaryBound) == 0) {
            nextSecondary = currentSecondary == ID.Emotion.NORMAL
                    ? ID.Emotion.BORED : ID.Emotion.NORMAL;
        }
        return new ExpressionTransition(nextPrimary, nextSecondary);
    }

    static ReactionTransition nextReaction(Policy policy, int type, int currentCooldown,
            BoundedRandom random) {
        return policy == Policy.FRIENDLY
                ? nextFriendlyReaction(type, currentCooldown, random)
                : nextHostileReaction(type, currentCooldown, random);
    }

    private static ReactionTransition nextFriendlyReaction(int type, int currentCooldown,
            BoundedRandom random) {
        return switch (type) {
            case 1 -> rollReaction(random, 9, currentCooldown, 60, Reaction.STRANGER);
            case 2 -> currentCooldown <= 10
                    ? new ReactionTransition(Reaction.DAMAGED, 40)
                    : noReaction(currentCooldown);
            case 3 -> rollReaction(random, 6, currentCooldown, 60, Reaction.ATTACK);
            case 4 -> rollReaction(random, 3, currentCooldown, 20, Reaction.IDLE);
            case 5 -> rollReaction(random, 3, currentCooldown, 25, Reaction.COMMAND);
            case 6 -> new ReactionTransition(Reaction.SHOCK, currentCooldown);
            default -> rollReaction(random, 7, currentCooldown, 50, Reaction.NORMAL);
        };
    }

    private static ReactionTransition nextHostileReaction(int type, int currentCooldown,
            BoundedRandom random) {
        return switch (type) {
            case 2 -> currentCooldown <= 0
                    ? new ReactionTransition(Reaction.DAMAGED, 40)
                    : noReaction(currentCooldown);
            case 3 -> rollReaction(random, 7, currentCooldown, 60, Reaction.ATTACK);
            case 6 -> new ReactionTransition(Reaction.SHOCK, currentCooldown);
            default -> rollReaction(random, 3, currentCooldown, 20, Reaction.IDLE);
        };
    }

    private static ReactionTransition rollReaction(BoundedRandom random, int bound,
            int currentCooldown, int nextCooldown, Reaction reaction) {
        // Original 1.10.2 condition order is random first, cooldown second:
        // if (ran.nextInt(9) == 0 && this.getEmotesTick() <= 0)
        boolean rolled = random.nextInt(bound) == 0;
        return rolled && currentCooldown <= 0
                ? new ReactionTransition(reaction, nextCooldown)
                : noReaction(currentCooldown);
    }

    private static ReactionTransition noReaction(int currentCooldown) {
        return new ReactionTransition(Reaction.NONE, currentCooldown);
    }
}
