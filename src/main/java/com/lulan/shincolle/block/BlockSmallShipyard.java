package com.lulan.shincolle.block;

import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModBlockEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public class BlockSmallShipyard extends BasicBlockFacingContainer {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public BlockSmallShipyard() {
        super(Properties.of().mapColor(MapColor.METAL).strength(3.0F).noOcclusion().lightLevel(s -> s.getValue(ACTIVE) ? 15 : 4));
        this.registerDefaultState(this.defaultBlockState().setValue(ACTIVE, false));
    }

    /**
     * Update the ACTIVE blockstate property to match the tile entity's active status.
     * Called from TileEntitySmallShipyard when the active state changes.
     */
    public static void updateBlockState(boolean active, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockSmallShipyard && state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), 3);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEntitySmallShipyard tile) {
                NetworkHooks.openScreen(serverPlayer, tile, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntitySmallShipyard(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.SMALL_SHIPYARD.get(), TileEntitySmallShipyard::serverTick);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEntitySmallShipyard tile && !level.isClientSide()) {
                int[] mats = new int[4];
                boolean hasMats = false;
                for (int i = 0; i < mats.length; i++) {
                    mats[i] = tile.getMatStock(i);
                    hasMats |= mats[i] != 0;
                }
                int fuel = tile.getPowerRemained();
                CompoundTag fuelFluid = tile.writeFuelFluid();

                ItemStack dropStack = new ItemStack(this);
                if (hasMats || fuel != 0 || !fuelFluid.isEmpty()) {
                    CompoundTag tag = dropStack.getOrCreateTag();
                    tag.putIntArray("Mats", mats);
                    tag.putInt("Fuel", fuel);
                    tag.put("FuelFluid", fuelFluid);
                }

                BasicEntityItem dropEntity = new BasicEntityItem(
                        ModEntities.BASIC_ENTITY_ITEM.get(),
                        level,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.25D,
                        pos.getZ() + 0.5D,
                        dropStack);
                level.addFreshEntity(dropEntity);
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEntitySmallShipyard tile) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                if (tag.contains("Mats")) {
                    int[] mats = tag.getIntArray("Mats");
                    if (mats.length == 4) {
                        for (int i = 0; i < mats.length; i++) {
                            tile.setMatStock(i, mats[i]);
                        }
                    }
                }
                if (tag.contains("Fuel")) {
                    tile.setPowerRemained(tag.getInt("Fuel"));
                }
                if (tag.contains("FuelFluid")) {
                    tile.readFuelFluid(tag.getCompound("FuelFluid"));
                }
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.5;
            // Main smoke
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.05, 0.0);
            // Occasional extra smoke
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMOKE,
                        x + (random.nextDouble() - 0.5) * 0.3, y,
                        z + (random.nextDouble() - 0.5) * 0.3,
                        0.0, 0.03, 0.0);
            }
        }
    }
}
