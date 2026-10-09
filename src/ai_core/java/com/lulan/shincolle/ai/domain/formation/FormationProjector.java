package com.lulan.shincolle.ai.domain.formation;

import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.LOAD_PREPARATION;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.OWNER_UNAVAILABLE;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.MEMBERSHIP_UNAVAILABLE;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.MEMBERS_UNRESOLVED;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.NOT_MEMBER;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.FORMATION_DISABLED;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.INSUFFICIENT_LIVING_MEMBERS;
import static com.lulan.shincolle.ai.domain.formation.FormationProjection.Reason.INVALID_OBSERVATION;

/** Projects the official team using only the members actually resolved in its level. */
public final class FormationProjector {
    private FormationProjector() { }
    public enum Membership { OWNER_UNAVAILABLE, UNAVAILABLE, MEMBER, NOT_MEMBER }
    public enum Resolution { ALIVE, DEAD, UNRESOLVED }
    public record Member(FormationSlot slot, int uid, Resolution resolution, float movement) { }
    public record Observation(boolean registered, Membership membership, int teamFormat,
                              int hostUid, List<Member> members) {
        public Observation { members = List.copyOf(members); }
    }

    public static FormationProjection project(Observation facts) {
        if (!facts.registered()) return new FormationProjection.Pending(LOAD_PREPARATION);
        if (facts.membership() == Membership.OWNER_UNAVAILABLE)
            return new FormationProjection.Pending(OWNER_UNAVAILABLE);
        if (facts.membership() == Membership.UNAVAILABLE)
            return new FormationProjection.Pending(MEMBERSHIP_UNAVAILABLE);
        if (facts.membership() == Membership.NOT_MEMBER) return new FormationProjection.Inactive(NOT_MEMBER);
        if (facts.teamFormat() == 0) return new FormationProjection.Inactive(FORMATION_DISABLED);
        var pattern = FormationPattern.fromLegacy(facts.teamFormat());
        if (pattern.isEmpty()) return new FormationProjection.Pending(INVALID_OBSERVATION);
        Set<Integer> uids = new HashSet<>();
        Set<FormationSlot> slots = new HashSet<>();
        int alive = 0, compacted = -1;
        boolean unresolved = false;
        float minMovement = 10F;
        Member host = null;
        for (Member member : facts.members()) {
            if (member.uid() <= 0 || !uids.add(member.uid()) || !slots.add(member.slot())
                    || !Float.isFinite(member.movement()))
                return new FormationProjection.Pending(INVALID_OBSERVATION);
            if (member.uid() == facts.hostUid()) host = member;
            if (member.resolution() == Resolution.UNRESOLVED) unresolved = true;
            if (member.resolution() == Resolution.ALIVE) {
                if (member.uid() == facts.hostUid()) compacted = alive;
                alive++;
                minMovement = Math.min(minMovement, member.movement());
            }
        }
        if (host == null) return new FormationProjection.Pending(INVALID_OBSERVATION);
        if (alive < 5) return unresolved ? new FormationProjection.Pending(MEMBERS_UNRESOLVED)
                : new FormationProjection.Inactive(INSUFFICIENT_LIVING_MEMBERS);
        if (host.resolution() != Resolution.ALIVE) return new FormationProjection.Pending(MEMBERS_UNRESOLVED);
        FormationSlot slot = pattern.get() == FormationPattern.DIAMOND && alive == 5
                ? new FormationSlot(compacted) : host.slot();
        return new FormationProjection.Active(pattern.get(), slot, minMovement);
    }
}
