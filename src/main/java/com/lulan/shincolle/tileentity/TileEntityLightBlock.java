package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.init.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for invisible light source blocks.
 * Used for both air and liquid light variants.
 * Stores a tick counter for automatic removal after a set duration.
 */
public class TileEntityLightBlock extends BasicTileEntity {

    private int ticksRemaining = 200;
    private long expiresAt = -1L;

    public TileEntityLightBlock(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LIGHT_BLOCK.get(), pos, state);
    }

    public TileEntityLightBlock(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntityLightBlock tile) {
        if (tile.expiresAt < 0L) {
            tile.expiresAt = level.getGameTime() + Math.max(0, tile.ticksRemaining);
            tile.setChanged();
        }
        if (level.getGameTime() >= tile.expiresAt) {
            level.removeBlock(pos, false);
        }
    }

    public int getTicksRemaining() {
        if (expiresAt >= 0L && level != null) {
            return (int) Math.max(0L, expiresAt - level.getGameTime());
        }
        return ticksRemaining;
    }

    // ==================== Tick Logic ====================

    public void setTicksRemaining(int ticks) {
        this.ticksRemaining = Math.max(0, ticks);
        this.expiresAt = level != null ? level.getGameTime() + this.ticksRemaining : -1L;
        setChanged();
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("TicksRemaining", ticksRemaining);
        if (expiresAt >= 0L) {
            tag.putLong("ExpiresAt", expiresAt);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TicksRemaining")) {
            ticksRemaining = tag.getInt("TicksRemaining");
        }
        expiresAt = tag.contains("ExpiresAt") ? tag.getLong("ExpiresAt") : -1L;
    }
}
