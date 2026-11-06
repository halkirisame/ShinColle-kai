package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.crane.CraneEntryApproach;
import com.lulan.shincolle.ai.domain.crane.CraneLifecycle;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Minecraft boundary for the typed crane lifecycle; the existing state slots remain the authority. */
public final class ShipCraneStateAdapter {
    private ShipCraneStateAdapter() {
    }

    public static CraneLifecycle.Phase phase(BasicEntityShip ship) {
        return switch (ship.getStateMinor(ID.M.CraneState)) {
            case 1 -> CraneLifecycle.Phase.WAITING;
            case 2 -> CraneLifecycle.Phase.WORKING;
            default -> CraneLifecycle.Phase.IDLE;
        };
    }

    public static boolean apply(BasicEntityShip ship, CraneLifecycle.Phase next) {
        if (next == CraneLifecycle.Phase.IDLE) {
            reset(ship);
            return true;
        }
        if (phase(ship) != next) {
            ship.setStateMinor(ID.M.CraneState, next == CraneLifecycle.Phase.WAITING ? 1 : 2);
        }
        return phase(ship) == next;
    }

    public static void reset(BasicEntityShip ship) {
        if (!ship.level().isClientSide()) {
            ship.setStateMinor(ID.M.CraneState, 0);
            ship.setStateTimer(ID.T.CraneTime, 0);
        }
    }

    public static boolean guards(BasicEntityShip ship, TileEntityCrane crane) {
        BlockPos pos = crane.getBlockPos();
        return ship.level() == crane.getLevel() && ship.hasGuardDestination() && ship.isGuardedInCurrentDimension()
                && ship.getGuardedEntity() == null && ship.getGuardedPos(0) == pos.getX()
                && ship.getGuardedPos(1) == pos.getY() && ship.getGuardedPos(2) == pos.getZ();
    }

    private static TileEntityCrane destination(BasicEntityShip ship) {
        if (!ship.hasGuardDestination() || !ship.isGuardedInCurrentDimension() || ship.getGuardedEntity() != null) return null;
        BlockPos pos = new BlockPos(ship.getGuardedPos(0), ship.getGuardedPos(1), ship.getGuardedPos(2));
        return ship.level().hasChunkAt(pos) && ship.level().getBlockEntity(pos) instanceof TileEntityCrane crane ? crane : null;
    }

    private static CraneLifecycle.ArrivalFacts facts(BasicEntityShip ship, TileEntityCrane crane) {
        boolean mount = ship.getVehicle() instanceof BasicEntityMount;
        Entity body = mount ? ship.getVehicle() : ship;
        boolean owned = crane != null && (crane.getOwnerUUID() != null
                ? crane.getOwnerUUID().equals(ship.getOwnerUUID())
                : crane.getPlayerUID() > 0 && crane.getPlayerUID() == ship.getPlayerUID());
        double distance = crane == null ? 0D : body.distanceToSqr(new Vec3(crane.getBlockPos().getX() + 0.5D,
                crane.getBlockPos().getY(), crane.getBlockPos().getZ() + 0.5D));
        return new CraneLifecycle.ArrivalFacts(ship.hasGuardDestination(), ship.isGuardedInCurrentDimension(),
                ship.getGuardedEntity() != null, ship.isOrderedToSit() || ship.getIsSitting(),
                ship.getIsLeashed() || mount && ((BasicEntityMount) body).isLeashed(), ship.isPassenger(), mount,
                crane != null, owned, distance, phase(ship));
    }

    /** Apply arrival before ordinary waypoint traversal, and report whether the crane owns this destination. */
    public static boolean arrive(BasicEntityShip ship) {
        TileEntityCrane crane = destination(ship);
        CraneLifecycle.ArrivalFacts facts = facts(ship, crane);
        CraneLifecycle.Arrival arrival = CraneLifecycle.arrive(facts);
        if (apply(ship, arrival.phase()) && arrival.dismountShips()) {
            if (ship.getVehicle() instanceof BasicEntityMount) ship.stopRiding();
            for (Entity passenger : List.copyOf(ship.getPassengers())) {
                if (passenger instanceof BasicEntityShip) passenger.stopRiding();
            }
        }
        if (arrival.phase() == CraneLifecycle.Phase.IDLE && !CraneLifecycle.destinationValid(facts)) {
            for (Entity passenger : ship.getPassengers()) {
                if (passenger instanceof BasicEntityShip carried) reset(carried);
            }
        }
        return CraneLifecycle.destinationValid(facts);
    }

    public static Optional<MovementIntent.CranePosition> motion(Entity host, MovementIntent standing) {
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        if (ship == null) return Optional.empty();
        TileEntityCrane crane = destination(ship);
        if (crane == null) return Optional.empty();
        BlockPos pos = crane.getBlockPos();
        return CraneLifecycle.motion(facts(ship, crane), host.isPassenger(),
                !ship.getStateFlag(ID.F.NoFuel) && ship.getStateMinor(ID.M.NumGrudge) > 0,
                crane.getDockedShip() == ship, standing, ShipCommandStateAdapter.handle(ship).dimension(),
                new MovementPoint(pos.getX(), pos.getY(), pos.getZ()), bounds(ship.getBoundingBox()),
                bounds(crane.getCaptureArea()));
    }

    private static CraneEntryApproach.Bounds bounds(AABB box) {
        return new CraneEntryApproach.Bounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
