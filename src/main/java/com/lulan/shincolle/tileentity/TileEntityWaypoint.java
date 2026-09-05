package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Block entity for the Waypoint block.
 * Stores owner UUID, linked waypoint chain (next/last), and paired chest
 * position.
 * Ported from 1.10.2 TileEntityWaypoint.
 */
public class TileEntityWaypoint extends BasicTileEntity implements ITileWaypoint {

    private UUID ownerUUID;
    private int playerUID = -1;
    private BlockPos nextPos = BlockPos.ZERO;
    private BlockPos lastPos = BlockPos.ZERO;
    private boolean nextWaypointSet;
    private boolean lastWaypointSet;
    private BlockPos chestPos = BlockPos.ZERO;
    private boolean pairedChestSet;
    private int wpstay = 0;

    public TileEntityWaypoint(BlockPos pos, BlockState state) {
        this(ModBlockEntities.WAYPOINT.get(), pos, state);
    }

    public TileEntityWaypoint(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ========== Owner ==========

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    public int getPlayerUID() {
        return playerUID;
    }

    public void setPlayerUID(int uid) {
        this.playerUID = uid;
        setChanged();
    }

    @Override
    public Entity getHostEntity() {
        return null;
    }

    // ========== Waypoint Route ==========

    public BlockPos getNextWaypoint() {
        return nextPos;
    }

    public void setNextWaypoint(BlockPos pos) {
        this.nextPos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.nextWaypointSet = pos != null;
        setChanged();
    }

    public BlockPos getLastWaypoint() {
        return lastPos;
    }

    public void setLastWaypoint(BlockPos pos) {
        this.lastPos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.lastWaypointSet = pos != null;
        setChanged();
    }

    public boolean hasNextWaypoint() {
        return nextWaypointSet;
    }

    public boolean hasLastWaypoint() {
        return lastWaypointSet;
    }

    // ========== Chest Pairing ==========

    public BlockPos getPairedChest() {
        return chestPos;
    }

    public void setPairedChest(BlockPos pos) {
        this.chestPos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.pairedChestSet = pos != null;
        setChanged();
    }

    @Override
    public boolean hasPairedChest() {
        return pairedChestSet;
    }

    // ========== Stay Time ==========

    public int getWpStayTime() {
        return wpstay;
    }

    public void setWpStayTime(int time) {
        this.wpstay = Math.max(0, Math.min(time, 16));
        setChanged();
    }

    /** Advance the raw waypoint stay setting, wrapping after the longest value. */
    public void nextWpStayTime() {
        setWpStayTime(this.wpstay == 16 ? 0 : this.wpstay + 1);
    }

    // ========== NBT ==========

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putInt("PlayerUID", playerUID);
        tag.putInt("NextX", nextPos.getX());
        tag.putInt("NextY", nextPos.getY());
        tag.putInt("NextZ", nextPos.getZ());
        tag.putBoolean("HasNextWaypoint", nextWaypointSet);
        tag.putInt("LastX", lastPos.getX());
        tag.putInt("LastY", lastPos.getY());
        tag.putInt("LastZ", lastPos.getZ());
        tag.putBoolean("HasLastWaypoint", lastWaypointSet);
        tag.putInt("ChestX", chestPos.getX());
        tag.putInt("ChestY", chestPos.getY());
        tag.putInt("ChestZ", chestPos.getZ());
        tag.putBoolean("HasPairedChest", pairedChestSet);
        tag.putInt("WpStay", wpstay);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        playerUID = tag.getInt("PlayerUID");
        nextPos = new BlockPos(tag.getInt("NextX"), tag.getInt("NextY"), tag.getInt("NextZ"));
        lastPos = new BlockPos(tag.getInt("LastX"), tag.getInt("LastY"), tag.getInt("LastZ"));
        // Legacy saves could not link world origin. Once saved by this version,
        // the explicit flag is authoritative and keeps origin/Y=0/negative-Y valid.
        nextWaypointSet = tag.contains("HasNextWaypoint")
                ? tag.getBoolean("HasNextWaypoint") : !nextPos.equals(BlockPos.ZERO);
        lastWaypointSet = tag.contains("HasLastWaypoint")
                ? tag.getBoolean("HasLastWaypoint") : !lastPos.equals(BlockPos.ZERO);
        if (!nextWaypointSet) {
            nextPos = BlockPos.ZERO;
        }
        if (!lastWaypointSet) {
            lastPos = BlockPos.ZERO;
        }
        chestPos = new BlockPos(tag.getInt("ChestX"), tag.getInt("ChestY"), tag.getInt("ChestZ"));
        // Original 1.10.2 used `this.chestPos = BlockPos.ORIGIN;` as the unset state.
        // World origin and negative Y are valid in 1.20.1, so new saves carry explicit presence.
        pairedChestSet = tag.contains("HasPairedChest")
                ? tag.getBoolean("HasPairedChest") : !chestPos.equals(BlockPos.ZERO);
        if (!pairedChestSet) {
            chestPos = BlockPos.ZERO;
        }
        wpstay = tag.getInt("WpStay");
    }
}
