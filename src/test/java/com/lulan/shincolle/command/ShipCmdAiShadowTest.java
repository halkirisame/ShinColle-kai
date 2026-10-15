package com.lulan.shincolle.command;

import com.lulan.shincolle.ai.TargetShadowComparison;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipCmdAiShadowTest {
    @Test
    void oldCommandsAlwaysReportComparisonNotPerformed() {
        try {
            ShipCmdAiShadow.enable();
            for (boolean diag : new boolean[]{false, true}) {
                var lines = ShipCmdAiShadow.reportLines(diag);
                assertEquals(4, lines.size());
                assertTrue(lines.get(0).contains("DISABLED (comparison not performed; NEW authority only)"));
                assertEquals("  comparisons=0 errors=0", lines.get(1));
            }
            ShipCmdAiShadow.reset();
            ShipCmdAiShadow.disable();
            assertTrue(ShipCmdAiShadow.reportLines(false).get(0).contains("comparison not performed"));
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }
}
