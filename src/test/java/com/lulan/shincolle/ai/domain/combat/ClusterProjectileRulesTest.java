package com.lulan.shincolle.ai.domain.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterProjectileRulesTest {
    @Test
    void clusterEmissionAgesAreEightThroughForty() {
        List<Integer> ages = new ArrayList<>();
        for (int age = -1; age <= 160; age++) {
            if (ClusterProjectileRules.emitsChildAtAge(age)) ages.add(age);
        }
        assertEquals(List.of(8, 16, 24, 32, 40), ages);
        // A skipped age does not produce catch-up emissions.
        assertEquals(false, ClusterProjectileRules.emitsChildAtAge(17));
    }

    @Test
    void clusterChildProfileKeepsLaunchDamageAndRawMotion() {
        var launch = new ClusterProjectileRules.LaunchCombatSnapshot(40F, .15F);
        var motion = new ClusterProjectileRules.RawVelocity(.8D, .3D, -.4D);
        var child = ClusterProjectileRules.child(launch, motion);
        assertEquals(20F, child.combat().attack());
        assertEquals(.15F, child.combat().knockback());
        assertEquals(40F, launch.attack());
        assertEquals(motion, child.velocity());
        assertEquals(140, child.lifetime());
        assertEquals(.5D, child.speed());
        assertEquals(-.06D, child.acceleration1());
        assertEquals(-.06D, child.acceleration2());
        assertEquals(ProjectileTrailDelay.FOUR_TICKS, child.trailDelay());
        assertEquals(-.95D, ClusterProjectileRules.childVerticalOffset(motion), 1E-12);
        var next = motion.afterChildUpdate(child.acceleration1());
        assertEquals(.76D, next.x(), 1E-12);
        assertEquals(.24D, next.y(), 1E-12);
        assertEquals(-.38D, next.z(), 1E-12);
    }

    @Test
    void customVelocityIsTranslationInvariant() {
        var launch = new ClusterProjectileRules.LaunchCombatSnapshot(40F, .15F);
        var velocity = new ClusterProjectileRules.RawVelocity(.8D, .3D, -.4D);
        var child = ClusterProjectileRules.child(launch, velocity);
        // Raw initialization consumes displacement, never an origin-to-target direction.
        assertEquals(velocity, child.velocity());
        assertEquals(new ClusterProjectileRules.RawVelocity(0D, -.06D, 0D),
                new ClusterProjectileRules.RawVelocity(0D, 0D, 0D).afterChildUpdate(-.06D));
    }
}
