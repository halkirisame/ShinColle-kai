package com.lulan.shincolle.ai.domain.combat;

/** Per-projectile visual wait; movement, model visibility and combat do not depend on it. */
public final class ProjectileTrailDelay {
    public static final ProjectileTrailDelay NONE = new ProjectileTrailDelay(0);
    public static final ProjectileTrailDelay FOUR_TICKS = new ProjectileTrailDelay(4);

    private final int remainingTicks;

    private ProjectileTrailDelay(int remainingTicks) {
        this.remainingTicks = remainingTicks;
    }

    public static ProjectileTrailDelay fromSnapshot(int remainingTicks) {
        if (remainingTicks < 0 || remainingTicks > 4) throw new IllegalArgumentException("Invalid projectile trail delay");
        return remainingTicks == 0 ? NONE : new ProjectileTrailDelay(remainingTicks);
    }

    public int remainingTicks() { return this.remainingTicks; }

    public boolean allowsEmission() { return this.remainingTicks == 0; }

    /** Called after movement, before considering this update's particle candidates. */
    public ProjectileTrailDelay afterUpdate() {
        return this.remainingTicks == 0 ? NONE : fromSnapshot(this.remainingTicks - 1);
    }
}
