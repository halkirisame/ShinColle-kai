package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Whether an owner is gliding or falling, from facts only. Rising is never airborne for this, so a jump
 * does not count until the owner starts down. The owner's own tick-to-tick height is used, because a
 * player's velocity is not kept on the server.
 */
public final class OwnerAirborneDetector {
    /** Server ticks without a further descent after which a falling owner with no support is taken as landed. */
    public static final long AIRBORNE_GRACE_TICKS = 10L;

    private OwnerAirborneDetector() { }

    /**
     * @param fallFlying gliding with elytra
     * @param supported  standing, swimming, riding or climbing, so not falling
     */
    public record Facts(boolean fallFlying, boolean supported) { }

    /**
     * @param descentAt the tick a descent was last observed while falling; present means falling is held
     */
    public record State(DimensionKey dimension, Optional<OwnerJumpDetector.Sample> last, OptionalLong descentAt,
                        boolean airborne) {
        public static State initial(DimensionKey dimension) {
            return new State(dimension, Optional.empty(), OptionalLong.empty(), false);
        }
    }

    /**
     * Falling is held once seen, because a position update can be late and then the height is merely
     * unchanged. It ends on support, a rise, or {@link #AIRBORNE_GRACE_TICKS} without a further descent.
     */
    public static State observe(State state, DimensionKey dimension, MovementPoint position, Facts facts, long now) {
        if (!state.dimension().equals(dimension)) state = State.initial(dimension);
        if (state.last().isPresent() && now <= state.last().get().tick()) return state;
        OptionalLong descentAt = state.descentAt();
        if (facts.supported()) {
            descentAt = OptionalLong.empty();
        } else if (state.last().isPresent()) {
            var last = state.last().get();
            if (position.y() > last.position().y()) {
                descentAt = OptionalLong.empty();
            } else if (now == last.tick() + 1 && position.y() < last.position().y()) {
                descentAt = OptionalLong.of(now);
            }
        }
        if (descentAt.isPresent() && now - descentAt.getAsLong() >= AIRBORNE_GRACE_TICKS) {
            descentAt = OptionalLong.empty();
        }
        return new State(dimension, Optional.of(new OwnerJumpDetector.Sample(now, position)), descentAt,
                facts.fallFlying() || descentAt.isPresent());
    }
}
