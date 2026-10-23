package com.lulan.shincolle.ai.domain.combat;

import java.util.Objects;

/** Built-in cluster projectile rules, independent of entities and world coordinates. */
public final class ClusterProjectileRules {
    private ClusterProjectileRules() { }

    public record LaunchCombatSnapshot(float attack, float knockback) { }

    /** A displacement, never a target position or a normalized direction. */
    public record RawVelocity(double x, double y, double z) {
        public RawVelocity afterChildUpdate(double acceleration) {
            return new RawVelocity(x * .95D, y + acceleration, z * .95D);
        }
    }

    public record ChildLaunch(LaunchCombatSnapshot combat, RawVelocity velocity, int lifetime, double speed,
                              double acceleration1, double acceleration2, ProjectileTrailDelay trailDelay) { }

    public static boolean emitsChildAtAge(int age) {
        return age >= 8 && age <= 40 && (age & 7) == 0;
    }

    public static ChildLaunch child(LaunchCombatSnapshot launch, RawVelocity displacement) {
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(displacement, "displacement");
        return new ChildLaunch(new LaunchCombatSnapshot(launch.attack() * .5F, launch.knockback()),
                displacement, 140, .5D, -.06D, -.06D, ProjectileTrailDelay.FOUR_TICKS);
    }

    public static double childVerticalOffset(RawVelocity displacement) {
        return -.65D - Math.abs(displacement.y());
    }
}
