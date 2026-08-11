package com.lulan.shincolle.command;

import java.util.Comparator;
import java.util.stream.Collectors;

import com.lulan.shincolle.entity.IShipEmotion;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Command: /shipai
 * <p>
 * Reports why the nearest ship is or is not moving: which goals hold
 * the movement slot, whether navigation produced a path, and what forward input
 * the move control is applying. "Sits still but still shoots" and "walks into
 * the enemy" look identical from the outside; these numbers tell them apart.
 * <p>
 * Available to all players (permission level 0).
 */
public class ShipCmdShipAI {

    private static final double RANGE = 32.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shipai")
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("[ShinColle] This command must be run by a player."));
            return 0;
        }

        // Pick the ship to report on by proximity rather than by a precise ray:
        // a diagnostic is useless if it depends on aiming well at a moving mob.
        // Among nearby ships, prefer whichever the player is closest to facing.
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        AABB searchArea = player.getBoundingBox().inflate(RANGE);

        Mob mob = player.level().getEntitiesOfClass(Mob.class, searchArea,
                        e -> e.isAlive() && e instanceof IShipEmotion).stream()
                .max(Comparator.comparingDouble(e -> {
                    Vec3 toEntity = e.position().add(0, e.getBbHeight() * 0.5D, 0).subtract(eyePos);
                    double len = toEntity.length();
                    double facing = len < 1.0E-4D ? 1.0D : toEntity.scale(1D / len).dot(lookVec);
                    // facing dominates, distance only breaks ties between similar angles
                    return facing - len / (RANGE * 20D);
                }))
                .orElse(null);

        if (mob == null) {
            source.sendFailure(Component.literal(
                    "[ShinColle] No ship within " + (int) RANGE + " blocks."));
            return 0;
        }

        PathNavigation nav = mob.getNavigation();
        Path path = nav.getPath();
        LivingEntity mobTarget = mob.getTarget();

        String goals = mob.goalSelector.getRunningGoals()
                .map(WrappedGoal::getGoal)
                .map(g -> g.getClass().getSimpleName())
                .collect(Collectors.joining(", "));
        if (goals.isEmpty()) {
            goals = "(none)";
        }

        String targets = mob.targetSelector.getRunningGoals()
                .map(WrappedGoal::getGoal)
                .map(g -> g.getClass().getSimpleName())
                .collect(Collectors.joining(", "));
        if (targets.isEmpty()) {
            targets = "(none)";
        }

        final String runningGoals = goals;
        final String runningTargets = targets;
        final String pathInfo = path == null
                ? "none"
                : (path.getNextNodeIndex() + "/" + path.getNodeCount()
                        + (path.isDone() ? " done" : " active"));

        source.sendSuccess(() -> Component.literal("[ShinColle] === Ship AI ==="), false);
        source.sendSuccess(() -> Component.literal(
                "  Entity: " + mob.getClass().getSimpleName()
                        + String.format(" (%.1fm)", mob.distanceTo(player))
                        + " | Target: " + (mobTarget == null ? "none" : mobTarget.getName().getString())), false);
        source.sendSuccess(() -> Component.literal(
                "  Goals: " + runningGoals), false);
        source.sendSuccess(() -> Component.literal(
                "  Target goals: " + runningTargets), false);
        source.sendSuccess(() -> Component.literal(
                "  Navigation: " + nav.getClass().getSimpleName()
                        + " | path " + pathInfo
                        + " | idle=" + nav.isDone()), false);
        source.sendSuccess(() -> Component.literal(
                "  Move: wanted=" + mob.getMoveControl().hasWanted()
                        + " | forward=" + String.format("%.2f", mob.zza)
                        + " | speed=" + String.format("%.3f", mob.getSpeed())), false);
        // Where the path wants the mob to go next, and what the move control was
        // actually told. A node the mob can never "reach" (vanilla needs the
        // height difference under one block) leaves the path stuck on node 0.
        if (path != null && !path.isDone()) {
            net.minecraft.core.Vec3i node = path.getNextNodePos();
            source.sendSuccess(() -> Component.literal(
                    "  Node: " + node.getX() + ", " + node.getY() + ", " + node.getZ()
                            + String.format(" | d=%.2f, %.2f, %.2f",
                                    node.getX() + 0.5D - mob.getX(),
                                    node.getY() - mob.getY(),
                                    node.getZ() + 0.5D - mob.getZ())), false);
        }
        source.sendSuccess(() -> Component.literal(
                "  Wanted: " + String.format("%.1f, %.1f, %.1f",
                                mob.getMoveControl().getWantedX(),
                                mob.getMoveControl().getWantedY(),
                                mob.getMoveControl().getWantedZ())
                        + String.format(" | spdMod=%.2f", mob.getMoveControl().getSpeedModifier())
                        + " | noAi=" + mob.isNoAi()), false);
        source.sendSuccess(() -> Component.literal(
                "  State: inWater=" + mob.isInWater()
                        + " | onGround=" + mob.onGround()
                        + " | pos " + String.format("%.1f, %.1f, %.1f",
                                mob.getX(), mob.getY(), mob.getZ())), false);
        return 1;
    }
}
