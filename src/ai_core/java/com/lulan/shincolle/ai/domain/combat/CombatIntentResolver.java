package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.action.ActionKind;

import java.util.EnumSet;
import java.util.Set;

public final class CombatIntentResolver {
    private CombatIntentResolver() { }

    /** Collects every reason to hold fire; with none, engages the locked target. */
    public static CombatIntent resolve(CombatFacts facts) {
        Set<HoldFireReason> reasons = EnumSet.noneOf(HoldFireReason.class);
        if (facts.lock().isEmpty()) reasons.add(HoldFireReason.NO_TARGET);
        if (facts.constraints().isPresent() && !facts.constraints().get().allows(ActionKind.FIRING)) {
            reasons.add(HoldFireReason.FIRING_BLOCKED);
        }
        if (facts.sitting()) reasons.add(HoldFireReason.SITTING);
        if (facts.onShipMount()) reasons.add(HoldFireReason.ON_SHIP_MOUNT);
        if (!reasons.isEmpty()) return new CombatIntent.HoldFire(reasons);
        TargetLock lock = facts.lock().get();
        return new CombatIntent.Engage(lock.target(), lock.source());
    }
}
