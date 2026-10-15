package com.lulan.shincolle.utility;

import com.lulan.shincolle.ai.ShipFormationStateAdapter;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandDecision;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipTransitionReason;
import com.lulan.shincolle.ai.domain.formation.FormationLayoutPlanner;
import com.lulan.shincolle.ai.domain.formation.FormationPattern;
import com.lulan.shincolle.ai.domain.movement.FormationSlot;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Helper for formation buff calculations and formation position calculation.
 * <p>
 * Formation types:
 * 1: Line Ahead (単縦陣)
 * 2: Double Line (複縦陣)
 * 3: Diamond (輪形陣)
 * 4: Echelon (梯形陣)
 * 5: Line Abreast (単横陣)
 * <p>
 * Ported from 1.10.2 FormationHelper.
 */
public class FormationHelper {

    /**
     * Get formation buff value by formation ID and slot.
     *
     * @param formatID   formation type (1=Line Ahead, 2=Double Line, etc.)
     * @param formatSlot slot position in formation (0-5)
     * @return float array of formation buff values indexed by ID.Attrs
     */
    public static float[] getFormationBuffValue(int formatID, int formatSlot) {
        int key = formatID * 10 + formatSlot;

        if (Values.FormationAttrs.containsKey(key)) {
            return Values.FormationAttrs.get(key).clone();
        }

        return AttrsAdv.getResetFormationValue();
    }

    // ========== Formation Direction ==========

    /**
     * Calculate formation face direction from movement vector.
     *
     * @return boolean[2]: [0]=along X axis, [1]=face positive direction
     */
    public static boolean[] getFormationDirection(double toX, double toZ, double fromX, double fromZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        boolean[] face = new boolean[2];

        // along X if |dx| > |dz|
        face[0] = Math.abs(dx) > Math.abs(dz);

        if (face[0]) {
            face[1] = dx >= 0;
        } else {
            face[1] = dz >= 0;
        }

        return face;
    }

    // ========== Guard Position Calculation ==========

    /**
     * Return guarding entity position in formation.
     * <p>
     * 1. Set guard target as flagship position
     * 2. Calc formation position by guard target posXZ and oldXZ
     */
    public static double[] getFormationGuardingPos(IShipAttackBase host, Entity target,
                                                   double oldX, double oldZ) {
        double[] pos = new double[]{target.getX(), target.getY(), target.getZ()};
        var active = ShipFormationStateAdapter.active(host);
        if (active.isEmpty()) return pos;
        int formatID = active.get().pattern().legacyId();
        int formatPos = active.get().slot().index();

        // no formation, return target position
        if (formatID <= 0)
            return pos;

        // check error position
        if (formatPos < 0 || formatPos > 5)
            formatPos = 0;

        // calc formation position by formation type
        boolean[] faceXP = getFormationDirection(target.getX(), target.getZ(), oldX, oldZ);

        int[] tempPos = calcFormationPos(formatID, formatPos, pos, faceXP);

        if (tempPos != null) {
            // check block is safe
            Level level = target.level();
            tempPos = BlockHelper.getSafeBlockWithin5x5(level, tempPos[0], tempPos[1], tempPos[2]);

            if (tempPos != null) {
                pos[0] = tempPos[0];
                pos[1] = tempPos[1];
                pos[2] = tempPos[2];
            }
        }

        return pos;
    }

    /**
     * Calculate formation position for a ship based on formation type.
     *
     * @param formatID    formation type
     * @param formatPos   slot position (0=flagship, 1-5)
     * @param flagshipPos flagship position {x, y, z}
     * @param faceXP      direction: [0]=along X, [1]=face positive
     * @return new position {x, y, z}
     */
    public static int[] calcFormationPos(int formatID, int formatPos, double[] flagshipPos, boolean[] faceXP) {
        var anchor = FormationLayoutPlanner.round(new MovementPoint(flagshipPos[0], flagshipPos[1], flagshipPos[2]));
        var pattern = FormationPattern.fromLegacy(formatID);
        if (pattern.isEmpty()) return coordinates(anchor);
        return coordinates(FormationLayoutPlanner.place(pattern.get(),
                FormationSlot.placedAs(FormationSlot.fromLegacy(formatPos)),
                new FormationLayoutPlanner.Facing(faceXP[0], faceXP[1]), anchor));
    }

    /**
     * Apply a safe guard position for a line-ahead or echelon formation member,
     * then return the following member's target position.
     */
    public static int[] setFormationPosAndApplyGuardPos1(BasicEntityShip ship, int formatType,
                                                            boolean alongX, boolean faceP,
                                                            int x, int y, int z, Level level) {
        FormationPlacement placement = calculateFormationPosition1(formatType, alongX, faceP, x, y, z, level);
        int[] pos = placement.position();
        applyShipGuard(ship, pos[0], pos[1], pos[2], true);
        return placement.next();
    }

    public record FormationPlacement(int[] position, int[] next) { }

    private static int[] coordinates(CommandPos place) { return new int[]{place.x(), place.y(), place.z()}; }

    /** Shared NEW placement boundary for manual commands and flagship route distribution. */
    public static FormationPlacement calculateFormationPlacementNew(BasicEntityShip ship, FormationPattern pattern,
            FormationLayoutPlanner.Facing facing, CommandPos cursor, CommandPos anchor, Level level) {
        CommandPos requested = pattern.sequential() ? cursor
                : FormationLayoutPlanner.place(pattern,
                        ShipFormationStateAdapter.active(ship).map(active -> active.slot()).orElse(FormationSlot.FLAGSHIP),
                        facing, anchor);
        int[] safe = BlockHelper.getSafeBlockWithin5x5(level, requested.x(), requested.y(), requested.z());
        if (safe == null) {
            int[] fallback = coordinates(pattern.sequential() ? cursor : anchor);
            return new FormationPlacement(fallback, fallback);
        }
        CommandPos placed = new CommandPos(safe[0], safe[1], safe[2]);
        return new FormationPlacement(safe, coordinates(pattern.sequential()
                ? FormationLayoutPlanner.next(pattern, facing, placed) : cursor));
    }

    public static FormationPlacement calculateFormationPosition1(int formatType, boolean alongX, boolean faceP,
                                                                  int x, int y, int z, Level level) {
        int[] pos = BlockHelper.getSafeBlockWithin5x5(level, x, y, z);

        if (pos != null) {
            var pattern = FormationPattern.fromLegacy(formatType).orElse(FormationPattern.LINE_AHEAD);
            return new FormationPlacement(pos, coordinates(FormationLayoutPlanner.next(pattern,
                    new FormationLayoutPlanner.Facing(alongX, faceP), new CommandPos(pos[0], pos[1], pos[2]))));
        }
        int[] fallback = new int[]{x, y, z};
        return new FormationPlacement(fallback, fallback);
    }

    /**
     * Apply a safe guard position for a double-line, diamond, or line-abreast
     * formation member.
     */
    public static void setFormationPosAndApplyGuardPos2(BasicEntityShip ship, int formatType,
                                                           boolean alongX, boolean faceP,
                                                           int x, int y, int z, Level level) {
        int[] pos = calculateFormationPosition2(ship, formatType, alongX, faceP, x, y, z, level);
        applyShipGuard(ship, pos[0], pos[1], pos[2], true);
    }

    public static int[] calculateFormationPosition2(BasicEntityShip ship, int formatType,
                                                     boolean alongX, boolean faceP,
                                                     int x, int y, int z, Level level) {
        var pattern = FormationPattern.fromLegacy(formatType);
        if (pattern.isEmpty()) return new int[]{x, y, z};
        return calculateFormationPlacementNew(ship, pattern.get(), new FormationLayoutPlanner.Facing(alongX, faceP),
                new CommandPos(x, y, z), new CommandPos(x, y, z), level).position();
    }

    // ========== Formation Position Functions ==========

    /**
     * Calculate next LINE AHEAD position.
     *
     * <pre>
     *        0
     *        1
     *        2
     *        3
     *        4
     *        5
     * </pre>
     */
    public static int[] nextLineAheadPos(boolean alongX, boolean faceP, int x, int y, int z) {
        int[] pos = new int[]{x, y, z};

        if (alongX) {
            if (faceP) {
                pos[0] -= 3;
            } else {
                pos[0] += 3;
            }
        } else {
            if (faceP) {
                pos[2] -= 3;
            } else {
                pos[2] += 3;
            }
        }

        return pos;
    }

    /**
     * Calculate next DOUBLE LINE position.
     *
     * <pre>
     *      2  3
     *      0  1
     *      4  5
     * </pre>
     */
    public static int[] nextDoubleLinePos(boolean alongX, boolean faceP, int formatPos, int x, int y, int z) {
        int[] pos = new int[]{x, y, z};

        switch (formatPos) {
            case 1:
                if (alongX) {
                    pos[2] += 3;
                } else {
                    pos[0] += 3;
                }
                break;
            case 2:
                if (alongX) {
                    pos[0] += faceP ? 3 : -3;
                } else {
                    pos[2] += faceP ? 3 : -3;
                }
                break;
            case 3:
                if (alongX) {
                    pos[0] += faceP ? 3 : -3;
                    pos[2] += 3;
                } else {
                    pos[0] += 3;
                    pos[2] += faceP ? 3 : -3;
                }
                break;
            case 4:
                if (alongX) {
                    pos[0] += faceP ? -3 : 3;
                } else {
                    pos[2] += faceP ? -3 : 3;
                }
                break;
            case 5:
                if (alongX) {
                    pos[0] += faceP ? -3 : 3;
                    pos[2] += 3;
                } else {
                    pos[0] += 3;
                    pos[2] += faceP ? -3 : 3;
                }
                break;
        }

        return pos;
    }

    /**
     * Calculate DIAMOND position.
     *
     * <pre>
     *         1
     *      2  5  3
     *         0
     *         4
     * </pre>
     */
    public static int[] nextDiamondPos(boolean alongX, boolean faceP, int formatPos, int x, int y, int z) {
        int[] pos = new int[]{x, y, z};

        switch (formatPos) {
            case 1:
                if (alongX) {
                    pos[0] += faceP ? 5 : -5;
                } else {
                    pos[2] += faceP ? 5 : -5;
                }
                break;
            case 2:
                if (alongX) {
                    pos[0] += faceP ? 1 : -1;
                    pos[2] -= 4;
                } else {
                    pos[0] -= 4;
                    pos[2] += faceP ? 1 : -1;
                }
                break;
            case 3:
                if (alongX) {
                    pos[0] += faceP ? 1 : -1;
                    pos[2] += 4;
                } else {
                    pos[0] += 4;
                    pos[2] += faceP ? 1 : -1;
                }
                break;
            case 4:
                if (alongX) {
                    pos[0] += faceP ? -3 : 3;
                } else {
                    pos[2] += faceP ? -3 : 3;
                }
                break;
            case 5:
                if (alongX) {
                    pos[0] += faceP ? 2 : -2;
                } else {
                    pos[2] += faceP ? 2 : -2;
                }
                break;
        }

        return pos;
    }

    /**
     * Calculate next ECHELON position.
     *
     * <pre>
     *                   0
     *              1
     *            2
     *          3
     *        4
     *      5
     * </pre>
     */
    public static int[] nextEchelonPos(boolean faceP, int x, int y, int z) {
        int[] pos = new int[]{x, y, z};

        if (faceP) {
            pos[0] -= 2;
            pos[2] -= 2;
        } else {
            pos[0] += 2;
            pos[2] += 2;
        }

        return pos;
    }

    /**
     * Calculate LINE ABREAST position.
     *
     * <pre>
     *  4  2  0  1  3  5
     * </pre>
     */
    public static int[] nextLineAbreastPos(boolean alongX, int formatPos, int x, int y, int z) {
        int[] pos = new int[]{x, y, z};

        switch (formatPos) {
            case 1:
                if (alongX) {
                    pos[2] += 3;
                } else {
                    pos[0] += 3;
                }
                break;
            case 2:
                if (alongX) {
                    pos[2] -= 3;
                } else {
                    pos[0] -= 3;
                }
                break;
            case 3:
                if (alongX) {
                    pos[2] += 6;
                } else {
                    pos[0] += 6;
                }
                break;
            case 4:
                if (alongX) {
                    pos[2] -= 6;
                } else {
                    pos[0] -= 6;
                }
                break;
            case 5:
                if (alongX) {
                    pos[2] += 9;
                } else {
                    pos[0] += 9;
                }
                break;
        }

        return pos;
    }

    // ========== Ship Guard Commands ==========

    /**
     * Set ship guard to a block position, and check if guard position is the same.
     * GuardType = 1: guard a block
     * <p>
     * If same position and not forceSet, cancel guard mode.
     * Otherwise apply guard mode with block guard.
     */
    public static void applyShipGuard(BasicEntityShip ship, int x, int y, int z, boolean forceSet) {
        applyShipGuard(ship, x, y, z, forceSet, 1);
    }

    public static void applyShipGuard(BasicEntityShip ship, int x, int y, int z,
                                      boolean forceSet, int guardType) {
        if (ship == null || ship.level().isClientSide()) return;
        guardType = Mth.clamp(guardType, 0, 1);
        MovementOrder order = ship.getCommandState().movement();
        boolean positioned = order instanceof MovementOrder.MoveTo || order instanceof MovementOrder.GuardPosition;
        var id = ship.level().dimension().location();
        DimensionKey dimension = new DimensionKey(id.getNamespace(), id.getPath());
        var currentDimension = order instanceof MovementOrder.MoveTo move ? move.dimension()
                : order instanceof MovementOrder.GuardPosition guard ? guard.dimension() : null;
        var command = CommandDecision.position(dimension, new CommandPos(x, y, z), false, forceSet, positioned,
                currentDimension, order.destination().orElse(null), guardType == 1);
        // clear attack target
        ship.setTarget(null);
        ship.setEntityTarget(null);
        ship.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.HELPER_COMMAND),
                new CommandStateOp.Apply(command));
    }

    /**
     * Set ship guard to an entity.
     * GuardType = 2: guard an entity
     * <p>
     * If same entity target, cancel guard mode.
     * Otherwise apply entity guard mode.
     */
    public static void applyShipGuardEntity(BasicEntityShip ship, Entity guarded) {
        if (ship == null || guarded == null || ship.level().isClientSide()) return;
        MovementOrder order = ship.getCommandState().movement();
        var command = CommandDecision.guardEntity(ShipCommandStateAdapter.handle(guarded),
                order instanceof MovementOrder.GuardEntity guard ? guard.target() : null);
        ship.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.HELPER_COMMAND),
                new CommandStateOp.Apply(command));
    }
}
