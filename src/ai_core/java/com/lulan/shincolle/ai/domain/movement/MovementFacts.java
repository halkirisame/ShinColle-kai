package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.command.MovementOrder;

/**
 * Everything the intent resolver reads, as plain values.
 *
 * @param orderedToSit the sit command; the sit, flee, wander and pick-item checks read this
 * @param sittingPose  the sitting pose; the guard and follow checks read this instead
 * @param hostIsMount  the host is a mount acting for the ship it carries; mounts never flee
 * @param ownerPresent the owner resolves, is alive and is in the same level
 * @param riding       the host itself rides something
 * @param ridingShip   the host rides another ship
 * @param engaged      the host is engaged with a locked target (the combat intent is Engage)
 */
public record MovementFacts(MovementOrder order, boolean orderedToSit, boolean sittingPose, boolean hostIsMount,
                            float hpRatio, float fleeThreshold, boolean ownerPresent, double ownerDistanceSq,
                            boolean hasGrudge, boolean riding, boolean ridingShip, boolean leashed,
                            boolean crane, boolean fishing, boolean pickItemEnabled, boolean engaged) {
}
