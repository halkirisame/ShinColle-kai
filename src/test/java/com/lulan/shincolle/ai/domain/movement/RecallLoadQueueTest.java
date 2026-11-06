package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.CommandStateReducer;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.command.ShipCommandState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Observation.PENDING;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Observation.ENTITIES_LOADED_WITHOUT_SHIP;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Result.ABSENT_AFTER_LOAD;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Result.CANCELLED;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Result.LOAD_BUDGET_EXCEEDED;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Result.QUEUE_BUDGET_EXCEEDED;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Result.REGISTERED;
import static com.lulan.shincolle.ai.domain.movement.RecallLoadQueue.Result.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecallLoadQueueTest {
    private static final DimensionKey WORLD = new DimensionKey("minecraft", "overworld");
    private static final UUID RUN = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static RecallRequestKey key(UUID owner, int ship) {
        return new RecallRequestKey(RUN, owner, 1, new UUID(0, ship), ship, ship);
    }
    private static RecallLoadQueue.Clock at(long tick) { return new RecallLoadQueue.Clock(tick, tick * 1_000L); }
    private static RecallLoadQueue.Chunk chunk(int x) { return new RecallLoadQueue.Chunk(WORLD, x, 0); }

    @Test
    void pendingPastTwoHundredTicksRegistersBeforeTheBudgetAndReleasesItsLease() {
        var queue = new RecallLoadQueue();
        var key = key(OWNER, 1);
        assertTrue(queue.enqueue(key, chunk(1), at(0)));
        queue.promote(at(0));
        assertEquals(WAIT, queue.observe(key, PENDING, at(220)));
        assertEquals(1, queue.leases().size());
        assertEquals(REGISTERED, queue.observe(key, RecallLoadQueue.Observation.REGISTERED, at(260)));
        assertTrue(queue.leases().isEmpty());
        assertEquals(CANCELLED, queue.observe(key, RecallLoadQueue.Observation.REGISTERED, at(261)));
    }

    @Test
    void absenceNeedsAnActiveLeaseAndEntityCompletionNotJustTime() {
        var queue = new RecallLoadQueue();
        var key = key(OWNER, 1);
        queue.enqueue(key, chunk(1), at(0));
        assertEquals(WAIT, queue.observe(key, ENTITIES_LOADED_WITHOUT_SHIP, at(1)));
        queue.promote(at(1));
        assertEquals(ABSENT_AFTER_LOAD, queue.observe(key, ENTITIES_LOADED_WITHOUT_SHIP, at(2)));
        assertTrue(queue.leases().isEmpty());
    }

    @Test
    void tickDeadlineWinsOverRegistrationAtExactlyTwelveHundredTicks() {
        var queue = new RecallLoadQueue();
        var key = key(OWNER, 1);
        queue.enqueue(key, chunk(1), at(0));
        queue.promote(at(0));
        assertEquals(WAIT, queue.observe(key, PENDING, at(1199)));
        assertEquals(LOAD_BUDGET_EXCEEDED, queue.observe(key, RecallLoadQueue.Observation.REGISTERED, at(1200)));
        assertTrue(queue.leases().isEmpty());
    }

    @Test
    void realTimeDeadlineIsIndependentOfServerTickRate() {
        var queue = new RecallLoadQueue();
        var key = key(OWNER, 1);
        queue.enqueue(key, chunk(1), at(0));
        queue.promote(at(0));
        assertEquals(WAIT, queue.observe(key, PENDING, new RecallLoadQueue.Clock(1, 119_999_999_999L)));
        assertEquals(LOAD_BUDGET_EXCEEDED, queue.observe(key, RecallLoadQueue.Observation.REGISTERED,
                new RecallLoadQueue.Clock(1, 120_000_000_000L)));
    }

    @Test
    void cancellationReturnsTheTicketSlotAndLateRegistrationCannotReviveIt() {
        var queue = new RecallLoadQueue();
        for (int i = 1; i <= 5; i++) queue.enqueue(key(OWNER, i), chunk(i), at(0));
        queue.promote(at(0));
        assertEquals(4, queue.ownerLeases(OWNER));
        assertEquals(RecallLoadQueue.Phase.QUEUED, queue.view(key(OWNER, 5)).orElseThrow().phase());
        queue.forget(key(OWNER, 1));
        queue.promote(at(1));
        assertEquals(CANCELLED, queue.observe(key(OWNER, 1), RecallLoadQueue.Observation.REGISTERED, at(2)));
        assertEquals(RecallLoadQueue.Phase.LOADING, queue.view(key(OWNER, 5)).orElseThrow().phase());
        assertEquals(4, queue.leases().size());
    }

    @Test
    void sharedChunkUsesOneGlobalSlotAndEachParticipatingOwnersSlot() {
        var queue = new RecallLoadQueue();
        UUID other = UUID.randomUUID();
        queue.enqueue(key(OWNER, 1), chunk(1), at(0));
        queue.enqueue(key(OWNER, 2), chunk(1), at(0));
        queue.enqueue(key(other, 3), chunk(1), at(0));
        queue.promote(at(0));
        assertEquals(1, queue.leases().size());
        assertEquals(1, queue.ownerLeases(OWNER));
        assertEquals(1, queue.ownerLeases(other));
        queue.forget(key(OWNER, 1));
        queue.forget(key(OWNER, 2));
        assertEquals(1, queue.leases().size());
        queue.forget(key(other, 3));
        assertTrue(queue.leases().isEmpty());
    }

    @Test
    void aLateParticipantInheritsTheExistingLeaseDeadline() {
        var queue = new RecallLoadQueue();
        queue.enqueue(key(OWNER, 1), chunk(1), at(0));
        queue.promote(at(0));
        queue.enqueue(key(UUID.randomUUID(), 2), chunk(1), at(1190));
        var late = queue.pending().get(1);
        queue.promote(at(1190));
        assertEquals(LOAD_BUDGET_EXCEEDED, queue.observe(late, RecallLoadQueue.Observation.REGISTERED, at(1200)));
    }

    @Test
    void sixteenGlobalSlotsIncludeDifferentDimensionsAndFairOwnerRounds() {
        var queue = new RecallLoadQueue();
        for (int owner = 1; owner <= 5; owner++) {
            UUID id = new UUID(0, owner);
            for (int ship = 1; ship <= 5; ship++) {
                var dimension = owner == 5 ? new DimensionKey("minecraft", "the_nether") : WORLD;
                queue.enqueue(key(id, owner * 10 + ship), new RecallLoadQueue.Chunk(dimension, owner * 10 + ship, 0), at(0));
            }
        }
        queue.promote(at(0));
        assertEquals(16, queue.leases().size());
        for (int owner = 1; owner <= 5; owner++) {
            assertTrue(queue.ownerLeases(new UUID(0, owner)) >= 3);
            assertTrue(queue.ownerLeases(new UUID(0, owner)) <= 4);
        }
    }

    @Test
    void queuedLimitAndQueueTimeoutNeverAcquireAnExtraTicket() {
        var queue = new RecallLoadQueue(new RecallLoadQueue.Budget(1200, 120_000_000_000L, 1, 1, 2));
        queue.enqueue(key(OWNER, 1), chunk(1), at(0));
        queue.promote(at(0));
        assertTrue(queue.enqueue(key(OWNER, 2), chunk(2), at(0)));
        assertTrue(queue.enqueue(key(OWNER, 3), chunk(3), at(0)));
        assertFalse(queue.enqueue(key(OWNER, 4), chunk(4), at(0)));
        queue.promote(at(0));
        assertEquals(QUEUE_BUDGET_EXCEEDED, queue.observe(key(OWNER, 2), PENDING, at(1200)));
        assertEquals(1, queue.leases().size());
    }

    @Test
    void reloadingTheSameRosterNeverRestartsItsCumulativeBudget() {
        var queue = new RecallLoadQueue();
        var key = key(OWNER, 1);
        queue.enqueue(key, chunk(1), at(0));
        queue.promote(at(0));
        assertEquals(REGISTERED, queue.observe(key, RecallLoadQueue.Observation.REGISTERED, at(100)));
        queue.enqueue(key, chunk(2), at(1100));
        queue.promote(at(1100));
        assertEquals(LOAD_BUDGET_EXCEEDED, queue.observe(key, RecallLoadQueue.Observation.REGISTERED, at(1200)));
    }

    @Test
    void aNewLoginShipOrGenerationIsNotTheOldRequest() {
        var key = key(OWNER, 1);
        assertNotEquals(key, new RecallRequestKey(RUN, OWNER, 2, key.ship(), 1, 1));
        assertNotEquals(key, new RecallRequestKey(RUN, OWNER, 1, key.ship(), 1, 2));
        assertNotEquals(key, key(OWNER, 2));
        assertNotEquals(key, new RecallRequestKey(UUID.randomUUID(), OWNER, 1, key.ship(), 1, 1));
    }

    @Test
    void sittingCancelsEvenWhenMovementDidNotChangeButAttackCancellationDoesNot() {
        assertFalse(RecallCancellation.commandCancels(ShipCommandState.INITIAL));
        var sitting = CommandStateReducer.apply(ShipCommandState.INITIAL,
                new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
        assertEquals(ShipCommandState.INITIAL.movement(), sitting.movement());
        assertTrue(RecallCancellation.commandCancels(sitting));
        var cancelAttack = CommandStateReducer.apply(ShipCommandState.INITIAL,
                new CommandStateOp.Apply(new ShipCommand.CancelAttack()));
        assertFalse(RecallCancellation.commandCancels(cancelAttack));
    }

    @Test
    void noticesAggregateWithoutExtendingTheWindowAndIgnoreNonFailures() {
        var notice = RecallNoticeWindow.empty(100).add(ABSENT_AFTER_LOAD).add(REGISTERED).add(WAIT).add(CANCELLED);
        notice = notice.add(LOAD_BUDGET_EXCEEDED).add(QUEUE_BUDGET_EXCEEDED);
        assertEquals(1, notice.absent());
        assertEquals(0, notice.failures());
        assertEquals(2, notice.limits());
        assertFalse(notice.due(119));
        assertTrue(notice.due(120));
        assertFalse(RecallNoticeWindow.empty(0).add(REGISTERED).due(1000));
    }
}
