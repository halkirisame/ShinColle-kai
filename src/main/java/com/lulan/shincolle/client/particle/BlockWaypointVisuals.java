package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.ParticleHelper;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Tool-visible waypoint markers and next-point/chest connection sprays. */
public final class BlockWaypointVisuals {
    private BlockWaypointVisuals() {
    }

    public static void tick(Level level, BlockPos pos, TileEntityWaypoint tile) {
        if (!level.isClientSide() || !holdingRouteTool() || (level.getGameTime() & 7) != 0) {
            return;
        }
        ParticleHelper.spawnTeamCircleAt(level, pos.getX() + 0.5D, pos.getY() - 0.25D, pos.getZ() + 0.5D, 9);
        if ((level.getGameTime() & 15) == 0) {
            if (tile.hasNextWaypoint()) {
                connection(level, pos, tile.getNextWaypoint(), 38);
            }
            if (tile.hasPairedChest()) {
                connection(level, pos, tile.getPairedChest(), 39);
            }
            if ((level.getGameTime() & 31) == 0) {
                if (tile.hasLastWaypoint() || tile.hasNextWaypoint() || tile.hasPairedChest()) {
                    showRouteText(level, pos, tile.getOwnerUUID(), tile.getPlayerUID(),
                            tile.getLastWaypoint(), tile.getNextWaypoint(), tile.getPairedChest());
                }
                ParticleHelper.spawnTeamCircleAt(level, pos.getX() + 0.5D, pos.getY() - 0.25D,
                        pos.getZ() + 0.5D, 8);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, TileEntityCrane tile) {
        if (!level.isClientSide() || !holdingRouteTool() || (level.getGameTime() & 15) != 0) {
            return;
        }
        if (!tile.getNextPos().equals(BlockPos.ZERO)) {
            connection(level, pos, tile.getNextPos(), 38);
        }
        if (tile.isPaired()) {
            connection(level, pos, tile.getChestPos(), 39);
        }
        if ((level.getGameTime() & 31) == 0 && (!tile.getLastPos().equals(BlockPos.ZERO)
                || !tile.getNextPos().equals(BlockPos.ZERO) || tile.isPaired())) {
            showRouteText(level, pos, tile.getOwnerUUID(), tile.getPlayerUID(),
                    tile.getLastPos(), tile.getNextPos(), tile.getChestPos());
        }
    }

    private static void showRouteText(Level level, BlockPos pos, UUID ownerUUID, int ownerUID,
                                      BlockPos from, BlockPos to, BlockPos chest) {
        String name = "";
        for (var player : level.players()) {
            boolean owner = ownerUUID != null ? ownerUUID.equals(player.getUUID())
                    : ownerUID > 0 && player.getCapability(CapaTeitokuProvider.CAPABILITY)
                            .map(capa -> capa.getPlayerUID() == ownerUID).orElse(false);
            if (owner) {
                name = ChatFormatting.GREEN + player.getName().getString();
                break;
            }
        }
        String text = name + "\n" + coordinates("F", ChatFormatting.LIGHT_PURPLE, from)
                + "\n" + coordinates("T", ChatFormatting.AQUA, to)
                + "\n" + coordinates("C", ChatFormatting.YELLOW, chest);
        CustomTextVisuals.atPosition(level, pos.getX() + 0.5D, pos.getY() + 1.9D, pos.getZ() + 0.5D, text, 4);
    }

    private static String coordinates(String label, ChatFormatting color, BlockPos pos) {
        return ChatFormatting.WHITE + label + ": " + color + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static boolean holdingRouteTool() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        return stack.is(ModItems.WAYPOINT_BLOCK_ITEM.get()) || stack.is(ModItems.TARGET_WRENCH.get())
                || stack.getItem() instanceof PointerItem && PointerItem.getMode(stack) < 3;
    }

    private static void connection(Level level, BlockPos from, BlockPos to, int type) {
        ParticleHelper.spawnAttackParticleAt(level, from.getX() + 0.5D, from.getY() + 0.5D, from.getZ() + 0.5D,
                (to.getX() - from.getX()) * 0.01D, (to.getY() - from.getY()) * 0.01D,
                (to.getZ() - from.getZ()) * 0.01D, type);
    }
}
