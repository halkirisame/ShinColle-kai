package com.lulan.shincolle.command;

import java.util.List;

import com.lulan.shincolle.ai.TargetShadowComparison;
import com.lulan.shincolle.ai.TargetShadowComparison.Metrics;
import com.lulan.shincolle.ai.TargetShadowComparison.Outcome;
import com.lulan.shincolle.utility.LogHelper;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ShipCmdAiShadow {
    private ShipCmdAiShadow() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shipaishadow")
                .requires(source -> source.hasPermission(2))
                .executes(context -> report(context.getSource()))
                .then(Commands.literal("on")
                        .executes(context -> enable(context.getSource())))
                .then(Commands.literal("off")
                        .executes(context -> disable(context.getSource())))
                .then(Commands.literal("report")
                        .executes(context -> report(context.getSource())))
                .then(Commands.literal("reset")
                        .executes(context -> reset(context.getSource()))));
    }

    static void enable() {
        TargetShadowComparison.setEnabled(true);
    }

    static void disable() {
        TargetShadowComparison.setEnabled(false);
    }

    static void reset() {
        TargetShadowComparison.resetCounters();
    }

    static List<String> reportLines(boolean diagEnabled) {
        Metrics metrics = TargetShadowComparison.metrics();
        String diagStatus = diagEnabled
                ? " (diag=ON)"
                : " (diag=OFF: per-diff DIAG lines are not logged)";
        return List.of(
                "[ShinColle] AI target shadow: "
                        + (TargetShadowComparison.isEnabled() ? "ON" : "OFF") + diagStatus,
                "  comparisons=" + metrics.comparisons() + " errors=" + metrics.errors(),
                "  MATCH=" + TargetShadowComparison.count(Outcome.MATCH)
                        + " DIFF_NON_LIVING=" + TargetShadowComparison.count(Outcome.DIFF_NON_LIVING)
                        + " DIFF_TIE_BREAK=" + TargetShadowComparison.count(Outcome.DIFF_TIE_BREAK)
                        + " UNKNOWN=" + TargetShadowComparison.count(Outcome.UNKNOWN)
                        + " DRAW_NOT_TAKEN=" + TargetShadowComparison.count(Outcome.DRAW_NOT_TAKEN),
                "  spatialQueries=" + metrics.spatialQueries()
                        + " rawCandidates=" + metrics.rawCandidates()
                        + " classifications=" + metrics.classifications()
                        + " relationLookups=" + metrics.relationLookups()
                        + " lineOfSightQueries=" + metrics.lineOfSightQueries()
                        + " selections=" + metrics.selections());
    }

    private static int enable(CommandSourceStack source) {
        enable();
        send(source, reportLines(LogHelper.diagEnabled()).subList(0, 1));
        return 1;
    }

    private static int disable(CommandSourceStack source) {
        disable();
        send(source, reportLines(LogHelper.diagEnabled()).subList(0, 1));
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        reset();
        send(source, reportLines(LogHelper.diagEnabled()).subList(0, 1));
        return 1;
    }

    private static int report(CommandSourceStack source) {
        send(source, reportLines(LogHelper.diagEnabled()));
        return 1;
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), true);
            LogHelper.info(line);
        }
    }
}
