package com.lulan.shincolle.ai.domain.movement;

public enum MovementActivity {
    /** Walking for a move, guard or follow intent. */
    COMMANDED_MOVE,
    WANDER,
    PICK_ITEM,
    /** Turning the head to a nearby player, whether by watching or by a held combat ration. */
    IDLE_LOOK
}
