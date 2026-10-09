package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A movement goal's timers and memory under NEW, one record per goal and held by that goal, so it
 * lives and starts over exactly as the goal's own fields did. Counts named {@code …In} or
 * {@code …Timer} change once per goal tick; times named {@code …At} are entity tick counts.
 * Server only, never saved.
 */
public sealed interface MovementState {

    /**
     * @param repathIn       goal ticks until the next path request
     * @param timeTimer      goal ticks stuck in a row, for the time teleport
     * @param farTimer       goal ticks spent far from the owner, for the distance teleport
     * @param ownerResolveAt when the owner and the destination are next looked up
     * @param destination    where the ship walks: the owner, or its formation place
     * @param anchorMemory   where the owner stood when the formation place was last worked out
     * @param destinationKind what {@code destination} stood for when it was written, kept with it
     */
    record Follow(int repathIn, int timeTimer, int farTimer, int ownerResolveAt, MovementPoint destination,
                  MovementPoint anchorMemory, FollowDestination destinationKind) implements MovementState {
        public Follow {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(anchorMemory, "anchorMemory");
            Objects.requireNonNull(destinationKind, "destinationKind");
        }

        /** A destination that is the owner. */
        public Follow(int repathIn, int timeTimer, int farTimer, int ownerResolveAt, MovementPoint destination,
                      MovementPoint anchorMemory) {
            this(repathIn, timeTimer, farTimer, ownerResolveAt, destination, anchorMemory, FollowDestination.OWNER);
        }
    }

    /**
     * @param stillTimer     goal ticks stuck in a row, for the time teleport
     * @param anchorResolveAt when the guarded point is next looked up
     * @param moving         whether the last path request was accepted
     */
    record Guard(int repathIn, int stillTimer, int farTimer, int anchorResolveAt, MovementPoint destination,
                 MovementPoint anchorMemory, boolean moving) implements MovementState {
        public Guard {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(anchorMemory, "anchorMemory");
        }
    }

    record Flee(int repathIn) implements MovementState { }

    /**
     * @param abandoned    each item given up on, with the entity tick from which it may be chosen again
     * @param failing      the item whose takes have been failing in a row, empty if none
     * @param failingSince the entity tick of its first failed take
     * @param failingLast  the entity tick of its latest failed take
     */
    record PickItem(int scanAt, Map<TargetHandle, Integer> abandoned, Optional<TargetHandle> failing,
                    int failingSince, int failingLast) implements MovementState {
        public PickItem {
            abandoned = Map.copyOf(abandoned);
            Objects.requireNonNull(failing, "failing");
        }
    }

    /** The reason of the attack goal's last combat move, empty before its first. */
    record Combat(Optional<CombatMove.Reason> last) implements MovementState {
        public static final Combat NONE = new Combat(Optional.empty());

        public Combat {
            Objects.requireNonNull(last, "last");
        }
    }
}
