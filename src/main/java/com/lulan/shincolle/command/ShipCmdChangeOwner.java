package com.lulan.shincolle.command;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Command: /shipchangeowner <player>
 * Alias: /shipch <player>
 * <p>
 * Changes the owner of the ship entity the player is currently looking at
 * (mouse-over).
 * Requires OP level 2.
 */
public class ShipCmdChangeOwner {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main command
        dispatcher.register(Commands.literal("shipchangeowner")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))));

        // Alias
        dispatcher.register(Commands.literal("shipch")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer targetPlayer) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("[ShinColle] This command must be run by a player."));
            return 0;
        }

        // Server-side raycast to find target entity
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double range = 32.0;
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        AABB searchArea = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(player, eyePos, endPos, searchArea,
                e -> !e.isSpectator(), range * range);
        Entity target = result != null ? result.getEntity() : null;

        if (!(target instanceof BasicEntityShip ship)) {
            source.sendFailure(Component.literal(
                    "[ShinColle] No ship entity found. Look at a ship entity and try again."));
            return 0;
        }

        if (!ServerDataManager.changeShipOwner(ship, targetPlayer)) {
            source.sendFailure(Component.literal(
                    "[ShinColle] Could not update all owner data for: "
                            + targetPlayer.getName().getString()));
            return 0;
        }

        final String newOwnerName = targetPlayer.getName().getString();
        final int shipUID = ship.getShipUID();
        source.sendSuccess(() -> Component.literal(
                        "[ShinColle] Ship (UID: " + shipUID + ") owner changed to: " + newOwnerName),
                true);

        return 1;
    }
}
