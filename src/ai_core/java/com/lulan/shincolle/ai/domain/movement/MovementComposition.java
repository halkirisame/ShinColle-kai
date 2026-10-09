package com.lulan.shincolle.ai.domain.movement;

/**
 * Composes an attack goal's wish to close in with the region the movement intent allows. A
 * target outside the region is approached only as far as a point just inside its edge, so the
 * follow or guard goal never has to pull the ship back.
 */
public final class MovementComposition {
    /** How far inside the region's edge combat stops. */
    public static final double EDGE_MARGIN = 2D;
    /** How close to the edge point counts as there. */
    public static final double EDGE_ARRIVAL = 1.5D;

    private MovementComposition() { }

    public static CombatMove compose(MovementConstraint constraint, CombatMove.Request request) {
        if (request.holdForFire()) return new CombatMove.Hold(CombatMove.Reason.IN_POSITION);
        if (constraint instanceof MovementConstraint.NoCombatMovement) {
            return new CombatMove.Hold(CombatMove.Reason.NO_COMBAT_MOVEMENT);
        }
        if (!(constraint instanceof MovementConstraint.Within region)) {
            return new CombatMove.Approach(request.target(), CombatMove.Reason.TOWARD_TARGET);
        }
        double inner = region.radius() - EDGE_MARGIN;
        if (inner <= 0D) return new CombatMove.Hold(CombatMove.Reason.NO_COMBAT_MOVEMENT);

        MovementPoint anchor = region.anchor();
        double targetDistSq = anchor.distanceSq(request.target());
        if (targetDistSq <= inner * inner) {
            return new CombatMove.Approach(request.target(), CombatMove.Reason.TOWARD_TARGET);
        }
        double scale = inner / Math.sqrt(targetDistSq);
        MovementPoint edge = new MovementPoint(
                anchor.x() + (request.target().x() - anchor.x()) * scale,
                anchor.y() + (request.target().y() - anchor.y()) * scale,
                anchor.z() + (request.target().z() - anchor.z()) * scale);
        if (request.self().distanceSq(edge) <= EDGE_ARRIVAL * EDGE_ARRIVAL) {
            return new CombatMove.Hold(CombatMove.Reason.AT_REGION_EDGE);
        }
        return new CombatMove.Approach(edge, CombatMove.Reason.TO_REGION_EDGE);
    }
}
