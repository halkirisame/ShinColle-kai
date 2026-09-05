package com.lulan.shincolle.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipLevelRulesTest {

    @Test
    void defaultCapsKeepOriginalUnmarriedAndMarriedLevels() {
        assertEquals(100, ShipLevelRules.naturalLevelCap(false, 100, 150));
        assertEquals(150, ShipLevelRules.naturalLevelCap(true, 100, 150));
    }

    @Test
    void unmarriedCapCannotExceedAbsoluteCap() {
        assertEquals(150, ShipLevelRules.naturalLevelCap(false, 200, 150));
        assertEquals(600, ShipLevelRules.naturalLevelCap(false, 600, 1000));
        assertEquals(1000, ShipLevelRules.naturalLevelCap(true, 600, 1000));
    }

    @Test
    void experiencePredicateKeepsOriginalTwoStageBehavior() {
        assertTrue(ShipLevelRules.canGainExperience(99, false, 100, 150));
        assertFalse(ShipLevelRules.canGainExperience(100, false, 100, 150));
        assertTrue(ShipLevelRules.canGainExperience(101, false, 100, 150));
        assertTrue(ShipLevelRules.canGainExperience(149, true, 100, 150));
        assertFalse(ShipLevelRules.canGainExperience(150, true, 100, 150));
        assertFalse(ShipLevelRules.canGainExperience(1000, true, 600, 1000));
    }

    @Test
    void setterAndTrainingRulesFollowConfiguredCaps() {
        assertTrue(ShipLevelRules.acceptsLevel(1000, 1000));
        assertFalse(ShipLevelRules.acceptsLevel(1001, 1000));
        assertTrue(ShipLevelRules.canUseTrainingBook(999, 1000));
        assertFalse(ShipLevelRules.canUseTrainingBook(1000, 1000));
        assertEquals(600, ShipLevelRules.trainingBookResult(598, 10, false, 600, 1000));
        assertEquals(1000, ShipLevelRules.trainingBookResult(998, 10, true, 600, 1000));
    }

    @Test
    void expeditionValuesRemainNormalizedByAbsoluteCap() {
        float legacyFailureAt75 = (float) (150 - 75) / (float) 150 * 0.2F + 0.05F;
        float legacyLuckAt75 = (float) 75 / 150 * 1.5F;
        assertEquals(Float.floatToIntBits(legacyFailureAt75),
                Float.floatToIntBits(ShipLevelRules.expeditionFailureChance(75, 150)));
        assertEquals(Float.floatToIntBits(legacyLuckAt75),
                Float.floatToIntBits(ShipLevelRules.expeditionLuckContribution(75, 150)));
        assertEquals(0.05F, ShipLevelRules.expeditionFailureChance(150, 150), 0.000001F);
        assertEquals(1.5F, ShipLevelRules.expeditionLuckContribution(150, 150), 0.000001F);
        assertEquals(0.05F, ShipLevelRules.expeditionFailureChance(1000, 1000), 0.000001F);
        assertEquals(1.5F, ShipLevelRules.expeditionLuckContribution(1000, 1000), 0.000001F);
    }
}
