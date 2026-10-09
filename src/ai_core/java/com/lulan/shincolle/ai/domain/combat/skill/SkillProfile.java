package com.lulan.shincolle.ai.domain.combat.skill;

import java.util.Objects;

/** The ship's immutable inputs for a sequence and its damage multipliers. */
public record SkillProfile(SkillKind kind, boolean hostile, int level, int scale) {
    public SkillProfile {
        Objects.requireNonNull(kind, "kind");
        if (level < 0 || scale < 0) throw new IllegalArgumentException("negative skill profile");
    }

    public int attacks() {
        return switch (kind) {
            case TENRYUU -> hostile ? 2 + scale * 2 : 3 + (int) (level * 0.03F);
            case TATSUTA -> hostile ? 1 + scale : 2 + (int) (level * 0.015F);
            case NAGATO -> 1;
        };
    }

    public float meleeMultiplier() {
        return kind == SkillKind.NAGATO ? 3F : 2F;
    }

    public float horizontalMultiplier() {
        return kind == SkillKind.TATSUTA ? 0.5F : hostile ? 0.4F : 0.3F;
    }

    public float finalMultiplier() {
        return switch (kind) {
            case TENRYUU -> hostile ? 1F : 1.2F;
            case TATSUTA -> 1.5F;
            case NAGATO -> 4F;
        };
    }
}
