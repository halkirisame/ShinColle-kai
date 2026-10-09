package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import java.util.Optional;

public final class LegacyCommandCodec {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final DimensionKey END = new DimensionKey("minecraft", "the_end");

    private LegacyCommandCodec() { }

    public static LegacyCommandFields encode(ShipCommandState state) {
        MovementOrder movement = state.movement();
        if (movement instanceof MovementOrder.Follow) {
            return new LegacyCommandFields(-1, -1, -1, 0, 0, null, null, true, false, state.sitting(), -1);
        }
        if (movement instanceof MovementOrder.GuardEntity guard) {
            DimensionKey dimension = guard.target().dimension();
            return new LegacyCommandFields(-1, -1, -1, legacyDimension(dimension), 2, dimension,
                    guard.target().uuid(), false, false, state.sitting(), -1);
        }
        DimensionKey dimension;
        CommandPos pos;
        int type;
        boolean release;
        if (movement instanceof MovementOrder.MoveTo move) {
            dimension = move.dimension();
            pos = move.position();
            type = 0;
            release = move.releaseOnArrival();
        } else {
            MovementOrder.GuardPosition guard = (MovementOrder.GuardPosition) movement;
            dimension = guard.dimension();
            pos = guard.position();
            type = 1;
            release = guard.releaseOnArrival();
        }
        return new LegacyCommandFields(pos.x(), pos.y(), pos.z(), legacyDimension(dimension), type,
                dimension, null, false, release, state.sitting(), -1);
    }

    public static ShipCommandState decode(LegacyCommandFields fields) {
        return decode(fields, OVERWORLD);
    }

    public static ShipCommandState decode(LegacyCommandFields fields, DimensionKey currentDimension) {
        MovementOrder movement;
        if (fields.canFollow()) {
            movement = new MovementOrder.Follow();
        } else if (fields.guardType() == 2) {
            movement = fields.guardedEntityUuid() == null ? new MovementOrder.Follow()
                    : new MovementOrder.GuardEntity(new TargetHandle(fields.guardedEntityUuid(),
                            fields.guardedDimension() == null ? currentDimension : fields.guardedDimension()));
        } else if (isClearedTuple(fields)) {
            movement = new MovementOrder.Follow();
        } else {
            DimensionKey dimension = fields.guardedDimension() == null
                    ? fromLegacyDimension(fields.guardDim()) : fields.guardedDimension();
            CommandPos pos = new CommandPos(fields.guardX(), fields.guardY(), fields.guardZ());
            movement = fields.guardType() <= 0
                    ? new MovementOrder.MoveTo(dimension, pos, fields.releaseOnArrival())
                    : new MovementOrder.GuardPosition(dimension, pos, fields.releaseOnArrival());
        }
        return new ShipCommandState(movement, fields.orderedToSit(), Optional.empty());
    }

    /** Whether the guard fields are exactly what clearing a command leaves, whatever the follow flag says. */
    public static boolean isClearedTuple(LegacyCommandFields fields) {
        return isClearedTuple(fields.guardType(), fields.guardX(), fields.guardY(), fields.guardZ(),
                fields.guardId(), fields.guardedDimension() != null, fields.guardedEntityUuid() != null);
    }

    /**
     * {@link #isClearedTuple(LegacyCommandFields)} for a caller that holds the saved fields themselves.
     * A destination at -1 on every axis is told apart by the dimension saved with it.
     */
    public static boolean isClearedTuple(int guardType, int guardX, int guardY, int guardZ, int guardId,
                                         boolean hasGuardedDimension, boolean hasGuardedEntity) {
        return guardType == 0 && guardX == -1 && guardY == -1 && guardZ == -1
                && !hasGuardedDimension && !hasGuardedEntity && guardId == -1;
    }

    private static int legacyDimension(DimensionKey dimension) {
        if (NETHER.equals(dimension)) return -1;
        if (END.equals(dimension)) return 1;
        return 0;
    }

    private static DimensionKey fromLegacyDimension(int dimension) {
        if (dimension == -1) return NETHER;
        if (dimension == 1) return END;
        return OVERWORLD;
    }
}
