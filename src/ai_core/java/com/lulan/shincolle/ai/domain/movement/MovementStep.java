package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.Objects;
import java.util.Optional;

/** One move of a plan. */
public sealed interface MovementStep {
    MovementBody body();

    MovementReason reason();

    /** A sequence's velocity, with the command's permitted region checked by the executor. */
    record SkillMotion(MovementBody body, MovementPoint velocity, MovementReason reason) implements MovementStep {
        public SkillMotion {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(velocity, "velocity");
            if (reason != MovementReason.SKILL_ATTACK) throw new IllegalArgumentException("not a skill motion");
        }
    }

    /** Drop the current path. */
    record Stop(MovementBody body, MovementReason reason) implements MovementStep {
        public Stop {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Request a path; {@code onFailure} is carried out only when no path could be made. */
    record PathTo(MovementBody body, MovementTarget target, double speed, MovementReason reason,
                  Optional<Teleport> onFailure) implements MovementStep {
        public PathTo {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(onFailure, "onFailure");
        }

        public PathTo(MovementBody body, MovementTarget target, double speed, MovementReason reason) {
            this(body, target, speed, reason, Optional.empty());
        }
    }

    /**
     * Drop the path and move the body to {@code destination}, or beside it, when {@link TeleportSafety}
     * allows it.
     *
     * @param dimension where the destination is
     */
    record Teleport(MovementBody body, MovementPoint destination, MovementReason reason, DimensionKey dimension)
            implements MovementStep {
        public Teleport {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(dimension, "dimension");
        }
    }
}
