package com.lulan.shincolle.ai.domain;

public record TargetPredicatePolicy(
        boolean pvpFirst,
        boolean antiAir,
        boolean antiSubmarine,
        int shipAttackPlayer,
        boolean mobShipsAttackPlayer) {

    public static TargetPredicatePolicy neutral() {
        return new TargetPredicatePolicy(false, false, false, 0, false);
    }
}
