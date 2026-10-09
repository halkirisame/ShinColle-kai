package com.lulan.shincolle.ai.domain.formation;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;

/** Formation geometry; block safety and execution remain with the caller. */
public final class FormationLayoutPlanner {
    private FormationLayoutPlanner() { }
    public record Facing(boolean alongX, boolean positive) { }
    public static Facing facing(double toX, double toZ, double fromX, double fromZ) {
        double dx = toX - fromX, dz = toZ - fromZ;
        boolean alongX = Math.abs(dx) > Math.abs(dz);
        return new Facing(alongX, alongX ? dx >= 0D : dz >= 0D);
    }
    public static CommandPos round(MovementPoint point) {
        return new CommandPos((int) Math.floor(point.x()), (int) (point.y() + 0.5D), (int) Math.floor(point.z()));
    }
    public static CommandPos next(FormationPattern pattern, Facing face, CommandPos placed) {
        return pattern == FormationPattern.ECHELON
                ? new CommandPos(placed.x() + (face.positive() ? -2 : 2), placed.y(),
                        placed.z() + (face.positive() ? -2 : 2))
                : offset(placed, face, face.positive() ? -3 : 3, 0);
    }
    public static CommandPos place(FormationPattern pattern, FormationSlot slot, Facing face, CommandPos anchor) {
        int index = slot.index();
        if (index == 0) return anchor;
        if (pattern.sequential()) {
            CommandPos place = anchor;
            for (int i = 0; i < index; i++) place = next(pattern, face, place);
            return place;
        }
        int sign = face.positive() ? 1 : -1;
        int forward = 0, cross = 0;
        switch (pattern) {
            case DOUBLE_LINE -> {
                forward = (index == 2 || index == 3 ? 3 : index >= 4 ? -3 : 0) * sign;
                cross = index % 2 == 1 ? 3 : 0;
            }
            case DIAMOND -> {
                forward = switch (index) { case 1 -> 5; case 2, 3 -> 1; case 4 -> -3; default -> 2; } * sign;
                cross = index == 2 ? -4 : index == 3 ? 4 : 0;
            }
            case LINE_ABREAST -> cross = index % 2 == 1 ? (index + 1) / 2 * 3 : -index / 2 * 3;
            default -> throw new IllegalArgumentException("Sequential formation must be placed from its cursor");
        }
        return offset(anchor, face, forward, cross);
    }
    private static CommandPos offset(CommandPos anchor, Facing face, int forward, int cross) {
        return new CommandPos(anchor.x() + (face.alongX() ? forward : cross), anchor.y(),
                anchor.z() + (face.alongX() ? cross : forward));
    }
    public static boolean mayAdvanceRoute(FormationProjection projection) {
        return !(projection instanceof FormationProjection.Active active) || active.slot().flagship();
    }
    public static boolean repeatReturnsToFollow(CommandPos previous, CommandPos placed, boolean manual) {
        return manual && placed.equals(previous);
    }
}
