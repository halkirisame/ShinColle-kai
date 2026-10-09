package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which waypoint a ship passes through: one it guards, with a next point to go on to. */
class WaypointPassThroughTest {
    private static final CommandPos A = new CommandPos(2, 2, 2);
    private static final CommandPos B = new CommandPos(7, 2, 2);
    private static final CommandPos C = new CommandPos(2, 2, 7);

    private static final class Builder {
        boolean blockGuard = true;
        boolean sameDimension = true;
        boolean guardingEntity;
        boolean sitting;
        boolean leashed;
        boolean riding;
        boolean ridingMount;
        boolean formationMember;
        boolean currentIsWaypoint = true;
        WaypointLinks links = new WaypointLinks(Optional.of(B), Optional.empty());
        double distanceSq = 50D;

        Optional<CommandPos> passThrough() {
            return WaypointTraversal.passThrough(new WaypointFacts(blockGuard, sameDimension, guardingEntity, sitting,
                    leashed, riding, ridingMount, formationMember, A, currentIsWaypoint, links, 0, 0, distanceSq,
                    WaypointProgress.NONE));
        }
    }

    @Test
    void aGuardedWaypointWithANextPointIsPassedThrough() {
        assertEquals(Optional.of(A), new Builder().passThrough());
    }

    @Test
    void theMarkDoesNotWaitForTheShipToArrive() {
        Builder near = new Builder();
        near.distanceSq = 1D;
        assertEquals(Optional.of(A), near.passThrough());
    }

    @Test
    void eachExclusionAloneEndsIt() {
        Builder noGuard = new Builder();
        noGuard.blockGuard = false;
        assertEquals(Optional.empty(), noGuard.passThrough());
        Builder otherDimension = new Builder();
        otherDimension.sameDimension = false;
        assertEquals(Optional.empty(), otherDimension.passThrough());
        Builder guardingEntity = new Builder();
        guardingEntity.guardingEntity = true;
        assertEquals(Optional.empty(), guardingEntity.passThrough());
        Builder sitting = new Builder();
        sitting.sitting = true;
        assertEquals(Optional.empty(), sitting.passThrough());
        Builder leashed = new Builder();
        leashed.leashed = true;
        assertEquals(Optional.empty(), leashed.passThrough());
        Builder riding = new Builder();
        riding.riding = true;
        assertEquals(Optional.empty(), riding.passThrough());
        Builder formationMember = new Builder();
        formationMember.formationMember = true;
        assertEquals(Optional.empty(), formationMember.passThrough());
    }

    @Test
    void aShipOnItsOwnMountIsPassedThroughToo() {
        Builder mounted = new Builder();
        mounted.riding = true;
        mounted.ridingMount = true;
        assertEquals(Optional.of(A), mounted.passThrough());
    }

    @Test
    void aBlockThatIsNoWaypointIsNotPassedThrough() {
        Builder block = new Builder();
        block.currentIsWaypoint = false;
        block.links = WaypointLinks.NONE;
        assertEquals(Optional.empty(), block.passThrough());
    }

    @Test
    void theLastPointOfARouteIsNotPassedThrough() {
        Builder last = new Builder();
        last.links = new WaypointLinks(Optional.empty(), Optional.of(C));
        assertEquals(Optional.empty(), last.passThrough());
    }

    @Test
    void aTurningPointIsPassedThroughAndSoIsOneWaitedAt() {
        Builder turn = new Builder();
        turn.links = new WaypointLinks(Optional.of(C), Optional.of(B));
        assertEquals(Optional.of(A), turn.passThrough());
        Builder waiting = new Builder();
        waiting.distanceSq = 1D;
        assertEquals(Optional.of(A), waiting.passThrough());
    }
}
