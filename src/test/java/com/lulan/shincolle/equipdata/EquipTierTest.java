package com.lulan.shincolle.equipdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the rare_mean band boundaries the whole tier presentation hangs off. */
class EquipTierTest {

    @Test
    void bandBoundariesMapToTheExpectedTier() {
        assertEquals(EquipTier.BASIC, EquipTier.fromRareMean(200));
        assertEquals(EquipTier.BASIC, EquipTier.fromRareMean(1000));
        assertEquals(EquipTier.IMPROVED, EquipTier.fromRareMean(1001));
        assertEquals(EquipTier.IMPROVED, EquipTier.fromRareMean(2600));
        assertEquals(EquipTier.ADVANCED, EquipTier.fromRareMean(2601));
        assertEquals(EquipTier.ADVANCED, EquipTier.fromRareMean(3600));
        assertEquals(EquipTier.ELITE, EquipTier.fromRareMean(3601));
        assertEquals(EquipTier.ELITE, EquipTier.fromRareMean(4500));
    }

    @Test
    void shippedDefinitionValuesLandInTheIntendedBands() {
        // The four clusters the shipped data actually uses.
        assertEquals(EquipTier.BASIC, EquipTier.fromRareMean(1000));
        assertEquals(EquipTier.IMPROVED, EquipTier.fromRareMean(2000));
        assertEquals(EquipTier.IMPROVED, EquipTier.fromRareMean(2400));
        assertEquals(EquipTier.ADVANCED, EquipTier.fromRareMean(3000));
        assertEquals(EquipTier.ADVANCED, EquipTier.fromRareMean(3200));
        assertEquals(EquipTier.ELITE, EquipTier.fromRareMean(4000));
        assertEquals(EquipTier.ELITE, EquipTier.fromRareMean(4400));
    }

    @Test
    void starsAreOneToFourAndRenderAsFilledAndEmpty() {
        assertEquals(1, EquipTier.BASIC.stars());
        assertEquals(4, EquipTier.ELITE.stars());
        assertEquals("★☆☆☆", EquipTier.BASIC.starText());
        assertEquals("★★☆☆", EquipTier.IMPROVED.starText());
        assertEquals("★★★☆", EquipTier.ADVANCED.starText());
        assertEquals("★★★★", EquipTier.ELITE.starText());
    }

    @Test
    void unknownDefinitionFallsBackToTheLowestTier() {
        assertEquals(EquipTier.BASIC, EquipTier.of(null));
    }
}
