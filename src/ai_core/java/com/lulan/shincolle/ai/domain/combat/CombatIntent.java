package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetSource;

import java.util.Objects;
import java.util.Set;

/** Whether a host may fire this tick, and at what. */
public sealed interface CombatIntent {
    /** Every reason that holds at once; never empty. */
    record HoldFire(Set<HoldFireReason> reasons) implements CombatIntent {
        public HoldFire {
            if (reasons.isEmpty()) {
                throw new IllegalArgumentException("HoldFire needs at least one reason");
            }
            reasons = Set.copyOf(reasons);
        }
    }

    record Engage(TargetHandle target, TargetSource source) implements CombatIntent {
        public Engage {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(source, "source");
        }
    }
}
