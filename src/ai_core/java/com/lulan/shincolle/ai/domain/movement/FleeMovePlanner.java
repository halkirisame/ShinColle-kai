package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.Optional;

/**
 * How fleeing moves the ship back to its owner: a path every 20 goal ticks, and a teleport when no
 * path could be made or the ship has been stuck on its way for long enough. When stuck it first
 * re-paths at once and then tries a point beside the owner. The goal ticks every other entity tick.
 */
public final class FleeMovePlanner {
    static final int REPATH = 20;
    static final double SPEED = 1.2D;
    /** Within this squared distance of the owner a fleeing ship never teleports. */
    static final double TELEPORT_MIN_DISTANCE_SQ = 100D;
    static final double TELEPORT_Y_OFFSET = 0.5D;

    private FleeMovePlanner() { }

    public static MovementState.Flee start() {
        return new MovementState.Flee(0);
    }

    /**
     * @param owner           the owner the goal started with, empty when it had none or it has died
     * @param body            the ship itself, or the mount it rides
     * @param ownerPosition   where the owner stands
     * @param ownerDistanceSq from the ship to the owner
     * @param sameLevel       the owner is in the ship's dimension
     * @param stuck           this goal tick's observation of the way back
     */
    public record Facts(Optional<TargetHandle> owner, MovementBody body, MovementPoint ownerPosition,
                        double ownerDistanceSq, boolean sameLevel, boolean canTeleport, StuckState stuck) {
    }

    /** On its way while it has an owner to flee to in its own dimension. */
    public static boolean travelling(Optional<TargetHandle> owner, boolean sameLevel) {
        return owner.isPresent() && sameLevel;
    }

    public static PlannedMove<MovementState.Flee> tick(MovementState.Flee state, Facts facts) {
        int repathIn = state.repathIn() - 1;
        if (facts.owner().isEmpty()) {
            return new PlannedMove<>(new MovementState.Flee(repathIn > 0 ? repathIn : REPATH), MovementPlan.NONE);
        }
        Optional<MovementStep.Teleport> jump = teleport(facts, MovementReason.TELEPORT_PATH_FAILED);
        MovementTarget owner = new MovementTarget.Entity(facts.owner().get());
        Optional<MovementStep.PathTo> recovery = StuckDetector.recovery(facts.stuck(), facts.body(), owner,
                facts.ownerPosition(), SPEED, jump);
        if (recovery.isPresent()) {
            return new PlannedMove<>(new MovementState.Flee(REPATH), MovementPlan.of(recovery.get()));
        }
        if (facts.stuck().due() == StuckStage.GIVE_UP) {
            Optional<MovementStep.Teleport> stuck = teleport(facts, MovementReason.TELEPORT_STUCK);
            if (stuck.isPresent()) {
                return new PlannedMove<>(new MovementState.Flee(REPATH), MovementPlan.of(stuck.get()));
            }
        }
        if (repathIn > 0) return new PlannedMove<>(new MovementState.Flee(repathIn), MovementPlan.NONE);
        return new PlannedMove<>(new MovementState.Flee(REPATH), MovementPlan.of(new MovementStep.PathTo(facts.body(),
                owner, SPEED, MovementReason.FLEE_TO_OWNER, jump)));
    }

    /** To the owner's feet, only when teleporting is on, the owner is far enough and in this dimension. */
    private static Optional<MovementStep.Teleport> teleport(Facts facts, MovementReason reason) {
        if (!facts.canTeleport() || facts.ownerDistanceSq() <= TELEPORT_MIN_DISTANCE_SQ || !facts.sameLevel()) {
            return Optional.empty();
        }
        MovementPoint owner = facts.ownerPosition();
        return Optional.of(new MovementStep.Teleport(facts.body(),
                new MovementPoint(owner.x(), owner.y() + TELEPORT_Y_OFFSET, owner.z()), reason,
                facts.owner().get().dimension()));
    }
}
