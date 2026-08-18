package com.lulan.shincolle.utility;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;

/**
 * Utility methods for tile entity operations.
 * Ported from 1.10.2 TileEntityHelper.
 */
public class TileEntityHelper {

    /**
     * Pair two waypoints together (from→to link).
     * Sets nextWaypoint on source and lastWaypoint on target (if no cycle).
     *
     * @param player    commanding player
     * @param playerUID player UID for ownership check
     * @param level     server level
     * @param posFrom   source waypoint position
     * @param posTo     target waypoint position
     */
    public static void pairingWaypoints(ServerPlayer player, int playerUID,
                                        Level level, BlockPos posFrom, BlockPos posTo) {
        if (!validatePairingRequest(player, level, posFrom, posTo,
                ConfigHandler.pairingDistWaypoint())) {
            player.sendSystemMessage(Component.literal("[ShinColle] Waypoint pairing failed: invalid access"));
            return;
        }

        BlockEntity beFrom = level.getBlockEntity(posFrom);
        BlockEntity beTo = level.getBlockEntity(posTo);

        if (!(beFrom instanceof TileEntityWaypoint wpFrom) ||
                !(beTo instanceof TileEntityWaypoint wpTo)) {
            player.sendSystemMessage(Component.literal("[ShinColle] Waypoint pairing failed: invalid blocks"));
            return;
        }

        // The UID in the packet is client-controlled; require the server-side
        // player identity as well as the legacy numeric identifier.
        if (!isOwnedBy(player, playerUID, wpFrom) || !isOwnedBy(player, playerUID, wpTo)) {
            player.sendSystemMessage(Component.literal("[ShinColle] Waypoint pairing failed: not your waypoints"));
            return;
        }

        // Set next waypoint on source
        wpFrom.setNextWaypoint(posTo);

        // Set last waypoint on target (unless it would create a 2-node cycle)
        if (!wpTo.getNextWaypoint().equals(posFrom)) {
            wpTo.setLastWaypoint(posFrom);
        }

        player.sendSystemMessage(Component.literal(
                "[ShinColle] Waypoints paired: (" +
                        posFrom.getX() + "," + posFrom.getY() + "," + posFrom.getZ() + ") -> (" +
                        posTo.getX() + "," + posTo.getY() + "," + posTo.getZ() + ")"));
    }

    /**
     * Pair a waypoint with a chest/inventory container.
     *
     * @param player    commanding player
     * @param playerUID player UID for ownership check
     * @param level     server level
     * @param posWp     waypoint position
     * @param posChest  chest/inventory position
     */
    public static void pairingWaypointAndChest(ServerPlayer player, int playerUID,
                                               Level level, BlockPos posWp, BlockPos posChest) {
        if (!validatePairingRequest(player, level, posWp, posChest,
                ConfigHandler.pairingDistChest())) {
            player.sendSystemMessage(Component.literal("[ShinColle] Chest pairing failed: invalid access"));
            return;
        }

        BlockEntity beWp = level.getBlockEntity(posWp);
        BlockEntity beChest = level.getBlockEntity(posChest);

        if (!(beWp instanceof TileEntityWaypoint wp)) {
            player.sendSystemMessage(Component.literal("[ShinColle] Chest pairing failed: invalid waypoint"));
            return;
        }

        // The UUID check prevents a player from forging another owner's UID.
        if (!isOwnedBy(player, playerUID, wp)) {
            player.sendSystemMessage(Component.literal("[ShinColle] Chest pairing failed: not your waypoint"));
            return;
        }

        // Check target is a container
        if (!(beChest instanceof Container)) {
            player.sendSystemMessage(Component.literal("[ShinColle] Chest pairing failed: target is not a container"));
            return;
        }

        wp.setPairedChest(posChest);

        player.sendSystemMessage(Component.literal(
                "[ShinColle] Waypoint-chest paired: WP(" +
                        posWp.getX() + "," + posWp.getY() + "," + posWp.getZ() + ") -> Chest(" +
                        posChest.getX() + "," + posChest.getY() + "," + posChest.getZ() + ")"));
    }

    private static boolean isOwnedBy(ServerPlayer player, int playerUID, TileEntityWaypoint waypoint) {
        return playerUID > 0
                && waypoint.getPlayerUID() == playerUID
                && player.getUUID().equals(waypoint.getOwnerUUID());
    }

    private static boolean validatePairingRequest(ServerPlayer player, Level level,
                                                  BlockPos first, BlockPos second, int maxDistance) {
        if (player.level() != level || first.equals(second)) {
            return false;
        }

        InteractionHand wrenchHand = getTargetWrenchHand(player);
        if (wrenchHand == null) {
            return false;
        }

        double maxDistanceSq = (double) maxDistance * maxDistance;
        if (first.distSqr(second) > maxDistanceSq
                || player.distanceToSqr(Vec3.atCenterOf(first)) > maxDistanceSq
                || player.distanceToSqr(Vec3.atCenterOf(second)) > maxDistanceSq) {
            return false;
        }

        if (level.isOutsideBuildHeight(first) || level.isOutsideBuildHeight(second)
                || !level.getWorldBorder().isWithinBounds(first)
                || !level.getWorldBorder().isWithinBounds(second)
                || !level.hasChunkAt(first) || !level.hasChunkAt(second)
                || !level.mayInteract(player, first) || !level.mayInteract(player, second)) {
            return false;
        }

        return canRightClick(player, wrenchHand, first) && canRightClick(player, wrenchHand, second);
    }

    private static InteractionHand getTargetWrenchHand(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.TARGET_WRENCH.get())) {
            return InteractionHand.MAIN_HAND;
        }
        return player.getOffhandItem().is(ModItems.TARGET_WRENCH.get())
                ? InteractionHand.OFF_HAND : null;
    }

    private static boolean canRightClick(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = ForgeHooks.onRightClickBlock(player, hand, pos, hit);
        return !event.isCanceled() && event.getUseBlock() != Event.Result.DENY;
    }
}
