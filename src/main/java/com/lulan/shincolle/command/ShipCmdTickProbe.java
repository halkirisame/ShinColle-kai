package com.lulan.shincolle.command;

import java.util.List;

import com.lulan.shincolle.ai.diag.TickStallProbe;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Operator switch for the slow-tick diagnostics. Never saved: a server restart always starts with it off. */
public final class ShipCmdTickProbe {
    private ShipCmdTickProbe() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shiptickprobe")
                .requires(source -> source.hasPermission(2))
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("on")
                        .executes(context -> enable(context.getSource(), TickStallProbe.DEFAULT_THRESHOLD_MS))
                        .then(Commands.argument("thresholdMs", IntegerArgumentType.integer(1))
                                .executes(context -> enable(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "thresholdMs")))))
                .then(Commands.literal("off")
                        .executes(context -> disable(context.getSource())))
                .then(Commands.literal("report")
                        .executes(context -> report(context.getSource())))
                .then(Commands.literal("reset")
                        .executes(context -> reset(context.getSource()))));
    }

    static String statusLine() {
        return "[TickProbe] " + (TickStallProbe.isEnabled() ? "ON" : "OFF")
                + " thresholdMs=" + TickStallProbe.thresholdMs();
    }

    private static int enable(CommandSourceStack source, int thresholdMs) {
        TickStallProbe.enable(thresholdMs);
        send(source, List.of(statusLine()));
        return 1;
    }

    private static int disable(CommandSourceStack source) {
        TickStallProbe.disable();
        send(source, List.of(statusLine()));
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        TickStallProbe.reset();
        send(source, List.of("[TickProbe] statistics cleared. " + statusLine().substring("[TickProbe] ".length())));
        return 1;
    }

    private static int report(CommandSourceStack source) {
        send(source, TickStallProbe.reportLines());
        return 1;
    }

    private static int status(CommandSourceStack source) {
        send(source, List.of(statusLine()));
        return 1;
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        TickStallProbe.log(lines);
    }
}
