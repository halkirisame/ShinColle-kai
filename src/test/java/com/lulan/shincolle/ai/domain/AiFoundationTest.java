package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiFoundationTest {

    @Test
    void decisionIdentityRejectsNegativeComponents() {
        assertThrows(IllegalArgumentException.class, () -> new AiDecisionId(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new AiDecisionId(0, -1));
        assertEquals(new AiDecisionId(12, 3), new AiDecisionId(12, 3));
    }

    @Test
    void tickContextUsesDecisionIdentityAsTheSameTimeAuthority() {
        AiDecisionId decisionId = new AiDecisionId(42, 0);

        assertEquals(decisionId, new AiTickContext(42, decisionId).decisionId());
        assertThrows(IllegalArgumentException.class,
                () -> new AiTickContext(41, decisionId));
        assertThrows(NullPointerException.class, () -> new AiTickContext(42, null));
    }

    @Test
    void randomnessCompatibilityHasOnlyTheNormativeModes() {
        assertEquals(
                "NONE,VALUE_DISTRIBUTION,SEQUENCE",
                String.join(",", java.util.Arrays.stream(RandomnessCompatibility.values())
                        .map(Enum::name)
                        .toList()));
    }

    @Test
    void debugReasonDefensivelyCopiesAndOrdersAttributes() {
        Map<String, String> source = new HashMap<>();
        source.put("target_tier", "air");
        source.put("candidate_count", "3");

        AiDebugReason reason = new AiDebugReason("target_selected", source);
        source.put("target_tier", "submarine");

        assertEquals("air", reason.attributes().get("target_tier"));
        assertEquals("candidate_count", reason.attributes().keySet().iterator().next());
        assertThrows(UnsupportedOperationException.class,
                () -> reason.attributes().put("source", "automatic"));
    }

    @Test
    void debugReasonRejectsInvalidNamesAndNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> AiDebugReason.of("TargetSelected"));
        assertThrows(IllegalArgumentException.class,
                () -> new AiDebugReason("target_selected", Map.of("Target", "enemy")));
        assertThrows(NullPointerException.class,
                () -> new AiDebugReason("target_selected", null));

        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "enemy");
        assertThrows(NullPointerException.class,
                () -> new AiDebugReason("target_selected", nullKey));

        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("target", null);
        assertThrows(NullPointerException.class,
                () -> new AiDebugReason("target_selected", nullValue));
    }
}
