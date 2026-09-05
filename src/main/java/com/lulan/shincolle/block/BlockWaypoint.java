package com.lulan.shincolle.block;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() || player.getItemInHand(hand).getItem() != ModItems.TARGET_WRENCH.get()
                || !(level.getBlockEntity(pos) instanceof TileEntityWaypoint waypoint)) {
            return InteractionResult.PASS;
        }

        boolean owned = waypoint.getOwnerUUID() != null
                && waypoint.getOwnerUUID().equals(player.getUUID())
                && player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .map(capa -> capa.getPlayerUID() > 0 && capa.getPlayerUID() == waypoint.getPlayerUID())
                .orElse(false);
        if (!owned) {
            return InteractionResult.PASS;
        }

        waypoint.nextWpStayTime();
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[ShinColle] Waypoint stay time: " + waypoint.getWpStayTime()));
        return InteractionResult.SUCCESS;
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
