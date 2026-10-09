package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.OptionalLong;

/** A jump's short wait belongs to the host, so starting its follow goal cannot discard it. */
public record FollowRecallRequest(TargetHandle owner, long detectedAt) {
    public static final int WAIT_TICKS = 100;

    public OptionalLong remaining(TargetHandle currentOwner, long now) {
        if (!owner.equals(currentOwner) || now < detectedAt || now >= detectedAt + OwnerJumpDetector.HOLD_TICKS) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0L, detectedAt + WAIT_TICKS - now));
    }
}
