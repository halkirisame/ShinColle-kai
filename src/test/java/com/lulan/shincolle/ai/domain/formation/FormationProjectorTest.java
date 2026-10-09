package com.lulan.shincolle.ai.domain.formation;

import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.LOAD_PREPARATION;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.OWNER_UNAVAILABLE;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.MEMBERSHIP_UNAVAILABLE;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.MEMBERS_UNRESOLVED;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.NOT_MEMBER;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.FORMATION_DISABLED;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.INSUFFICIENT_LIVING_MEMBERS;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.INVALID_OBSERVATION;
import com.lulan.shincolle.ai.domain.formation.FormationProjector.Membership;
import com.lulan.shincolle.ai.domain.formation.FormationProjector.Resolution;
import com.lulan.shincolle.ai.domain.formation.FormationProjector.Member;
import com.lulan.shincolle.ai.domain.formation.FormationProjector.Observation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FormationProjectorTest {
    private static List<Member> members(int count) {
        List<Member> result = new ArrayList<>();
        for (int slot = 0; slot < count; slot++)
            result.add(new Member(new FormationSlot(slot), 100 + slot, Resolution.ALIVE, .4F - slot * .01F));
        return result;
    }
    private static FormationProjection project(int format, List<Member> members) {
        return FormationProjector.project(new Observation(true, Membership.MEMBER, format, 100, members));
    }
    @Test void preparationAndMissingOwnerRemainPending() {
        assertEquals(new FormationProjection.Pending(LOAD_PREPARATION),
                FormationProjector.project(new Observation(false, Membership.NOT_MEMBER, 0, 100, List.of())));
        assertEquals(new FormationProjection.Pending(OWNER_UNAVAILABLE),
                FormationProjector.project(new Observation(true, Membership.OWNER_UNAVAILABLE, 0, 100, List.of())));
        assertEquals(new FormationProjection.Pending(MEMBERSHIP_UNAVAILABLE),
                FormationProjector.project(new Observation(true, Membership.UNAVAILABLE, 0, 100, List.of())));
    }
    @Test void fourLivingCannotGrantBuffButFiveAndSixCan() {
        assertEquals(new FormationProjection.Inactive(INSUFFICIENT_LIVING_MEMBERS), project(1, members(4)));
        var five = assertInstanceOf(FormationProjection.Active.class, project(1, members(5)));
        assertEquals(.36F, five.minimumMovement(), .00001F);
        assertInstanceOf(FormationProjection.Active.class, project(1, members(6)));
    }
    @Test void unresolvedMemberIsNotADeathOrFormalRemoval() {
        var pending = members(4);
        pending.add(new Member(new FormationSlot(4), 104, Resolution.UNRESOLVED, 0F));
        assertEquals(new FormationProjection.Pending(MEMBERS_UNRESOLVED), project(1, pending));
        pending.set(4, new Member(new FormationSlot(4), 104, Resolution.DEAD, 0F));
        assertEquals(new FormationProjection.Inactive(INSUFFICIENT_LIVING_MEMBERS), project(1, pending));
        var five = members(5);
        five.add(new Member(new FormationSlot(5), 105, Resolution.UNRESOLVED, 0F));
        assertInstanceOf(FormationProjection.Active.class, project(1, five));
    }
    @Test void formalRemovalAndDisableTakePriorityOverUnresolvedMembers() {
        assertEquals(new FormationProjection.Inactive(NOT_MEMBER),
                FormationProjector.project(new Observation(true, Membership.NOT_MEMBER, 1, 100, members(5))));
        assertEquals(new FormationProjection.Inactive(FORMATION_DISABLED), project(0, members(4)));
        assertEquals(new FormationProjection.Pending(INVALID_OBSERVATION), project(9, members(5)));
    }
    @Test void fiveShipDiamondCompactsOnlyLivingResolvedSlots() {
        var team = members(6);
        team.set(1, new Member(new FormationSlot(1), 101, Resolution.DEAD, 0F));
        var active = assertInstanceOf(FormationProjection.Active.class,
                FormationProjector.project(new Observation(true, Membership.MEMBER, 3, 105, team)));
        assertEquals(new FormationSlot(4), active.slot());
        active = assertInstanceOf(FormationProjection.Active.class,
                FormationProjector.project(new Observation(true, Membership.MEMBER, 2, 105, team)));
        assertEquals(new FormationSlot(5), active.slot());
    }
    @Test void duplicateUidOrSlotCannotCountAsFiveShips() {
        var team = members(5);
        team.set(4, new Member(new FormationSlot(4), 100, Resolution.ALIVE, .3F));
        assertEquals(new FormationProjection.Pending(INVALID_OBSERVATION), project(1, team));
        team.set(4, new Member(new FormationSlot(3), 104, Resolution.ALIVE, .3F));
        assertEquals(new FormationProjection.Pending(INVALID_OBSERVATION), project(1, team));
    }
}
