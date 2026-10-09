package com.lulan.shincolle.command;

import java.util.List;

import com.lulan.shincolle.ai.TargetShadowComparison;
import com.lulan.shincolle.ai.TargetShadowComparison.Outcome;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCmdAiShadowGameTests {
    private ShipCmdAiShadowGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void onAndOffChangeEnabledState(GameTestHelper helper) {
        try {
            ShipCmdAiShadow.enable();
            helper.assertTrue(TargetShadowComparison.isEnabled(), "on must enable target shadow comparison");

            ShipCmdAiShadow.disable();
            helper.assertTrue(!TargetShadowComparison.isEnabled(), "off must disable target shadow comparison");
            helper.succeed();
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resetClearsAllCounters(GameTestHelper helper) {
        try {
            TargetShadowComparison.setEnabled(true);
            TargetShadowComparison.compare(null, null, 0, 0, false, null);
            helper.assertTrue(TargetShadowComparison.metrics().comparisons() > 0L,
                    "fixture must increment comparisons before reset");

            ShipCmdAiShadow.reset();

            for (Outcome outcome : Outcome.values()) {
                helper.assertTrue(TargetShadowComparison.count(outcome) == 0L,
                        outcome + " count must be zero after reset");
            }
            helper.assertTrue(TargetShadowComparison.metrics().comparisons() == 0L,
                    "comparisons must be zero after reset");
            helper.succeed();
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resetPreservesEnabledState(GameTestHelper helper) {
        try {
            TargetShadowComparison.setEnabled(true);

            ShipCmdAiShadow.reset();

            helper.assertTrue(TargetShadowComparison.isEnabled(), "reset must preserve the enabled state");
            helper.succeed();
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptyReportHasFourCompleteLines(GameTestHelper helper) {
        try {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();

            List<String> diagOn = ShipCmdAiShadow.reportLines(true);
            List<String> diagOff = ShipCmdAiShadow.reportLines(false);

            helper.assertTrue(diagOn.size() == 4, "diag-on report must have four lines");
            helper.assertTrue(diagOff.size() == 4, "diag-off report must have four lines");
            helper.assertTrue(diagOn.get(0).equals("[ShinColle] AI target shadow: OFF (diag=ON)"),
                    "diag-on status line");
            helper.assertTrue(diagOff.get(0).equals("[ShinColle] AI target shadow: OFF "
                    + "(diag=OFF: per-diff DIAG lines are not logged)"), "diag-off status line");
            helper.assertTrue(diagOn.get(1).equals("  comparisons=0 errors=0"), "empty comparison line");
            helper.assertTrue(diagOn.get(2).equals("  MATCH=0 DIFF_NON_LIVING=0 DIFF_TIE_BREAK=0 "
                    + "UNKNOWN=0 DRAW_NOT_TAKEN=0"), "empty outcome line");
            helper.assertTrue(diagOn.get(3).equals("  spatialQueries=0 rawCandidates=0 classifications=0 "
                    + "relationLookups=0 lineOfSightQueries=0 selections=0"), "empty metrics line");
            helper.succeed();
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }
}
