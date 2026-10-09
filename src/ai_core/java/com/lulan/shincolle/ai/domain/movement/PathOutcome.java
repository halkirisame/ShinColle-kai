package com.lulan.shincolle.ai.domain.movement;

/** What became of a plan's path request. */
public enum PathOutcome {
    /** The plan requested no path. */
    NONE,
    ISSUED,
    /** No path could be made. */
    FAILED,
    /** The target is gone or in another dimension; nothing was done. */
    UNRESOLVED
}
