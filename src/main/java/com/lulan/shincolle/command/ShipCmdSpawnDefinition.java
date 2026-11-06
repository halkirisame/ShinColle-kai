package com.lulan.shincolle.command;

import com.lulan.shincolle.shipdata.GenericShipSpawner;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.shipdata.ShipDefinitionRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ShipCmdSpawnDefinition {
    private ShipCmdSpawnDefinition() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shincolle")
                .then(Commands.literal("ship").then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("ship_id", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        ShipDefinitionRegistry.server().all().keySet().stream().map(ShipDefinitionId::location), builder))
                                .executes(context -> spawn(context.getSource(), new ShipDefinitionId(
                                        ResourceLocationArgument.getId(context, "ship_id")), false))
                                .then(Commands.literal("hostile").executes(context -> spawn(context.getSource(),
                                        new ShipDefinitionId(ResourceLocationArgument.getId(context, "ship_id")), true))
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .executes(context -> spawnVariant(context.getSource(),
                                                        new ShipDefinitionId(ResourceLocationArgument.getId(context, "ship_id")),
                                                        StringArgumentType.getString(context, "variant")))))))));
    }

    private static int spawnVariant(CommandSourceStack source, ShipDefinitionId id, String variantId)
            throws CommandSyntaxException {
        var definition = ShipDefinitionRegistry.server().get(id);
        var variant = definition == null ? null : definition.hostileVariants().get(variantId);
        if (variant == null) {
            throw new DynamicCommandExceptionType(value -> Component.literal("Unknown hostile variant: " + value))
                    .create(id + "/" + variantId);
        }
        var ship = (com.lulan.shincolle.entity.GenericHostileShipEntity)
                GenericShipSpawner.create(source.getLevel(), id, true, null);
        if (ship == null) return 0;
        ship.selectVariant(variant);
        return add(source, id, ship);
    }

    private static int spawn(CommandSourceStack source, ShipDefinitionId id, boolean hostile) {
        Mob ship = GenericShipSpawner.create(source.getLevel(), id, hostile,
                source.getEntity() instanceof Player player ? player : null);
        if (ship == null) {
            source.sendFailure(Component.translatable("command.shincolle_kai.ship.missing", id.toString()));
            return 0;
        }
        return add(source, id, ship);
    }

    private static int add(CommandSourceStack source, ShipDefinitionId id, Mob ship) {
        Vec3 position = source.getPosition();
        ship.moveTo(position.x, position.y, position.z, source.getRotation().y, 0);
        if (!source.getLevel().addFreshEntity(ship)) return 0;
        source.sendSuccess(() -> Component.translatable("command.shincolle_kai.ship.spawned", id.toString()), true);
        return 1;
    }
}
