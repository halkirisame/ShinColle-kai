package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.IShipNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Open door goal.
 * Ported from EntityAIShipOpenDoor (setMutexBits: 0)
 */
public class ShipOpenDoorGoal extends Goal {

    private final Mob entity;
    private final boolean closeDoor;
    private final List<BlockPos> doorPositions = new ArrayList<>();
    private int waitTimer;

    public ShipOpenDoorGoal(IShipNavigator host, boolean closeDoor) {
        this.entity = (Mob) host;
        this.closeDoor = closeDoor;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.entity.horizontalCollision) {
            this.doorPositions.clear();
            // Scan the full collision volume. Four foot-level blocks miss
            // doors hit by wide/tall hulls and upper door halves.
            AABB search = this.entity.getBoundingBox().inflate(1D, 0.5D, 1D);
            BlockPos min = BlockPos.containing(search.minX, search.minY, search.minZ);
            BlockPos max = BlockPos.containing(search.maxX, search.maxY, search.maxZ);
            for (BlockPos checkPos : BlockPos.betweenClosed(min, max)) {
                BlockState state = this.entity.level().getBlockState(checkPos);
                if (state.is(BlockTags.WOODEN_DOORS) || state.getBlock() instanceof FenceGateBlock) {
                    this.doorPositions.add(checkPos.immutable());
                }
            }
            return !this.doorPositions.isEmpty();
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.closeDoor && this.waitTimer > 0;
    }

    @Override
    public void start() {
        this.waitTimer = 20;
        for (BlockPos doorPos : this.doorPositions) {
            BlockState state = this.entity.level().getBlockState(doorPos);
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(this.entity, this.entity.level(), state, doorPos, true);
            } else if (state.getBlock() instanceof FenceGateBlock) {
                this.entity.level().setBlock(doorPos, state.setValue(FenceGateBlock.OPEN, true), 10);
            }
        }
    }

    @Override
    public void stop() {
        if (this.closeDoor) {
            for (BlockPos doorPos : this.doorPositions) {
                BlockState state = this.entity.level().getBlockState(doorPos);
                if (state.getBlock() instanceof DoorBlock door) {
                    door.setOpen(this.entity, this.entity.level(), state, doorPos, false);
                } else if (state.getBlock() instanceof FenceGateBlock) {
                    this.entity.level().setBlock(doorPos, state.setValue(FenceGateBlock.OPEN, false), 10);
                }
            }
        }
        this.doorPositions.clear();
    }

    @Override
    public void tick() {
        --this.waitTimer;
    }
}
