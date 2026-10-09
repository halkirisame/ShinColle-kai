package com.lulan.shincolle.ai.domain.formation;

import com.lulan.shincolle.ai.domain.movement.FormationSlot;

public sealed interface FormationProjection {
    record Active(FormationPattern pattern, FormationSlot slot, float minimumMovement)
            implements FormationProjection { }
    record Pending(Reason reason) implements FormationProjection { }
    record Inactive(Reason reason) implements FormationProjection { }
    enum Reason {
        LOAD_PREPARATION, OWNER_UNAVAILABLE, MEMBERSHIP_UNAVAILABLE, MEMBERS_UNRESOLVED,
        NOT_MEMBER, FORMATION_DISABLED, INSUFFICIENT_LIVING_MEMBERS, INVALID_OBSERVATION
    }
}
