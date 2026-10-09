package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.Objects;
import java.util.Optional;

/** Which way a moving ship faces: the target it is engaged with, else what its movement looks at. */
public final class LookPlanner {
    /** Turn speeds of the attack goals when they aim at their target. */
    public static final float ENGAGED_YAW_SPEED = 30F;
    public static final float ENGAGED_PITCH_SPEED = 30F;

    private LookPlanner() {
    }

    public static LookRequest choose(Optional<TargetHandle> engaged, LookRequest fallback) {
        Objects.requireNonNull(engaged, "engaged");
        Objects.requireNonNull(fallback, "fallback");
        return engaged.map(target -> new LookRequest(new MovementTarget.Entity(target),
                ENGAGED_YAW_SPEED, ENGAGED_PITCH_SPEED, LookReason.ENGAGED_TARGET)).orElse(fallback);
    }
}
