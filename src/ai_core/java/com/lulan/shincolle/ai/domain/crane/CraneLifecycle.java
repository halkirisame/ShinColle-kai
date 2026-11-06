package com.lulan.shincolle.ai.domain.crane;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pure crane arrival, queue selection, work completion and positioning decisions. */
public final class CraneLifecycle {
    public enum Phase { IDLE, WAITING, WORKING }
    public enum WaitMode { NO_WORK, FULL, EMPTY, EXCESS, REMAIN, TIMED }

    public record ArrivalFacts(boolean blockGuard, boolean sameDimension, boolean guardingEntity,
                               boolean sitting, boolean leashed, boolean riding, boolean ridingMount,
                               boolean craneDestination, boolean owned, double distanceSq, Phase phase) { }

    public record Arrival(Phase phase, boolean dismountShips) { }

    public record Candidate(UUID ship, Phase phase, boolean alive, boolean owned, boolean guardsCrane) { }

    public record Work(boolean loaded, boolean unloaded, boolean fluid, boolean energy) {
        public boolean any() {
            return loaded || unloaded || fluid || energy;
        }

        public int successes() {
            return (loaded ? 1 : 0) + (unloaded ? 1 : 0) + (fluid ? 1 : 0) + (energy ? 1 : 0);
        }
    }

    public record InventoryCompletion(boolean full, boolean empty, boolean excess, boolean remain) { }

    private CraneLifecycle() {
    }

    public static boolean destinationValid(ArrivalFacts facts) {
        return facts.blockGuard() && facts.sameDimension() && !facts.guardingEntity()
                && facts.craneDestination() && facts.owned();
    }

    public static boolean eligible(ArrivalFacts facts) {
        return destinationValid(facts) && !facts.sitting() && !facts.leashed() && (!facts.riding() || facts.ridingMount());
    }

    public static Arrival arrive(ArrivalFacts facts) {
        if (!destinationValid(facts)) return new Arrival(Phase.IDLE, false);
        if (!eligible(facts)) return new Arrival(facts.phase(), false);
        if (facts.phase() == Phase.IDLE && facts.distanceSq() < 25D) {
            return new Arrival(Phase.WAITING, true);
        }
        return new Arrival(facts.phase(), false);
    }

    public static Optional<UUID> choose(List<Candidate> candidates) {
        for (Phase phase : new Phase[] {Phase.WORKING, Phase.WAITING}) {
            for (Candidate candidate : candidates) {
                if (candidate.phase() == phase && candidate.alive() && candidate.owned() && candidate.guardsCrane()) {
                    return Optional.of(candidate.ship());
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<MovementIntent.CranePosition> motion(ArrivalFacts facts, boolean hostRiding,
            boolean fueled, boolean captured, MovementIntent standing, DimensionKey dimension, MovementPoint crane,
            CraneEntryApproach.Bounds body, CraneEntryApproach.Bounds capture) {
        if (!eligible(facts) || hostRiding || !fueled) return Optional.empty();
        MovementPoint below = new MovementPoint(crane.x() + 0.5D, crane.y() - 2D, crane.z() + 0.5D);
        if (facts.phase() == Phase.WORKING && captured) {
            return Optional.of(new MovementIntent.CranePosition(dimension, below, 0.5D, MovementReason.CRANE_DOCK));
        }
        if (CraneEntryApproach.decide(facts, hostRiding, fueled, captured, standing, body, capture).approach()) {
            return Optional.of(new MovementIntent.CranePosition(dimension, below, 1D, MovementReason.CRANE_APPROACH));
        }
        if (facts.phase() == Phase.IDLE && facts.distanceSq() >= 25D
                && (standing instanceof MovementIntent.GuardPosition || standing instanceof MovementIntent.MoveTo)) {
            return Optional.of(new MovementIntent.CranePosition(dimension, below, 1D, MovementReason.CRANE_APPROACH));
        }
        return Optional.empty();
    }

    public static boolean complete(WaitMode mode, Work work, InventoryCompletion inventory,
                                    int elapsedTicks, int waitTicks) {
        return switch (mode) {
            case NO_WORK -> !work.any();
            case FULL -> inventory.full();
            case EMPTY -> inventory.empty();
            case EXCESS -> !work.fluid() && !work.energy() && inventory.excess();
            case REMAIN -> !work.fluid() && !work.energy() && inventory.remain();
            case TIMED -> elapsedTicks >= waitTicks;
        };
    }
}
