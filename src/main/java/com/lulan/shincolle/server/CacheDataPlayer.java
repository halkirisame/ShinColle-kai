package com.lulan.shincolle.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Cache data for player UID → entity mapping.
 * <p>
 * This is a runtime cache ONLY — NOT saved to disk.
 * Used for owner checking and radar display.
 */
public class CacheDataPlayer {

    public int entityID;
    public ResourceKey<Level> dimension;
    public boolean hasTeam;
    public int posX;
    public int posY;
    public int posZ;
    public CompoundTag capaNBT;

    public CacheDataPlayer(int eid, ResourceKey<Level> dimension, boolean hasTeam,
                           double posX, double posY, double posZ, CompoundTag nbt) {
        this.entityID = eid;
        this.dimension = dimension;
        this.hasTeam = hasTeam;
        this.posX = Mth.floor(posX);
        this.posY = Mth.floor(posY);
        this.posZ = Mth.floor(posZ);
        this.capaNBT = nbt;
    }
}
