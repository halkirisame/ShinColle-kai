package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumSet;
import java.util.Set;

/** Manual mounted weapons do not participate in automatic combat timing. */
public final class MountSkillDecision {
    public enum Skill { LIGHT, HEAVY, AIR_LIGHT, AIR_HEAVY }
    public enum TargetKind { ENTITY, BLOCK }
    public enum Reason { NO_ABILITY, NOT_CARRIER, TARGET_KIND, COOLDOWN, SAME_TICK }

    private MountSkillDecision() { }

    public static Set<Reason> reject(Skill skill, TargetKind target, boolean ability,
                                      boolean carrier, int remaining, boolean acceptedThisTick) {
        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        if (!ability) reasons.add(Reason.NO_ABILITY);
        if ((skill == Skill.AIR_LIGHT || skill == Skill.AIR_HEAVY) && !carrier) reasons.add(Reason.NOT_CARRIER);
        if (target == TargetKind.BLOCK && skill != Skill.HEAVY) reasons.add(Reason.TARGET_KIND);
        if (remaining > 0) reasons.add(Reason.COOLDOWN);
        if (acceptedThisTick) reasons.add(Reason.SAME_TICK);
        return Set.copyOf(reasons);
    }
}
