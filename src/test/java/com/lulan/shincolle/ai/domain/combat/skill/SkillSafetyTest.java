package com.lulan.shincolle.ai.domain.combat.skill;

import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.domain.movement.ConstraintSource;
import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.TeleportDenial;
import com.lulan.shincolle.ai.domain.movement.TeleportSafety;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSafetyTest {
    @Test
    void skillExemptsOnlyCooldownAndPreservesEverySafetyCondition() {
        assertTrue(TeleportSafety.denials(new TeleportSafety.Facts(true, 1, true, true, true),
                MovementReason.SKILL_ATTACK).isEmpty());
        assertEquals(Set.of(TeleportDenial.COOLDOWN), TeleportSafety.denials(
                new TeleportSafety.Facts(true, 1, true, true, true)));
        assertEquals(Set.of(TeleportDenial.OTHER_DIMENSION, TeleportDenial.CHUNK_NOT_LOADED,
                TeleportDenial.OUTSIDE_WORLD_BORDER, TeleportDenial.NO_FREE_SPACE), TeleportSafety.denials(
                new TeleportSafety.Facts(false, 1, false, false, false), MovementReason.SKILL_ATTACK));
    }

    @Test
    void skillsKeepActualLandingsWithinCommandRegion() {
        MovementConstraint region = new MovementConstraint.Within(new MovementPoint(0, 64, 0), 6,
                ConstraintSource.GUARD_POSITION);
        assertTrue(SkillMovementPermission.allows(region, new MovementPoint(6, 64, 0)));
        assertFalse(SkillMovementPermission.allows(region, new MovementPoint(6, 65, 0)));
        assertThrows(IllegalArgumentException.class, () -> new MovementPoint(Double.NaN, 64, 0));
        assertFalse(SkillMovementPermission.allows(new MovementConstraint.NoCombatMovement(ConstraintSource.GUARD_POSITION),
                new MovementPoint(0, 64, 0)));
    }

    @Test
    void ordinaryWeaponsResumeWithTheirRemainingWaitAfterSkill() {
        var initial = CombatTimingReducer.initial(100);
        var paused = initial;
        for (int tick = 0; tick < 50; tick++) paused = CombatTimingReducer.pauseForSkill(paused);
        for (WeaponChannel channel : WeaponChannel.values()) {
            assertEquals(initial.readyAt(channel) - 100, paused.readyAt(channel) - 150);
        }
        assertEquals(initial.aimTicks(), paused.aimTicks());
        assertEquals(initial.aimTarget(), paused.aimTarget());
    }
}
