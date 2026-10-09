package com.lulan.shincolle.ai.domain.combat;

import java.util.Objects;

public record CombatMovementDirective(CombatManeuver maneuver) {
    public static final CombatMovementDirective NONE = new CombatMovementDirective(CombatManeuver.NONE);
    public static final CombatMovementDirective APPROACH = new CombatMovementDirective(CombatManeuver.APPROACH_TARGET);
    public static final CombatMovementDirective STOP = new CombatMovementDirective(CombatManeuver.STOP_FOR_FIRE);

    public CombatMovementDirective {
        Objects.requireNonNull(maneuver, "maneuver");
    }

    public boolean stopForFire() {
        return this.maneuver == CombatManeuver.STOP_FOR_FIRE;
    }
}
