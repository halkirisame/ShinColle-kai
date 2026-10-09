package com.lulan.shincolle.ai.domain.combat.skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.CAPTURE_CHARGE;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.CLEAR_HITS;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.FIRE_GAE_BOLG;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.PUNCH_FINISH;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.PUNCH_INWARD;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.PUNCH_OUTWARD;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.START_DROP;
import static com.lulan.shincolle.ai.domain.combat.skill.SkillDecision.Effect.START_SLASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkillDecisionTest {
    @Test
    void tenryuuRestoresLevelAndScaleDependentSequences() {
        for (boolean hostile : List.of(false, true)) {
            SkillProfile profile = new SkillProfile(SkillKind.TENRYUU, hostile, 150, 3);
            SkillState state = SkillDecision.heavy(SkillState.idle(profile.kind()), profile).state();
            assertEquals(SkillPhase.READY, state.phase());
            assertFalse(state.running());
            state = SkillDecision.heavy(state, profile).state();
            List<SkillDecision.Effect> events = new ArrayList<>();
            int hits = 0;
            int ticks = 0;
            while (state.running() && ticks++ < 150) {
                SkillDecision.Plan plan = SkillDecision.tick(state, profile, true, true);
                events.addAll(plan.effects());
                if (plan.damage()) hits++;
                state = plan.state();
            }
            int slashes = hostile ? 7 : 6;
            assertEquals(slashes, events.stream().filter(e -> e == START_SLASH).count());
            assertEquals(1, events.stream().filter(e -> e == START_DROP).count());
            assertEquals(slashes * 7 + (hostile ? 9 : 8), hits);
            assertEquals(slashes + 1, events.stream().filter(e -> e == CLEAR_HITS).count());
            assertEquals(SkillPhase.IDLE, state.phase());
        }
    }

    @Test
    void tatsutaCapturesAtEightAndFiresOnceAtSix() {
        SkillProfile profile = new SkillProfile(SkillKind.TATSUTA, false, 100, 0);
        SkillState state = SkillDecision.heavy(SkillState.idle(profile.kind()), profile).state();
        state = SkillDecision.heavy(state, profile).state();
        int chargeCapture = 0;
        int beams = 0;
        int spinTicks = 0;
        int cycles = 0;
        for (int tick = 0; state.running() && tick < 80; tick++) {
            SkillDecision.Plan plan = SkillDecision.tick(state, profile, true, true);
            if (plan.effects().contains(CAPTURE_CHARGE)) {
                assertEquals(8, state.ticks());
                chargeCapture++;
            }
            if (plan.effects().contains(FIRE_GAE_BOLG)) {
                assertEquals(6, state.ticks());
                beams++;
            }
            if (plan.damage()) spinTicks++;
            if (plan.effects().contains(CLEAR_HITS)) cycles++;
            state = plan.state();
        }
        assertEquals(1, chargeCapture);
        assertEquals(1, beams);
        assertEquals(2, cycles);
        assertEquals(10, spinTicks);
        assertEquals(SkillPhase.IDLE, state.phase());
    }

    @Test
    void nagatoUsesFourHeavyAttacksAndNeverOccupiesContinuousFire() {
        SkillProfile profile = new SkillProfile(SkillKind.NAGATO, false, 1, 0);
        SkillState state = SkillState.idle(profile.kind());
        for (SkillDecision.Effect expected : List.of(PUNCH_INWARD, PUNCH_OUTWARD, PUNCH_INWARD, PUNCH_FINISH)) {
            SkillDecision.Plan plan = SkillDecision.heavy(state, profile);
            assertEquals(List.of(expected), plan.effects());
            assertFalse(plan.state().running());
            state = plan.state();
        }
        assertEquals(SkillPhase.IDLE, state.phase());
    }

    @Test
    void interruptionDropsAllCountersAndStopsEffects() {
        for (SkillKind kind : SkillKind.values()) {
            SkillProfile profile = new SkillProfile(kind, false, 99, 0);
            SkillState state = SkillDecision.heavy(SkillState.idle(kind), profile).state();
            state = SkillDecision.heavy(state, profile).state();
            assertEquals(SkillState.idle(kind), SkillDecision.tick(state, profile, false, true).state());
            assertEquals(SkillState.idle(kind), SkillDecision.tick(state, profile, true, false).state());
        }
    }

    @Test
    void retentionExtendsOnlyExistingLocksDuringContinuousPhases() {
        for (SkillKind kind : SkillKind.values()) {
            for (SkillPhase phase : SkillPhase.values()) {
                SkillState state = new SkillState(kind, phase, 3, 2);
                boolean continuous = kind != SkillKind.NAGATO && List.of(SkillPhase.CHARGE,
                        SkillPhase.HORIZONTAL, SkillPhase.SPIN, SkillPhase.FINAL).contains(phase);
                double expected = continuous ? kind == SkillKind.TENRYUU ? 10D : 8D : 4D;
                assertEquals(expected, SkillDecision.targetRetentionRange(state, 4D, true));
                assertEquals(4D, SkillDecision.targetRetentionRange(state, 4D, false));
                assertEquals(32D, SkillDecision.targetRetentionRange(state, 32D, true));
            }
        }
    }

    @Test
    void damageProfilesKeepFriendlyAndHostileNumbersSeparate() {
        assertEquals(0.3F, new SkillProfile(SkillKind.TENRYUU, false, 1, 0).horizontalMultiplier());
        assertEquals(0.4F, new SkillProfile(SkillKind.TENRYUU, true, 1, 0).horizontalMultiplier());
        assertEquals(1.2F, new SkillProfile(SkillKind.TENRYUU, false, 1, 0).finalMultiplier());
        assertEquals(1F, new SkillProfile(SkillKind.TENRYUU, true, 1, 0).finalMultiplier());
        assertEquals(0.5F, new SkillProfile(SkillKind.TATSUTA, true, 1, 3).horizontalMultiplier());
        assertEquals(1.5F, new SkillProfile(SkillKind.TATSUTA, false, 150, 0).finalMultiplier());
        assertEquals(4, new SkillProfile(SkillKind.TATSUTA, true, 1, 3).attacks());
        assertEquals(4F, new SkillProfile(SkillKind.NAGATO, false, 1, 0).finalMultiplier());
    }
}
