package com.lulan.shincolle.ai.domain.movement;

import java.util.Set;

public record MovementPermission(boolean allowed, Set<MovementInhibitReason> reasons) {
    public static final MovementPermission ALLOWED = new MovementPermission(true, Set.of());

    public MovementPermission {
        reasons = Set.copyOf(reasons);
        if (allowed != reasons.isEmpty()) {
            throw new IllegalArgumentException("A permission is allowed exactly when it has no reasons");
        }
    }

    public static MovementPermission inhibited(Set<MovementInhibitReason> reasons) {
        return reasons.isEmpty() ? ALLOWED : new MovementPermission(false, reasons);
    }
}
