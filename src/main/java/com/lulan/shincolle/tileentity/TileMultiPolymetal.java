package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.init.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block entity for Polymetal multiblock structure components.
 * Stores a reference to the core block position via BasicTileMulti.
 */
public class TileMultiPolymetal extends BasicTileMulti {

    private final Map<Direction, DelegatedCapability<IItemHandler>> itemCapabilities = new HashMap<>();
    private final Map<Direction, DelegatedCapability<IFluidHandler>> fluidCapabilities = new HashMap<>();
    private boolean capabilitiesValid = true;
    private boolean structureNotificationPending;
    private int structureUpdateDepth;

    public TileMultiPolymetal(BlockPos pos, BlockState state) {
        this(ModBlockEntities.POLYMETAL_MULTI.get(), pos, state);
    }

    public TileMultiPolymetal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        boolean previouslyHadCore = hasCorePos();
        BlockPos previousCorePos = getCorePos();
        super.load(tag);
        if (previouslyHadCore != hasCorePos() || !previousCorePos.equals(getCorePos())) {
            invalidateDelegatedCapabilities();
            if (level != null) {
                structureNotificationPending = true;
                notifyStructureNeighbors();
            }
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capabilitiesValid && level != null && hasCorePos()
                && level.getBlockEntity(getCorePos()) instanceof TileMultiGrudgeHeavy core && core.hasCorePos()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return getDelegatedCapability(core, ForgeCapabilities.ITEM_HANDLER, side, itemCapabilities).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return getDelegatedCapability(core, ForgeCapabilities.FLUID_HANDLER, side, fluidCapabilities).cast();
            }
        }
        return super.getCapability(capability, side);
    }

    private <T> LazyOptional<T> getDelegatedCapability(TileMultiGrudgeHeavy core, Capability<T> capability,
            @Nullable Direction side, Map<Direction, DelegatedCapability<T>> cache) {
        DelegatedCapability<T> cached = cache.get(side);
        if (cached != null) {
            if (cached.delegated().isPresent()) {
                return cached.delegated();
            }
            cached.invalidate();
            cache.remove(side);
        }
        LazyOptional<T> source = core.getCapability(capability, side);
        T handler = source.orElse(null);
        if (handler == null) {
            return LazyOptional.empty();
        }
        LazyOptional<T> delegated = LazyOptional.of(() -> handler);
        NonNullConsumer<LazyOptional<T>> listener = ignored -> delegated.invalidate();
        source.addListener(listener);
        cache.put(side, new DelegatedCapability<>(source, delegated, listener));
        return delegated;
    }

    @Override
    public void setCorePos(BlockPos pos) {
        boolean changed = !hasCorePos() || !getCorePos().equals(pos);
        super.setCorePos(pos);
        if (changed) {
            invalidateDelegatedCapabilities();
            structureNotificationPending = true;
        }
    }

    @Override
    public void resetCorePos() {
        boolean changed = hasCorePos();
        super.resetCorePos();
        invalidateDelegatedCapabilities();
        structureNotificationPending |= changed;
    }

    public void beginStructureUpdate() {
        structureUpdateDepth++;
    }

    public void endStructureUpdate() {
        structureUpdateDepth--;
        notifyStructureNeighbors();
    }

    public void notifyStructureNeighbors() {
        if (!structureNotificationPending || structureUpdateDepth != 0 || level == null || level.isClientSide) {
            return;
        }
        structureNotificationPending = false;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() == getBlockState().getBlock()) {
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    @Override
    public void invalidateCaps() {
        capabilitiesValid = false;
        invalidateDelegatedCapabilities();
        super.invalidateCaps();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capabilitiesValid = true;
    }

    private void invalidateDelegatedCapabilities() {
        List<DelegatedCapability<?>> capabilities = new ArrayList<>(itemCapabilities.values());
        capabilities.addAll(fluidCapabilities.values());
        itemCapabilities.clear();
        fluidCapabilities.clear();
        capabilities.forEach(DelegatedCapability::invalidate);
    }

    private record DelegatedCapability<T>(LazyOptional<T> source, LazyOptional<T> delegated,
            NonNullConsumer<LazyOptional<T>> listener) {
        private void invalidate() {
            if (source.isPresent()) {
                source.removeListener(listener);
            }
            delegated.invalidate();
        }
    }
}
