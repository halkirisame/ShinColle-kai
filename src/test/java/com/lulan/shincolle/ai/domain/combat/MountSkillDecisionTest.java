package com.lulan.shincolle.ai.domain.combat;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Reason;
import static com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Skill;
import static com.lulan.shincolle.ai.domain.combat.MountSkillDecision.TargetKind;
import static com.lulan.shincolle.ai.domain.combat.MountSkillDecision.reject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountSkillDecisionTest {
    @Test
    void acceptsEveryEnabledReadyEntitySkill() {
        for (Skill skill : Skill.values()) {
            assertEquals(Set.of(), reject(skill, TargetKind.ENTITY, true, true, 0, false));
        }
    }

    @Test
    void onlyHeavyAcceptsBlocksIncludingNegativeHeight() {
        for (Skill skill : Skill.values()) {
            assertEquals(skill != Skill.HEAVY,
                    reject(skill, TargetKind.BLOCK, true, true, 0, false).contains(Reason.TARGET_KIND));
        }
    }

    @Test
    void unavailableAbilityDoesNotFallThroughToAnotherSkill() {
        assertEquals(Set.of(Reason.NO_ABILITY), reject(Skill.LIGHT, TargetKind.ENTITY, false, true, 0, false));
    }

    @Test
    void aircraftNeedsCarrierButGunsDoNot() {
        assertEquals(Set.of(Reason.NOT_CARRIER), reject(Skill.AIR_LIGHT, TargetKind.ENTITY, true, false, 0, false));
        assertEquals(Set.of(Reason.NOT_CARRIER), reject(Skill.AIR_HEAVY, TargetKind.ENTITY, true, false, 0, false));
        assertTrue(reject(Skill.LIGHT, TargetKind.ENTITY, true, false, 0, false).isEmpty());
    }

    @Test
    void cooldownAndSameTickAreIndependentRejectionReasons() {
        assertEquals(Set.of(Reason.COOLDOWN, Reason.SAME_TICK),
                reject(Skill.HEAVY, TargetKind.ENTITY, true, false, 1, true));
        assertEquals(Set.of(Reason.SAME_TICK), reject(Skill.HEAVY, TargetKind.ENTITY, true, false, 0, true));
        assertTrue(reject(Skill.HEAVY, TargetKind.ENTITY, true, false, -1, false).isEmpty());
    }
}
