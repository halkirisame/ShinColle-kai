package com.lulan.shincolle.ai.domain.movement;

import java.util.EnumMap;
import java.util.Map;

public record MovementDecision(MovementIntent intent, Map<MovementActivity, MovementPermission> permissions) {
    public MovementDecision {
        EnumMap<MovementActivity, MovementPermission> copy = new EnumMap<>(MovementActivity.class);
        for (MovementActivity activity : MovementActivity.values()) {
            MovementPermission permission = permissions.get(activity);
            if (permission == null) {
                throw new IllegalArgumentException("Missing permission for " + activity);
            }
            copy.put(activity, permission);
        }
        permissions = Map.copyOf(copy);
    }

    public MovementPermission get(MovementActivity activity) {
        return this.permissions.get(activity);
    }

    public boolean allows(MovementActivity activity) {
        return get(activity).allowed();
    }
}
