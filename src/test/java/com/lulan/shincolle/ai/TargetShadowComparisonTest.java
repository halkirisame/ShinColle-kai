package com.lulan.shincolle.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TargetShadowComparisonTest {
    @Test
    void compatibilityRequestsNeverEnableComparisonOrProduceResults() {
        try {
            for (boolean requested : new boolean[]{false, true}) {
                TargetShadowComparison.setEnabled(requested);
                assertFalse(TargetShadowComparison.isEnabled(), "The retired comparison must stay disabled");
                TargetShadowComparison.compare(null, null, 16, 0, true, null);
                assertFalse(TargetShadowComparison.isEnabled());
                assertEquals(new TargetShadowComparison.Metrics(0, 0, 0, 0, 0, 0, 0, 0),
                        TargetShadowComparison.metrics());
                for (TargetShadowComparison.Outcome outcome : TargetShadowComparison.Outcome.values()) {
                    assertEquals(0L, TargetShadowComparison.count(outcome), "No fabricated " + outcome);
                }
                TargetShadowComparison.resetCounters();
                assertFalse(TargetShadowComparison.isEnabled());
            }
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }
}
