package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.LegacyDecodeResult;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.utility.WaypointStayTime;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaypointTraversalTest {
    private static final CommandPos A = new CommandPos(2, 2, 2);
    private static final CommandPos B = new CommandPos(7, 2, 2);
    private static final CommandPos C = new CommandPos(2, 2, 7);

    /** An arrived ship at {@link #A} whose waypoint links to {@link #B}, with no wait to serve. */
    private static Builder arrived() {
        return new Builder();
    }

    private static final class Builder {
        boolean blockGuard = true;
        boolean sameDimension = true;
        boolean guardingEntity;
        boolean sitting;
        boolean leashed;
        boolean riding;
        boolean ridingMount;
        boolean formationMember;
        CommandPos current = A;
        boolean currentIsWaypoint = true;
        WaypointLinks links = new WaypointLinks(Optional.of(B), Optional.empty());
        int waypointStay;
        int shipStay;
        double distanceSq = 1D;
        WaypointProgress progress = WaypointProgress.NONE;

        WaypointStep step() {
            return WaypointTraversal.step(new WaypointFacts(blockGuard, sameDimension, guardingEntity, sitting,
                    leashed, riding, ridingMount, formationMember, current, currentIsWaypoint, links,
                    waypointStay, shipStay, distanceSq, progress));
        }

        Builder elapsed(int ticks) {
            progress = new WaypointProgress(progress.lastWaypoint(), Optional.of(new WaypointStay(A, ticks)));
            return this;
        }
    }

    private static Set<WaypointSkipReason> skipped(WaypointStep step) {
        return assertInstanceOf(WaypointStep.Skip.class, step).reasons();
    }

    @Test
    void eachExclusionIsReportedByItself() {
        Builder noGuard = arrived();
        noGuard.blockGuard = false;
        assertEquals(EnumSet.of(WaypointSkipReason.NO_BLOCK_GUARD), skipped(noGuard.step()));
        Builder otherDimension = arrived();
        otherDimension.sameDimension = false;
        assertEquals(EnumSet.of(WaypointSkipReason.OTHER_DIMENSION), skipped(otherDimension.step()));
        Builder guardingEntity = arrived();
        guardingEntity.guardingEntity = true;
        assertEquals(EnumSet.of(WaypointSkipReason.GUARDING_ENTITY), skipped(guardingEntity.step()));
        Builder sitting = arrived();
        sitting.sitting = true;
        assertEquals(EnumSet.of(WaypointSkipReason.SITTING), skipped(sitting.step()));
        Builder leashed = arrived();
        leashed.leashed = true;
        assertEquals(EnumSet.of(WaypointSkipReason.LEASHED), skipped(leashed.step()));
        Builder riding = arrived();
        riding.riding = true;
        assertEquals(EnumSet.of(WaypointSkipReason.RIDING), skipped(riding.step()));
        Builder formation = arrived();
        formation.formationMember = true;
        assertEquals(EnumSet.of(WaypointSkipReason.FORMATION_MEMBER), skipped(formation.step()));
    }

    @Test
    void severalExclusionsAreAllReported() {
        Builder both = arrived();
        both.sitting = true;
        both.leashed = true;
        assertEquals(EnumSet.of(WaypointSkipReason.SITTING, WaypointSkipReason.LEASHED), skipped(both.step()));
    }

    @Test
    void aShipOnItsOwnMountTakesPart() {
        Builder mounted = arrived();
        mounted.riding = true;
        mounted.ridingMount = true;
        assertInstanceOf(WaypointStep.Advance.class, mounted.step());
    }

    @Test
    void aShipOnAnythingElseIsStillExcluded() {
        Builder ridden = arrived();
        ridden.riding = true;
        assertEquals(EnumSet.of(WaypointSkipReason.RIDING), skipped(ridden.step()));
    }

    @Test
    void aStayCarriedFromAnotherPointStartsOver() {
        Builder ship = arrived();
        ship.waypointStay = 100;
        ship.progress = new WaypointProgress(Optional.empty(), Optional.of(new WaypointStay(B, 96)));
        WaypointStep.Wait wait = assertInstanceOf(WaypointStep.Wait.class, ship.step());
        assertEquals(Optional.of(new WaypointStay(A, WaypointTraversal.CHECK_INTERVAL)), wait.next().stay());
    }

    @Test
    void aShipThatHasNotArrivedOrIsNotAtAWaypointDoesNothing() {
        Builder far = arrived();
        far.distanceSq = 9D;
        assertEquals(EnumSet.of(WaypointSkipReason.NOT_ARRIVED), skipped(far.step()));
        Builder near = arrived();
        near.distanceSq = 8.99D;
        assertInstanceOf(WaypointStep.Advance.class, near.step());
        Builder plain = arrived();
        plain.currentIsWaypoint = false;
        assertEquals(EnumSet.of(WaypointSkipReason.NOT_A_WAYPOINT), skipped(plain.step()));
        Builder both = arrived();
        both.currentIsWaypoint = false;
        both.distanceSq = 100D;
        assertEquals(EnumSet.of(WaypointSkipReason.NOT_A_WAYPOINT, WaypointSkipReason.NOT_ARRIVED),
                skipped(both.step()));
    }

    @Test
    void noWaitAdvancesAtTheFirstCheck() {
        WaypointStep.Advance advance = assertInstanceOf(WaypointStep.Advance.class, arrived().step());
        assertEquals(B, advance.destination());
        assertEquals(Optional.of(A), advance.next().lastWaypoint());
        assertEquals(Optional.empty(), advance.next().stay());
    }

    @Test
    void aHundredTickStayWaitsSevenChecksAndMovesOnAtTheEighth() {
        Builder ship = arrived();
        ship.waypointStay = 100;
        int elapsed = 0;
        for (int check = 1; check <= 7; check++) {
            WaypointStep.Wait wait = assertInstanceOf(WaypointStep.Wait.class, ship.step());
            elapsed += WaypointTraversal.CHECK_INTERVAL;
            assertEquals(Optional.of(new WaypointStay(A, elapsed)), wait.next().stay(), "check " + check);
            ship.progress = wait.next();
        }
        assertEquals(112, elapsed);
        assertInstanceOf(WaypointStep.Advance.class, ship.step());
    }

    @Test
    void theLongerOfTheShipAndTheWaypointSettingsWins() {
        Builder ship = arrived();
        ship.waypointStay = 100;
        ship.shipStay = 1200;
        ship.elapsed(112);
        assertInstanceOf(WaypointStep.Wait.class, ship.step());
        ship.elapsed(1200);
        assertInstanceOf(WaypointStep.Advance.class, ship.step());
    }

    @Test
    void waitKeepsTheRouteHistory() {
        Builder ship = arrived();
        ship.waypointStay = 100;
        ship.progress = new WaypointProgress(Optional.of(C), Optional.empty());
        WaypointStep.Wait wait = assertInstanceOf(WaypointStep.Wait.class, ship.step());
        assertEquals(Optional.of(C), wait.next().lastWaypoint());
    }

    @Test
    void anUnlinkedWaypointHoldsAndRemembersItself() {
        Builder ship = arrived();
        ship.links = WaypointLinks.NONE;
        WaypointStep.Hold hold = assertInstanceOf(WaypointStep.Hold.class, ship.step());
        assertEquals(Optional.of(A), hold.next().lastWaypoint());
        assertEquals(Optional.empty(), hold.next().stay());
    }

    @Test
    void comingBackFromTheNextWaypointTakesTheLastLink() {
        Builder ship = arrived();
        ship.links = new WaypointLinks(Optional.of(B), Optional.of(C));
        ship.progress = new WaypointProgress(Optional.of(B), Optional.empty());
        assertEquals(C, assertInstanceOf(WaypointStep.Advance.class, ship.step()).destination());
    }

    @Test
    void comingBackWithoutALastLinkStillTakesTheNextOne() {
        Builder ship = arrived();
        ship.progress = new WaypointProgress(Optional.of(B), Optional.empty());
        assertEquals(B, assertInstanceOf(WaypointStep.Advance.class, ship.step()).destination());
    }

    @Test
    void aDifferentPreviousWaypointTakesTheNextLink() {
        Builder ship = arrived();
        ship.links = new WaypointLinks(Optional.of(B), Optional.of(C));
        ship.progress = new WaypointProgress(Optional.of(C), Optional.empty());
        assertEquals(B, assertInstanceOf(WaypointStep.Advance.class, ship.step()).destination());
    }

    @Test
    void theWorldOriginAndANegativeYAreRealPositions() {
        CommandPos origin = new CommandPos(0, 0, 0);
        CommandPos deep = new CommandPos(4, -20, 6);
        Builder ship = arrived();
        ship.current = deep;
        ship.links = new WaypointLinks(Optional.of(origin), Optional.empty());
        WaypointStep.Advance advance = assertInstanceOf(WaypointStep.Advance.class, ship.step());
        assertEquals(origin, advance.destination());
        assertEquals(Optional.of(deep), advance.next().lastWaypoint());
    }

    @Test
    void staySettingDecodesToTheSameTicksAsTheLegacyTable() {
        for (int raw = 0; raw <= WaypointStaySetting.MAX; raw++) {
            LegacyDecodeResult<WaypointStaySetting> decoded = WaypointStaySetting.fromLegacy(raw);
            assertInstanceOf(LegacyDecodeResult.Known.class, decoded);
            WaypointStaySetting setting = ((LegacyDecodeResult.Known<WaypointStaySetting>) decoded).value();
            assertEquals(WaypointStayTime.toTicks(raw), setting.ticks(), "setting " + raw);
        }
        assertEquals(0, new WaypointStaySetting(0).ticks());
        assertEquals(100, new WaypointStaySetting(1).ticks());
        assertEquals(500, new WaypointStaySetting(5).ticks());
        assertEquals(1200, new WaypointStaySetting(6).ticks());
        assertEquals(6000, new WaypointStaySetting(10).ticks());
        assertEquals(12000, new WaypointStaySetting(11).ticks());
        assertEquals(72000, new WaypointStaySetting(16).ticks());
    }

    @Test
    void anOutOfRangeStaySettingIsUnknownAndKeepsItsRawValue() {
        assertEquals(new LegacyDecodeResult.Unknown<WaypointStaySetting>(17), WaypointStaySetting.fromLegacy(17));
        assertEquals(new LegacyDecodeResult.Unknown<WaypointStaySetting>(-1), WaypointStaySetting.fromLegacy(-1));
        assertThrows(IllegalArgumentException.class, () -> new WaypointStaySetting(17));
    }
}
