package com.lulan.shincolle.ai.domain.combat.skill;

import java.util.ArrayList;
import java.util.List;

/** Pure sequence decisions. The adapter supplies facts and executes the returned effects. */
public final class SkillDecision {
    public enum Effect {
        CHARGE_VISUAL, START_SLASH, START_DROP, CAPTURE_CHARGE, START_SPIN, START_FINAL,
        CLEAR_HITS, SLASH_SOUND, SLASH_TRAIL, FINAL_SOUND, CHARGE_TRAIL, SPIN_RING, SPIN_SOUND,
        FIRE_GAE_BOLG, GAE_BOLG_TRAIL, PUNCH_INWARD, PUNCH_OUTWARD, PUNCH_FINISH
    }

    public record Plan(SkillState state, List<Effect> effects, boolean motion, boolean damage) {
        public Plan {
            effects = List.copyOf(effects);
        }
    }

    private SkillDecision() { }

    /** Only an existing lock gains the continuous skill's range; acquisition stays unchanged. */
    public static double targetRetentionRange(SkillState state, double ordinaryRange, boolean held) {
        if (!held || !state.running()) return ordinaryRange;
        return Math.max(ordinaryRange, switch (state.kind()) {
            case TENRYUU -> 10D;
            case TATSUTA -> 8D;
            case NAGATO -> ordinaryRange;
        });
    }

    public static Plan heavy(SkillState state, SkillProfile profile) {
        if (state.running()) return new Plan(state, List.of(), false, false);
        if (profile.kind() == SkillKind.NAGATO) {
            SkillPhase phase = switch (state.phase()) {
                case PUNCH_FIRST -> SkillPhase.PUNCH_SECOND;
                case PUNCH_SECOND -> SkillPhase.PUNCH_THIRD;
                case PUNCH_THIRD -> SkillPhase.IDLE;
                default -> SkillPhase.PUNCH_FIRST;
            };
            Effect effect = switch (phase) {
                case PUNCH_SECOND -> Effect.PUNCH_OUTWARD;
                case IDLE -> Effect.PUNCH_FINISH;
                default -> Effect.PUNCH_INWARD;
            };
            return new Plan(new SkillState(state.kind(), phase, 0, 0), List.of(effect), false, false);
        }
        if (state.phase() == SkillPhase.READY) {
            return new Plan(new SkillState(state.kind(), SkillPhase.CHARGE,
                    profile.kind() == SkillKind.TATSUTA ? 10 : 0, profile.attacks()), List.of(), false, false);
        }
        return new Plan(new SkillState(state.kind(), SkillPhase.READY, 0, 0),
                List.of(Effect.CHARGE_VISUAL), false, false);
    }

    public static Plan tick(SkillState state, SkillProfile profile, boolean permitted, boolean targetValid) {
        if (!permitted || !targetValid) return new Plan(SkillState.idle(state.kind()), List.of(), false, false);
        if (!state.running()) return new Plan(state, List.of(), false, false);
        return state.kind() == SkillKind.TENRYUU ? tenryuu(state, profile) : tatsuta(state);
    }

    private static Plan tenryuu(SkillState s, SkillProfile p) {
        if (s.phase() == SkillPhase.CHARGE) {
            boolean drop = s.remaining() <= 1;
            return new Plan(new SkillState(s.kind(), drop ? SkillPhase.FINAL : SkillPhase.HORIZONTAL,
                    drop ? 8 : 6, Math.max(0, s.remaining() - 1)),
                    List.of(drop ? Effect.START_DROP : Effect.START_SLASH), false, false);
        }
        List<Effect> effects = new ArrayList<>();
        if (s.ticks() == 6) {
            effects.add(Effect.CLEAR_HITS);
            effects.add(Effect.SLASH_SOUND);
        }
        if (s.ticks() == 3) {
            effects.add(Effect.SLASH_TRAIL);
            if (s.phase() == SkillPhase.FINAL) effects.add(Effect.FINAL_SOUND);
        }
        boolean motion = s.ticks() <= (p.hostile() ? 12 : 7);
        SkillState next = s.ticks() == 0
                ? s.phase() == SkillPhase.FINAL ? SkillState.idle(s.kind())
                : new SkillState(s.kind(), SkillPhase.CHARGE, 0, s.remaining())
                : new SkillState(s.kind(), s.phase(), s.ticks() - 1, s.remaining());
        return new Plan(next, effects, motion, motion);
    }

    private static Plan tatsuta(SkillState s) {
        List<Effect> effects = new ArrayList<>();
        SkillPhase phase = s.phase();
        int ticks = s.ticks();
        int remaining = s.remaining();
        if (ticks == 0) {
            switch (phase) {
                case CHARGE -> {
                    phase = SkillPhase.SPIN;
                    ticks = 25;
                    effects.add(Effect.START_SPIN);
                }
                case SPIN -> {
                    phase = SkillPhase.FINAL;
                    ticks = 15;
                    remaining = 0;
                    effects.add(Effect.START_FINAL);
                }
                default -> {
                    return new Plan(SkillState.idle(s.kind()), List.of(), false, false);
                }
            }
        }
        if (phase == SkillPhase.CHARGE) {
            if (ticks == 8) effects.add(Effect.CAPTURE_CHARGE);
            if (ticks == 6) effects.add(Effect.CHARGE_TRAIL);
        } else if (phase == SkillPhase.SPIN) {
            if ((ticks & 1) == 0) effects.add(Effect.SPIN_RING);
            if ((ticks & 7) == 0) {
                remaining = Math.max(0, remaining - 1);
                effects.add(Effect.CLEAR_HITS);
                effects.add(Effect.SPIN_SOUND);
                if (remaining <= 1) ticks = 0;
            }
        } else if (phase == SkillPhase.FINAL) {
            if (ticks == 6) effects.add(Effect.FIRE_GAE_BOLG);
            if (ticks == 4) {
                effects.add(Effect.FINAL_SOUND);
                effects.add(Effect.GAE_BOLG_TRAIL);
            }
        }
        return new Plan(new SkillState(s.kind(), phase, Math.max(0, ticks - 1), remaining), effects,
                true, phase == SkillPhase.SPIN);
    }
}
