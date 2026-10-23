package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Flight rules of the heavy bombardment fortress; the entity only gathers facts and carries out the answer. */
public final class FortFlightDecision {
    public static final int MAX_AGE = 500;
    public static final int PATH_INTERVAL = 16;
    public static final double DETONATION_DISTANCE_SQUARED = 6D;
    public static final double PATH_DISTANCE_SQUARED = 4D;

    private FortFlightDecision() { }

    public enum Completion { ACTIVE, FINISHED }

    public enum Action { WAIT, PATH, DETONATE, DISCARD }

    public enum Reason {
        ALREADY_FINISHED, HOST_INVALID, AGE_LIMIT, RETURNING, TARGET_LOST, IN_BLAST_RANGE, PATH_DUE, EN_ROUTE
    }

    /**
     * @param offsetDistanceSquared squared distance from the fortress to the point one block above the target's feet
     */
    public record Facts(int age, boolean returning, boolean hostValid, boolean targetValid,
                        double offsetDistanceSquared, Completion completion) {
        public Facts {
            Objects.requireNonNull(completion, "completion");
        }
    }

    public record Result(Action action, Set<Reason> reasons) {
        public Result {
            Objects.requireNonNull(action, "action");
            reasons = Set.copyOf(reasons);
        }
    }

    public static Result decide(Facts facts) {
        Objects.requireNonNull(facts, "facts");
        if (facts.completion() == Completion.FINISHED) return result(Action.WAIT, Reason.ALREADY_FINISHED);
        // A fortress without a launching ship leaves without exploding, whatever else is true.
        if (!facts.hostValid()) return result(Action.DISCARD, Reason.HOST_INVALID);
        EnumSet<Reason> ends = EnumSet.noneOf(Reason.class);
        if (facts.age() >= MAX_AGE) ends.add(Reason.AGE_LIMIT);
        if (facts.returning()) ends.add(Reason.RETURNING);
        if (!facts.targetValid()) ends.add(Reason.TARGET_LOST);
        if (!ends.isEmpty()) return new Result(Action.DETONATE, ends);
        if (facts.offsetDistanceSquared() <= DETONATION_DISTANCE_SQUARED) return result(Action.DETONATE, Reason.IN_BLAST_RANGE);
        if (facts.age() % PATH_INTERVAL == 0 && facts.offsetDistanceSquared() > PATH_DISTANCE_SQUARED) {
            return result(Action.PATH, Reason.PATH_DUE);
        }
        return result(Action.WAIT, Reason.EN_ROUTE);
    }

    private static Result result(Action action, Reason reason) {
        return new Result(action, EnumSet.of(reason));
    }
}
