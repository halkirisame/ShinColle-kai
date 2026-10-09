package com.lulan.shincolle.ai.domain.combat;

import java.util.Objects;

/**
 * Everything one combat decision reads.
 *
 * @param distanceSq      squared distance between the host's and the target's positions
 * @param meleeDistanceSq squared distance to the target's feet, as the melee goal measured it
 * @param rangeSq         squared attack range
 * @param meleeReachSq    squared melee reach (width squared times 16)
 * @param aimTime         ticks of sight the cannons need
 * @param engageFactor    engageDistance / 100; cannons stop to fire inside range times this
 */
public record CombatContext(CombatIntent intent, CombatTimingState timing, CombatLoadout loadout,
                            double distanceSq, double meleeDistanceSq, double rangeSq, double meleeReachSq,
                            boolean onSight, int aimTime, WeaponReadiness readiness, boolean useMelee,
                            double engageFactor, int now) {
    public CombatContext {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(loadout, "loadout");
        Objects.requireNonNull(readiness, "readiness");
    }
}
