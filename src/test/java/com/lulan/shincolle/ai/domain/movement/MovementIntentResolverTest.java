package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementIntentResolverTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final CommandPos POS = new CommandPos(1, 64, 2);

    /** A healthy, fuelled, unhindered ship following its owner 5 blocks away. */
    private static Facts base() {
        return new Facts();
    }

    private static final class Facts {
        MovementOrder order = new MovementOrder.Follow();
        boolean orderedToSit, sittingPose, hostIsMount;
        float hpRatio = 1F, fleeThreshold = 0.35F;
        boolean ownerPresent = true;
        double ownerDistanceSq = 25D;
        boolean hasGrudge = true, riding, ridingShip, leashed, crane, fishing, pickItemEnabled = true, engaged;

        MovementFacts build() {
            return new MovementFacts(order, orderedToSit, sittingPose, hostIsMount, hpRatio, fleeThreshold,
                    ownerPresent, ownerDistanceSq, hasGrudge, riding, ridingShip, leashed, crane, fishing,
                    pickItemEnabled, engaged);
        }

        Facts fleeing() {
            hpRatio = 0.2F;
            return this;
        }
    }

    private static MovementDecision resolve(Facts facts) {
        return MovementIntentResolver.resolve(facts.build());
    }

    @Test
    void ordersMapToIntents() {
        Facts facts = base();
        assertEquals(new MovementIntent.FollowOwner(), resolve(facts).intent());
        facts.order = new MovementOrder.MoveTo(OVERWORLD, POS, true);
        assertEquals(new MovementIntent.MoveTo(OVERWORLD, POS), resolve(facts).intent());
        facts.order = new MovementOrder.GuardPosition(OVERWORLD, POS, false);
        assertEquals(new MovementIntent.GuardPosition(OVERWORLD, POS), resolve(facts).intent());
        facts.order = new MovementOrder.GuardPosition(OVERWORLD, POS, true);
        assertEquals(new MovementIntent.MoveTo(OVERWORLD, POS), resolve(facts).intent());
        TargetHandle cow = new TargetHandle(UUID.randomUUID(), OVERWORLD);
        facts.order = new MovementOrder.GuardEntity(cow);
        assertEquals(new MovementIntent.GuardEntity(cow), resolve(facts).intent());
    }

    @Test
    void sitWinsOverFleeAndOrders() {
        Facts facts = base().fleeing();
        facts.order = new MovementOrder.GuardPosition(OVERWORLD, POS, false);
        facts.orderedToSit = true;
        assertEquals(new MovementIntent.Sit(), resolve(facts).intent());
    }

    @Test
    void fleeWinsOverOrdersOnlyInsideItsConditions() {
        Facts facts = base().fleeing();
        facts.order = new MovementOrder.GuardPosition(OVERWORLD, POS, false);
        assertEquals(new MovementIntent.Flee(), resolve(facts).intent());

        // beside the owner the ship keeps fleeing, so it stays there instead of walking back
        facts.ownerDistanceSq = 0D;
        assertEquals(new MovementIntent.Flee(), resolve(facts).intent());
        facts.ownerDistanceSq = 3600D;
        assertNotFleeing(facts);
        facts.ownerDistanceSq = 3599.9D;
        assertEquals(new MovementIntent.Flee(), resolve(facts).intent());

        Facts healthy = base();
        healthy.hpRatio = 0.35F;
        assertEquals(new MovementIntent.Flee(), resolve(healthy).intent(), "the threshold itself flees");
        healthy.hpRatio = 0.36F;
        assertEquals(new MovementIntent.FollowOwner(), resolve(healthy).intent());

        Facts leashed = base().fleeing();
        leashed.leashed = true;
        assertNotFleeing(leashed);
        Facts dry = base().fleeing();
        dry.hasGrudge = false;
        assertNotFleeing(dry);
        Facts alone = base().fleeing();
        alone.ownerPresent = false;
        assertNotFleeing(alone);
        Facts mount = base().fleeing();
        mount.hostIsMount = true;
        assertNotFleeing(mount);
    }

    private static void assertNotFleeing(Facts facts) {
        assertNotEquals(new MovementIntent.Flee(), resolve(facts).intent());
    }

    @Test
    void commandedMoveInhibitors() {
        for (MovementOrder order : new MovementOrder[]{new MovementOrder.GuardPosition(OVERWORLD, POS, false),
                new MovementOrder.GuardPosition(OVERWORLD, POS, true), new MovementOrder.MoveTo(OVERWORLD, POS, true)}) {
            Facts guard = base();
            guard.order = order;
            guard.leashed = true;
            assertEquals(Set.of(MovementInhibitReason.LEASHED),
                    resolve(guard).get(MovementActivity.COMMANDED_MOVE).reasons(), "a leash stops " + order);
        }

        Facts follow = base();
        follow.leashed = true;
        assertEquals(Set.of(MovementInhibitReason.LEASHED),
                resolve(follow).get(MovementActivity.COMMANDED_MOVE).reasons());

        Facts all = base();
        all.sittingPose = true;
        all.riding = true;
        all.crane = true;
        all.hasGrudge = false;
        assertEquals(Set.of(MovementInhibitReason.SITTING, MovementInhibitReason.RIDING,
                        MovementInhibitReason.CRANE, MovementInhibitReason.NO_GRUDGE),
                resolve(all).get(MovementActivity.COMMANDED_MOVE).reasons());
    }

    @Test
    void idleActivitiesKeepTheirOwnInhibitors() {
        Facts facts = base();
        facts.orderedToSit = true;
        facts.riding = true;
        facts.fishing = true;
        facts.crane = true;
        facts.pickItemEnabled = false;
        facts.ridingShip = true;
        MovementDecision decision = resolve(facts);
        assertEquals(Set.of(MovementInhibitReason.SITTING, MovementInhibitReason.RIDING,
                MovementInhibitReason.FISHING, MovementInhibitReason.CRANE),
                decision.get(MovementActivity.WANDER).reasons());
        assertEquals(Set.of(MovementInhibitReason.SITTING, MovementInhibitReason.RIDING,
                MovementInhibitReason.PICK_ITEM_OFF, MovementInhibitReason.CRANE, MovementInhibitReason.FISHING),
                decision.get(MovementActivity.PICK_ITEM).reasons());
        assertEquals(Set.of(MovementInhibitReason.RIDING_SHIP), decision.get(MovementActivity.IDLE_LOOK).reasons());

        MovementDecision free = resolve(base());
        for (MovementActivity activity : MovementActivity.values()) {
            assertTrue(free.allows(activity), activity.name());
        }
    }

    @Test
    void anEngagedShipDoesNotLookAround() {
        Facts facts = base();
        facts.engaged = true;
        MovementDecision decision = resolve(facts);
        assertEquals(Set.of(MovementInhibitReason.ENGAGED), decision.get(MovementActivity.IDLE_LOOK).reasons());
        assertTrue(decision.allows(MovementActivity.WANDER));
        assertTrue(decision.allows(MovementActivity.PICK_ITEM));
    }

    @Test
    void anEngagedShipOnAShipHasBothLookInhibitors() {
        Facts facts = base();
        facts.engaged = true;
        facts.ridingShip = true;
        assertEquals(Set.of(MovementInhibitReason.ENGAGED, MovementInhibitReason.RIDING_SHIP),
                resolve(facts).get(MovementActivity.IDLE_LOOK).reasons());
    }

    @Test
    void aShipThatIsNotEngagedStillLooksAround() {
        assertTrue(resolve(base()).allows(MovementActivity.IDLE_LOOK));
    }

    @Test
    void fleeingStopsOnlyWanderingAndPickingUp() {
        MovementDecision decision = resolve(base().fleeing());
        assertEquals(new MovementIntent.Flee(), decision.intent());
        assertEquals(Set.of(MovementInhibitReason.FLEEING), decision.get(MovementActivity.WANDER).reasons());
        assertEquals(Set.of(MovementInhibitReason.FLEEING), decision.get(MovementActivity.PICK_ITEM).reasons());
        assertTrue(decision.allows(MovementActivity.IDLE_LOOK));
    }

    @Test
    void theSittingPoseAloneDoesNotStopIdleActivities() {
        Facts facts = base();
        facts.sittingPose = true;
        MovementDecision decision = resolve(facts);
        assertFalse(decision.allows(MovementActivity.COMMANDED_MOVE));
        assertTrue(decision.allows(MovementActivity.WANDER));
        assertTrue(decision.allows(MovementActivity.PICK_ITEM));
        assertEquals(new MovementIntent.FollowOwner(), decision.intent());
    }

    @Test
    void resolutionIsDeterministic() {
        Facts facts = base().fleeing();
        facts.crane = true;
        assertEquals(resolve(facts), resolve(facts));
    }

    @Test
    void decisionRequiresEveryActivity() {
        assertThrows(IllegalArgumentException.class,
                () -> new MovementDecision(new MovementIntent.Sit(), Map.of()));
    }
}
