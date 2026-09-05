package com.lulan.shincolle.entity;

/** Pure rules for configurable ship level caps and level-normalized task values. */
public final class ShipLevelRules {

    /** Original natural level cap for an unmarried ship and the common-config default. */
    public static final int DEFAULT_UNMARRIED_CAP = 100;
    /** Original married and absolute level cap and the common-config default. */
    public static final int DEFAULT_ABSOLUTE_CAP = 150;
    /** Highest configured cap covered by the supported-behavior verification suite. */
    public static final int GUARANTEED_CAP = 1000;

    private ShipLevelRules() {
    }

    public static int naturalLevelCap(boolean married, int unmarriedCap, int absoluteCap) {
        return married ? absoluteCap : Math.min(unmarriedCap, absoluteCap);
    }

    public static boolean acceptsLevel(int requestedLevel, int absoluteCap) {
        return requestedLevel <= absoluteCap;
    }

    public static boolean canGainExperience(int currentLevel, boolean married,
                                            int unmarriedCap, int absoluteCap) {
        int naturalCap = naturalLevelCap(married, unmarriedCap, absoluteCap);
        return currentLevel != naturalCap && currentLevel < absoluteCap;
    }

    public static boolean canUseTrainingBook(int currentLevel, int absoluteCap) {
        return currentLevel < absoluteCap;
    }

    public static int trainingBookResult(int currentLevel, int gainedLevels, boolean married,
                                         int unmarriedCap, int absoluteCap) {
        int naturalCap = naturalLevelCap(married, unmarriedCap, absoluteCap);
        return Math.min(currentLevel + gainedLevels, naturalCap);
    }

    public static float expeditionFailureChance(int level, int absoluteCap) {
        return (float) (absoluteCap - level) / (float) absoluteCap * 0.2F + 0.05F;
    }

    public static float expeditionLuckContribution(int level, int absoluteCap) {
        return (float) level / (float) absoluteCap * 1.5F;
    }
}
