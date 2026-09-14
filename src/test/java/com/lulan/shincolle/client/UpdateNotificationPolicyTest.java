package com.lulan.shincolle.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateNotificationPolicyTest {

    private static final List<Integer> DEFAULT_LAUNCHES = List.of(1, 2, 4, 8, 16);

    @Test
    void defaultScheduleNotifiesOnlyOnConfiguredLaunches() {
        UpdateNoticeState state = null;
        List<Integer> notified = new ArrayList<>();

        for (int launch = 1; launch <= 20; launch++) {
            UpdateNotificationPolicy.Decision decision = UpdateNotificationPolicy.evaluate(
                    true, DEFAULT_LAUNCHES, state, "1.20.1-0.9.1", true);
            state = decision.state();
            if (decision.shouldNotify()) {
                notified.add(launch);
            }
        }

        assertEquals(DEFAULT_LAUNCHES, notified);
        assertEquals(new UpdateNoticeState("1.20.1-0.9.1", 20, 16), state);
    }

    @Test
    void remainsSilentAfterLargestConfiguredLaunch() {
        UpdateNoticeState state = new UpdateNoticeState("1.20.1-0.9.1", 16, 16);

        for (int launch = 17; launch <= 100; launch++) {
            UpdateNotificationPolicy.Decision decision = UpdateNotificationPolicy.evaluate(
                    true, DEFAULT_LAUNCHES, state, "1.20.1-0.9.1", true);
            assertFalse(decision.shouldNotify());
            state = decision.state();
        }

        assertEquals(new UpdateNoticeState("1.20.1-0.9.1", 100, 16), state);
    }

    @Test
    void aDifferentDetectedVersionRestartsAtFirstLaunch() {
        UpdateNoticeState oldState = new UpdateNoticeState("1.20.1-0.9.1", 20, 16);

        UpdateNotificationPolicy.Decision decision = UpdateNotificationPolicy.evaluate(
                true, DEFAULT_LAUNCHES, oldState, "1.20.1-0.9.2", true);

        assertTrue(decision.shouldNotify());
        assertEquals(new UpdateNoticeState("1.20.1-0.9.2", 1, 1), decision.state());
    }

    @Test
    void disabledFeatureDoesNotCreateOrAdvanceState() {
        UpdateNotificationPolicy.Decision noState = UpdateNotificationPolicy.evaluate(
                false, DEFAULT_LAUNCHES, null, "1.20.1-0.9.1", true);
        UpdateNoticeState saved = new UpdateNoticeState("1.20.1-0.9.1", 2, 2);
        UpdateNotificationPolicy.Decision existingState = UpdateNotificationPolicy.evaluate(
                false, DEFAULT_LAUNCHES, saved, "1.20.1-0.9.1", true);

        assertFalse(noState.shouldNotify());
        assertNull(noState.state());
        assertFalse(existingState.shouldNotify());
        assertEquals(saved, existingState.state());
    }

    @Test
    void emptyOrUnusableScheduleAdvancesStateWithoutNotifying() {
        UpdateNotificationPolicy.Decision empty = UpdateNotificationPolicy.evaluate(
                true, List.of(), null, "1.20.1-0.9.1", true);
        UpdateNotificationPolicy.Decision normalized = UpdateNotificationPolicy.evaluate(
                true, List.of(2, -1, 2, 0, 1), empty.state(), "1.20.1-0.9.1", true);

        assertFalse(empty.shouldNotify());
        assertEquals(new UpdateNoticeState("1.20.1-0.9.1", 1, 0), empty.state());
        assertTrue(normalized.shouldNotify());
        assertEquals(new UpdateNoticeState("1.20.1-0.9.1", 2, 2), normalized.state());
    }

    @Test
    void nonTargetStatusDoesNotCreateOrAdvanceState() {
        UpdateNoticeState saved = new UpdateNoticeState("1.20.1-0.9.1", 2, 2);

        UpdateNotificationPolicy.Decision noState = UpdateNotificationPolicy.evaluate(
                true, DEFAULT_LAUNCHES, null, "1.20.1-0.9.1", false);
        UpdateNotificationPolicy.Decision existingState = UpdateNotificationPolicy.evaluate(
                true, DEFAULT_LAUNCHES, saved, "1.20.1-0.9.1", false);

        assertFalse(noState.shouldNotify());
        assertNull(noState.state());
        assertFalse(existingState.shouldNotify());
        assertEquals(saved, existingState.state());
    }
}
