package com.lulan.shincolle.ai.domain.traits;

import com.lulan.shincolle.ai.domain.combat.WeaponChannel;

/** Built-in innate bonuses, separate from equipment and the existing XP multiplier. */
public final class ShipInnateTraits {
    private ShipInnateTraits() { }

    public enum Profile {
        NONE, KASHIMA, YUKIKAZE;

        public Profile enabled(boolean newAi, boolean definitionActive) {
            return newAi && definitionActive ? this : NONE;
        }

        public float rawDodge(float rebuilt) {
            return this == YUKIKAZE ? rebuilt + .15F : rebuilt;
        }

        /** Applies only after firing; initial readiness and aim timers do not pass through here. */
        public int postAttackDelay(WeaponChannel channel, int delay) {
            if (this != YUKIKAZE || channel == WeaponChannel.AIR) return delay;
            int minimum = switch (channel) {
                case LIGHT -> 5;
                case HEAVY -> 10;
                case MELEE -> 1;
                case AIR -> throw new IllegalArgumentException("Aircraft intervals are not ordinary attacks");
            };
            return Math.max(minimum, (int) Math.round(delay / 1.1D));
        }
    }

    public record TrainingSource(boolean activeKashima, boolean alive, boolean fueled, boolean registered,
                                 boolean sameOwner, boolean sameLevel, boolean sharesFleet, boolean self) { }

    public static boolean supportsTraining(TrainingSource source) {
        return source.activeKashima() && source.alive() && source.fueled() && source.registered()
                && source.sameOwner() && source.sameLevel() && (source.self() || source.sharesFleet());
    }

    /** An individual ship's fractional support XP, in fifths of one experience point. */
    public record TrainingRemainder(int fifths) {
        public static final String KEY = "FleetTrainingRemainder";
        public static final TrainingRemainder ZERO = new TrainingRemainder(0);

        public TrainingRemainder {
            if (fifths < 0 || fifths > 4) throw new IllegalArgumentException("Training remainder must be in 0..4");
        }

        public static TrainingRemainder loaded(boolean integer, int value) {
            return integer && value >= 0 && value <= 4 ? new TrainingRemainder(value) : ZERO;
        }
    }

    public record TrainingAward(int experience, TrainingRemainder remainder) { }

    /** The existing multiplier and its truncation have already produced baseline. */
    public static TrainingAward trainingAward(int baseline, TrainingRemainder remainder) {
        if (baseline <= 0) return new TrainingAward(baseline, remainder);
        long total = baseline * 6L + remainder.fifths();
        return new TrainingAward((int) Math.min(Integer.MAX_VALUE, total / 5L),
                new TrainingRemainder((int) (total % 5L)));
    }
}
