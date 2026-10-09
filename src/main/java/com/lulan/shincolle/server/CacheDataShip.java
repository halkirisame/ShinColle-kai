package com.lulan.shincolle.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Cache data for ship UID → entity mapping.
 * <p>
 * Saved to disk via ShinWorldData for ship backup/recovery.
 * Used for commands, dupe checking, and radar.
 */
public class CacheDataShip {

    public int entityID;
    public ResourceKey<Level> dimension;
    public int classID;
    public boolean isDead;
    public int posX;
    public int posY;
    public int posZ;
    public CompoundTag entityNBT;
    public boolean sunk;
    public ResourceKey<Level> sunkDimension;
    public int sunkX;
    public int sunkY;
    public int sunkZ;

    public CacheDataShip(int eid, ResourceKey<Level> dimension, int cid, boolean isDead,
                         double posX, double posY, double posZ, CompoundTag nbt) {
        this(eid, dimension, cid, isDead, posX, posY, posZ, nbt, false, null, 0, 0, 0);
    }

    public CacheDataShip(int eid, ResourceKey<Level> dimension, int cid, boolean isDead,
                         double posX, double posY, double posZ, CompoundTag nbt,
                         boolean sunk, ResourceKey<Level> sunkDimension,
                         int sunkX, int sunkY, int sunkZ) {
        this.entityID = eid;
        this.dimension = dimension;
        this.classID = cid;
        this.isDead = isDead;
        this.posX = (int) posX;
        this.posY = (int) posY;
        this.posZ = (int) posZ;
        this.entityNBT = nbt;
        this.sunk = sunk;
        this.sunkDimension = sunkDimension;
        this.sunkX = sunkX;
        this.sunkY = sunkY;
        this.sunkZ = sunkZ;
    }

    public void setSunkLocation(ResourceKey<Level> dimension, int x, int y, int z) {
        this.sunk = true;
        this.sunkDimension = dimension;
        this.sunkX = x;
        this.sunkY = y;
        this.sunkZ = z;
    }

    public void clearSunkLocation() {
        this.sunk = false;
        this.sunkDimension = null;
        this.sunkX = 0;
        this.sunkY = 0;
        this.sunkZ = 0;
    }
}
