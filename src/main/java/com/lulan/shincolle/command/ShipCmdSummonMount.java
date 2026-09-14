package com.lulan.shincolle.command;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Operator command that enables and immediately summons a looked-at ship's mount. */
public final class ShipCmdSummonMount {
    private static final double RANGE = 32.0D;

    private ShipCmdSummonMount() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shipsummonmount")
                .requires(source -> source.hasPermission(2))
                .executes(context -> execute(context.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("[ShinColle] This command must be run by a player."));
            return 0;
        }

        BasicEntityShip ship = findLookedAtShip(player);
        if (ship == null) {
            source.sendFailure(Component.literal(
                    "[ShinColle] No friendly ship found. Look at a ship within 32 blocks and try again."));
            return 0;
        }

        SummonResult result = summon(ship);
        if (result == SummonResult.SUMMONED) {
            source.sendSuccess(() -> Component.literal("[ShinColle] Summoned the ship mount."), false);
            return 1;
        }
        if (result == SummonResult.ALREADY_PRESENT) {
            source.sendSuccess(() -> Component.literal("[ShinColle] The ship already has its mount."), false);
            return 1;
        }

        source.sendFailure(Component.literal(result.failureMessage));
        return 0;
    }

    private static BasicEntityShip findLookedAtShip(ServerPlayer player) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 lookVector = player.getLookAngle();
        Vec3 endPosition = eyePosition.add(lookVector.scale(RANGE));
        AABB searchArea = player.getBoundingBox().expandTowards(lookVector.scale(RANGE)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eyePosition, endPosition, searchArea,
                entity -> !entity.isSpectator(), RANGE * RANGE);
        if (hit != null && hit.getEntity() instanceof BasicEntityShip ship) {
            return ship;
        }
        return null;
    }

    static SummonResult summon(BasicEntityShip ship) {
        if (!ship.isAlive()) {
            return SummonResult.NOT_ALIVE;
        }
        if (!ship.hasShipMounts()) {
            return SummonResult.NOT_SUPPORTED;
        }
        if (ship.getStateFlag(ID.F.NoFuel)) {
            return SummonResult.NO_FUEL;
        }

        int originalState = ship.getStateEmotion(ID.S.State);
        if (ship.isPassenger()) {
            if (ship.getVehicle() instanceof BasicEntityMount mount
                    && mount.isAlive() && mount.getHost() == ship) {
                enableMountState(ship, originalState);
                return SummonResult.ALREADY_PRESENT;
            }
            return SummonResult.RIDING_OTHER;
        }

        if (!(ship.level() instanceof ServerLevel serverLevel)) {
            return SummonResult.SUMMON_FAILED;
        }
        enableMountState(ship, originalState);
        ship.updateMountSummon();
        if (ship.getVehicle() instanceof BasicEntityMount mount
                && mount.isAlive() && mount.getHost() == ship) {
            return SummonResult.SUMMONED;
        }

        ship.setStateEmotion(ID.S.State, originalState, true);
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof BasicEntityMount mount && mount.isAlive() && mount.getHost() == ship) {
                return SummonResult.ORPHAN_PRESENT;
            }
        }
        return SummonResult.SUMMON_FAILED;
    }

    private static void enableMountState(BasicEntityShip ship, int originalState) {
        int enabledState = originalState | 1;
        if (enabledState != originalState) {
            ship.setStateEmotion(ID.S.State, enabledState, true);
        }
    }

    enum SummonResult {
        SUMMONED(""),
        ALREADY_PRESENT(""),
        NOT_ALIVE("[ShinColle] The targeted ship is not alive."),
        NOT_SUPPORTED("[ShinColle] The targeted ship does not support a mount."),
        NO_FUEL("[ShinColle] The targeted ship is out of fuel."),
        RIDING_OTHER("[ShinColle] The targeted ship is already riding another entity."),
        ORPHAN_PRESENT("[ShinColle] A previous mount is being removed. Wait a moment and try again."),
        SUMMON_FAILED("[ShinColle] The mount could not be summoned.");

        private final String failureMessage;

        SummonResult(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }
}
