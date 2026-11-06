package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.action.ActionInhibitReason;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipResourceRulesTest {
    @Test
    void liquidPeriodsKeepEveryLevelBoundaryAndXpIsIndependent() {
        int[] levels = {29, 30, 74, 75, 114, 115, 144, 145};
        int[] periods = {64, 32, 32, 16, 16, 8, 8, 4};
        for (int i = 0; i < levels.length; i++) {
            for (int tick = 0; tick < 256; tick++) {
                var decision = ShipResourceRules.decide(facts(levels[i], tick));
                assertEquals(tick % periods[i] == 0, decision.liquidDue());
                assertEquals(tick % 4 == 0, decision.xpDue());
            }
        }
    }

    @Test
    void allInhibitionsAreExplainedTogether() {
        var facts = new ShipResourceRules.PumpFacts(false, true, true,
                Set.of(ActionInhibitReason.DEAD, ActionInhibitReason.NO_FUEL), false, true, false, 145, 64);
        var decision = ShipResourceRules.decide(facts);
        assertEquals(Set.of(ShipResourceRules.InhibitReason.DISABLED, ShipResourceRules.InhibitReason.SITTING,
                ShipResourceRules.InhibitReason.STOPPED, ShipResourceRules.InhibitReason.DEAD,
                ShipResourceRules.InhibitReason.NO_FUEL, ShipResourceRules.InhibitReason.MISSING_DRUM), decision.reasons());
        assertFalse(decision.liquidDue());
        assertFalse(decision.xpDue());
    }

    @Test
    void onlyMarriedTransportMayOmitDrum() {
        for (boolean transport : List.of(false, true)) {
            for (boolean married : List.of(false, true)) {
                var facts = new ShipResourceRules.PumpFacts(true, false, false, Set.of(),
                        transport, married, false, 145, 64);
                assertEquals(transport && married, ShipResourceRules.decide(facts).allowed());
            }
        }
    }

    @Test
    void factsAndDecisionsDoNotAliasTheCallersReasonSet() {
        Set<ActionInhibitReason> reasons = EnumSet.of(ActionInhibitReason.NO_FUEL);
        var facts = new ShipResourceRules.PumpFacts(true, false, false, reasons, true, true, false, 145, 64);
        reasons.clear();
        assertEquals(Set.of(ShipResourceRules.InhibitReason.NO_FUEL), ShipResourceRules.decide(facts).reasons());
    }

    @Test
    void infiniteNeedsPositiveDepthAndThreeSuccessfulColumns() {
        assertFalse(ShipResourceRules.infinite(0, List.of(true, true, true)));
        assertFalse(ShipResourceRules.infinite(-1, List.of(true, true, true)));
        assertTrue(ShipResourceRules.infinite(1, List.of(true, true, true)));
        assertFalse(ShipResourceRules.infinite(1, List.of(true, false, true)));
    }

    @Test
    void rewardIsPerCommittedTransferNotVolumeOrContainer() {
        assertEquals(7, ShipResourceRules.liquidReward(true, true, 7));
        assertEquals(0, ShipResourceRules.liquidReward(false, true, 7));
        assertEquals(0, ShipResourceRules.liquidReward(true, false, 7));
        assertEquals(0, ShipResourceRules.liquidReward(true, true, 0));
    }

    @Test
    void pickupRewardsOneSuccessfulStorageRegardlessOfStackSize() {
        assertEquals(0, ShipResourceRules.pickupReward(new ShipResourceRules.PickupResult(0), 7));
        assertEquals(7, ShipResourceRules.pickupReward(new ShipResourceRules.PickupResult(1), 7));
        assertEquals(7, ShipResourceRules.pickupReward(new ShipResourceRules.PickupResult(64), 7));
        assertEquals(0, ShipResourceRules.pickupReward(new ShipResourceRules.PickupResult(64), 0));
        assertThrows(IllegalArgumentException.class, () -> new ShipResourceRules.PickupResult(-1));
    }

    @Test
    void mergedOrbsAreCountedWithoutOverflowAndBottlesUseEightXp() {
        assertEquals(13, ShipResourceRules.collectedXp(7, 2, 3).orElseThrow());
        assertTrue(ShipResourceRules.collectedXp(-1, 8, 1).isEmpty());
        assertTrue(ShipResourceRules.collectedXp(Integer.MAX_VALUE - 5, 2, 3).isEmpty());
        assertTrue(ShipResourceRules.collectedXp(7, 2, 0).isEmpty());
        assertTrue(ShipResourceRules.collectedXp(7, 2, Integer.MAX_VALUE).isEmpty());
        assertFalse(ShipResourceRules.canBottle(7));
        assertTrue(ShipResourceRules.canBottle(8));
        assertTrue(ShipResourceRules.canBottle(16));
    }

    private static ShipResourceRules.PumpFacts facts(int level, int tick) {
        return new ShipResourceRules.PumpFacts(true, false, false, Set.of(), false, false, true, level, tick);
    }
}
