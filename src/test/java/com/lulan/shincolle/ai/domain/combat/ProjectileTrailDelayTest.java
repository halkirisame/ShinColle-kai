package com.lulan.shincolle.ai.domain.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileTrailDelayTest {
    @Test
    void projectileTrailDelayUsesDecrementBeforeEmission() {
        ProjectileTrailDelay delay = ProjectileTrailDelay.FOUR_TICKS;
        assertEquals(4, delay.remainingTicks());
        assertFalse(delay.allowsEmission());
        for (int tick = 1; tick <= 8; tick++) {
            delay = delay.afterUpdate();
            assertEquals(Math.max(4 - tick, 0), delay.remainingTicks());
            assertEquals(tick >= 4, delay.allowsEmission());
        }
        assertSame(ProjectileTrailDelay.NONE, delay);
        assertSame(delay, delay.afterUpdate());
    }

    @Test
    void snapshotsKeepRemainingDelayWithoutStartingAgain() {
        for (int remaining = 0; remaining <= 4; remaining++) {
            ProjectileTrailDelay snapshot = ProjectileTrailDelay.fromSnapshot(remaining);
            assertEquals(remaining, snapshot.remainingTicks());
            assertEquals(remaining == 0, snapshot.allowsEmission());
            assertEquals(Math.max(remaining - 1, 0), snapshot.afterUpdate().remainingTicks());
        }
        assertTrue(ProjectileTrailDelay.NONE.allowsEmission());
        assertThrows(IllegalArgumentException.class, () -> ProjectileTrailDelay.fromSnapshot(-1));
        assertThrows(IllegalArgumentException.class, () -> ProjectileTrailDelay.fromSnapshot(5));
    }
}
