package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.CommandPos;

/** What the ship is moving for right now. Combat positioning is not an intent. */
public sealed interface MovementIntent {
    record Sit() implements MovementIntent { }
    /** Run to the owner; the flee goal resolves the destination. */
    record Flee() implements MovementIntent { }
    record MoveTo(DimensionKey dimension, CommandPos position) implements MovementIntent { }
    record GuardPosition(DimensionKey dimension, CommandPos position) implements MovementIntent { }
    record GuardEntity(TargetHandle target) implements MovementIntent { }
    record FollowOwner() implements MovementIntent { }

    /** The intents the guarding goal walks for. */
    default boolean isGuard() {
        return this instanceof MoveTo || this instanceof GuardPosition || this instanceof GuardEntity;
    }
}
