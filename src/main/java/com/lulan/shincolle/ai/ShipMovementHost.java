package com.lulan.shincolle.ai;

/** A host whose movement goals move it through the movement executor under NEW: ships, hostile ships and mounts. */
public interface ShipMovementHost {
    ShipMovementExecutor shipMovementExecutor();
}
