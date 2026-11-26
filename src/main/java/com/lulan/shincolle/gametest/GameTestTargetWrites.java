package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;

/**
 * The only place a GameTest writes a ship's target with setEntityTarget. A ship with target
 * authority ignores a non-null write, and the AI rebuild at tick 16 clears one made before it,
 * so a fixture that needs a target locks it with a manual attack command instead.
 */
final class GameTestTargetWrites {

    private GameTestTargetWrites() {
    }

    /** For a test that finishes before the ship's first AI rebuild, which clears this target. */
    static void beforeAuthority(GameTestHelper helper, BasicEntityShip ship, Entity target) {
        helper.assertTrue(!ship.hasTargetAuthority(),
                "The ship already has target authority, which ignores this write; "
                        + "lock the target with a manual attack command");
        ship.setEntityTarget(target);
    }

    /** For a test of the authority ignoring a write from outside it; the test asserts the lock kept. */
    static void ignoredByAuthority(GameTestHelper helper, BasicEntityShip ship, IShipAttackBase writer,
                                   Entity target) {
        helper.assertTrue(ship.hasTargetAuthority(),
                "The ship has no target authority yet, so this write would set its target");
        writer.setEntityTarget(target);
    }
}
