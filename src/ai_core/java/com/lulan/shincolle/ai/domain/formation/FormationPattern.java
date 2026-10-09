package com.lulan.shincolle.ai.domain.formation;

import java.util.Optional;

public enum FormationPattern {
    LINE_AHEAD(1), DOUBLE_LINE(2), DIAMOND(3), ECHELON(4), LINE_ABREAST(5);
    private final int legacyId;
    FormationPattern(int legacyId) { this.legacyId = legacyId; }
    public int legacyId() { return this.legacyId; }
    public boolean sequential() { return this == LINE_AHEAD || this == ECHELON; }
    public static Optional<FormationPattern> fromLegacy(int raw) {
        for (FormationPattern pattern : values()) if (pattern.legacyId == raw) return Optional.of(pattern);
        return Optional.empty();
    }
}
