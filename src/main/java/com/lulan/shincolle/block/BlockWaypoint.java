package com.lulan.shincolle.block;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

public class BlockWaypoint extends BasicBlockContainer {

    public BlockWaypoint() {
        super(Properties.of().mapColor(MapColor.NONE).strength(1.0F).noOcclusion().noCollission());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityWaypoint(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)
                || !(level.getBlockEntity(pos) instanceof TileEntityWaypoint waypoint)) {
            return;
        }

        waypoint.setOwnerUUID(player.getUUID());
        player.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> {
            if (capa.getPlayerUID() > 0) {
                waypoint.setPlayerUID(capa.getPlayerUID());
            }
        });
    }
}
