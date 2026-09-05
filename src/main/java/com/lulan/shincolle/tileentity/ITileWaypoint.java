package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.entity.IShipOwner;

import net.minecraft.core.BlockPos;

/**
 * Interface for waypoint tile entities.
 * Supports linked waypoint navigation and paired chest interaction.
 */
public interface ITileWaypoint extends IShipOwner, ITileGuardPoint {

    BlockPos getLastWaypoint();

    boolean hasLastWaypoint();

    /**
     * Last waypoint position
     */
    void setLastWaypoint(BlockPos pos);

    BlockPos getNextWaypoint();

    boolean hasNextWaypoint();

    /**
     * Next waypoint position
     */
    void setNextWaypoint(BlockPos pos);

    int getWpStayTime();

    /**
     * Waypoint stay time
     */
    void setWpStayTime(int time);

    BlockPos getPairedChest();

    /**
     * Whether a paired chest position was explicitly assigned.
     */
    boolean hasPairedChest();

    /**
     * Paired chest position
     */
    void setPairedChest(BlockPos pos);
}
