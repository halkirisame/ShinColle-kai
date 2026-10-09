package com.lulan.shincolle.ai.domain.action;

import java.util.Set;

public record ActionPermission(boolean allowed, Set<ActionInhibitReason> reasons) {
    public static final ActionPermission ALLOWED = new ActionPermission(true, Set.of());

    public ActionPermission {
        reasons = Set.copyOf(reasons);
        if (allowed != reasons.isEmpty()) {
            throw new IllegalArgumentException("A permission is allowed exactly when it has no reasons");
        }
    }

    public static ActionPermission inhibited(Set<ActionInhibitReason> reasons) {
        return reasons.isEmpty() ? ALLOWED : new ActionPermission(false, reasons);
    }
}
