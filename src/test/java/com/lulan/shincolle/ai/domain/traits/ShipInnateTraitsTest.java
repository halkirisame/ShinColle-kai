package com.lulan.shincolle.ai.domain.traits;

import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipInnateTraitsTest {
    @Test
    void smallTrainingRewardsRetainEveryFifthOfExperience() {
        for (int baseline : new int[]{2, 4}) {
            int total = 0;
            var remainder = ShipInnateTraits.TrainingRemainder.ZERO;
            for (int i = 0; i < 5; i++) {
                var award = ShipInnateTraits.trainingAward(baseline, remainder);
                total += award.experience();
                remainder = award.remainder();
            }
            assertEquals(baseline * 6, total);
            assertEquals(ShipInnateTraits.TrainingRemainder.ZERO, remainder);
        }
        assertEquals(12, ShipInnateTraits.trainingAward(10, ShipInnateTraits.TrainingRemainder.ZERO).experience());
        // The existing XP multiplier is applied, and truncated, before this rule.
        assertEquals(18, ShipInnateTraits.trainingAward((int) (10 * 1.5F),
                ShipInnateTraits.TrainingRemainder.ZERO).experience());
    }

    @Test
    void zeroAndNegativeAwardsDoNotConsumeTheRemainderAndLargeAwardsSaturate() {
        var remainder = new ShipInnateTraits.TrainingRemainder(4);
        for (int baseline : new int[]{0, -1, Integer.MIN_VALUE}) {
            var award = ShipInnateTraits.trainingAward(baseline, remainder);
            assertEquals(baseline, award.experience());
            assertEquals(remainder, award.remainder());
        }
        var large = ShipInnateTraits.trainingAward(Integer.MAX_VALUE, remainder);
        assertEquals(Integer.MAX_VALUE, large.experience());
        assertEquals((int) ((Integer.MAX_VALUE * 6L + 4) % 5), large.remainder().fifths());
    }

    @Test
    void remainderIsValidatedOnceAtTheLoadBoundary() {
        for (int value : new int[]{-1, 5, Integer.MAX_VALUE}) {
            assertEquals(ShipInnateTraits.TrainingRemainder.ZERO,
                    ShipInnateTraits.TrainingRemainder.loaded(true, value));
        }
        assertEquals(ShipInnateTraits.TrainingRemainder.ZERO,
                ShipInnateTraits.TrainingRemainder.loaded(false, 3));
        for (int value = 0; value <= 4; value++) {
            assertEquals(value, ShipInnateTraits.TrainingRemainder.loaded(true, value).fifths());
        }
    }

    @Test
    void onlyActiveFriendlyNewProfilesHaveInnateTraits() {
        for (var profile : ShipInnateTraits.Profile.values()) {
            assertEquals(ShipInnateTraits.Profile.NONE, profile.enabled(false, true));
            assertEquals(ShipInnateTraits.Profile.NONE, profile.enabled(true, false));
        }
        assertEquals(ShipInnateTraits.Profile.YUKIKAZE,
                ShipInnateTraits.Profile.YUKIKAZE.enabled(true, true));
    }

    @Test
    void trainingRequiresAllSourceConditionsButSelfDoesNotRequireAFleet() {
        var ready = new ShipInnateTraits.TrainingSource(true, true, true, true, true, true, true, false);
        assertTrue(ShipInnateTraits.supportsTraining(ready));
        assertTrue(ShipInnateTraits.supportsTraining(
                new ShipInnateTraits.TrainingSource(true, true, true, true, true, true, false, true)));
        for (int missing = 0; missing < 7; missing++) {
            boolean[] facts = {true, true, true, true, true, true, true};
            facts[missing] = false;
            assertFalse(ShipInnateTraits.supportsTraining(new ShipInnateTraits.TrainingSource(
                    facts[0], facts[1], facts[2], facts[3], facts[4], facts[5], facts[6], false)));
        }
    }

    @Test
    void yukikazeShortensTheWholePostAttackDelayButNotAirOrOtherShips() {
        var profile = ShipInnateTraits.Profile.YUKIKAZE;
        assertEquals(91, profile.postAttackDelay(WeaponChannel.LIGHT, 100));
        assertEquals(155, profile.postAttackDelay(WeaponChannel.HEAVY, 170));
        assertEquals(36, profile.postAttackDelay(WeaponChannel.MELEE, 40));
        assertEquals(5, profile.postAttackDelay(WeaponChannel.LIGHT, 5));
        assertEquals(10, profile.postAttackDelay(WeaponChannel.HEAVY, 10));
        assertEquals(1, profile.postAttackDelay(WeaponChannel.MELEE, 0));
        assertEquals(170, profile.postAttackDelay(WeaponChannel.AIR, 170));
        for (var other : new ShipInnateTraits.Profile[]{ShipInnateTraits.Profile.NONE,
                ShipInnateTraits.Profile.KASHIMA, profile.enabled(false, true)}) {
            for (var channel : WeaponChannel.values()) {
                assertEquals(170, other.postAttackDelay(channel, 170));
            }
            assertEquals(0.2F, other.rawDodge(0.2F));
        }
        assertEquals(0.35F, profile.rawDodge(0.2F), 0.00001F);
    }

    @Test
    void firstShotTimingIsUntouchedAndOnlyTheFiredChannelChanges() {
        var initial = CombatTimingReducer.initial(100);
        assertEquals(119, initial.readyAt(WeaponChannel.LIGHT));
        assertEquals(139, initial.readyAt(WeaponChannel.HEAVY));
        assertEquals(119, initial.readyAt(WeaponChannel.MELEE));
        var fired = CombatTimingReducer.onFired(initial, WeaponChannel.LIGHT,
                ShipInnateTraits.Profile.YUKIKAZE.postAttackDelay(WeaponChannel.LIGHT, 100), 200);
        assertEquals(291, fired.readyAt(WeaponChannel.LIGHT));
        assertEquals(initial.readyAt(WeaponChannel.HEAVY), fired.readyAt(WeaponChannel.HEAVY));
        assertEquals(initial.aimTicks(), fired.aimTicks());
    }

    @Test
    void bundledStatsAndGrowthKeepTheSupportAndAgilityRolesAtLevelOneAndOneHundred() throws IOException {
        String[] stats = {"hp", "atk", "def", "spd", "mov", "hit"};
        String[] ships = {"kashima", "yukikaze"};
        double[][] bases = {{40, 8, .08, 1, .36, 8}, {34, 10, .12, 1, .52, 7}};
        double[][] growths = {{.34, .32, .10, .5, .8, .4}, {.34, .4, .16, .5, 1, .4}};
        double[][] levelHundred = {{74, 12.256, .09330, 1.20, .52, 8.8},
                {68, 15.320, .14128, 1.20, .72, 7.8}};
        double[] coefficients = {1, .133, .00133, .004, .002, .02};
        for (int ship = 0; ship < ships.length; ship++) {
            var json = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/data/shincolle_kai/shincolle_kai/ships/" + ships[ship] + ".json")))
                    .getAsJsonObject();
            for (int stat = 0; stat < stats.length; stat++) {
                double base = json.getAsJsonObject("base_stats").get(stats[stat]).getAsDouble();
                double growth = json.getAsJsonObject("growth").get(stats[stat]).getAsDouble();
                assertEquals(bases[ship][stat], base, .0000001D);
                assertEquals(growths[ship][stat], growth, .0000001D);
                assertEquals(levelHundred[ship][stat], base + 100 * coefficients[stat] * growth, .0000001D);
                assertEquals(bases[ship][stat] + coefficients[stat] * growths[ship][stat],
                        base + coefficients[stat] * growth, .0000001D);
            }
        }
    }
}
