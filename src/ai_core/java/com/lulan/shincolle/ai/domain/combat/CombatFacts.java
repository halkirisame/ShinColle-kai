package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.action.ActionConstraints;

import java.util.Objects;
import java.util.Optional;

/**
 * What the combat intent is resolved from.
 *
 * @param lock        the target authority's lock, present only while its entity is loaded and alive
 * @param constraints the action constraints, absent for a host that has none (a hostile ship)
 * @param sitting     the host sits
 * @param onShipMount the host rides a ship mount
 */
public record CombatFacts(Optional<TargetLock> lock, Optional<ActionConstraints> constraints,
                          boolean sitting, boolean onShipMount) {
    public CombatFacts {
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(constraints, "constraints");
    }
}
