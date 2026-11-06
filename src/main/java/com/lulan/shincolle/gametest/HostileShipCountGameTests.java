package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The hostile fleet spawn cap counts loaded hostile ships. The count walks the loaded entities
 * instead of querying a world-sized box, and must still match what that box query returned,
 * including leaving out ships above or below the build height.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileShipCountGameTests {

    private static final double LIMIT = 30000000D;

    private HostileShipCountGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileCountMatchesWorldBoxQueryAcrossBuildHeight(GameTestHelper helper) {
        Vec3 relative = new Vec3(1.5D, 2.0D, 1.5D);
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ServerLevel level = helper.getLevel();
            Vec3 base = helper.absoluteVec(relative);
            BasicEntityShipHostile inside = spawn(helper, entities, base.x, base.y);
            double height = inside.getBbHeight();
            double minY = level.getMinBuildHeight();
            BasicEntityShipHostile above = spawn(helper, entities, base.x, level.getMaxBuildHeight() + 80.0D);
            BasicEntityShipHostile crossing = spawn(helper, entities, base.x, minY - height * 0.5D);
            BasicEntityShipHostile below = spawn(helper, entities, base.x, minY - height - 2.0D);

            AABB worldBox = new AABB(-LIMIT, minY, -LIMIT, LIMIT, level.getMaxBuildHeight(), LIMIT);
            List<BasicEntityShipHostile> queried = level.getEntitiesOfClass(BasicEntityShipHostile.class, worldBox);
            check(helper, level, queried, inside, "inside", true);
            check(helper, level, queried, above, "above the build height", false);
            check(helper, level, queried, crossing, "crossing the build floor", true);
            check(helper, level, queried, below, "below the build floor", false);
            helper.succeed();
        }, relative);
    }

    private static BasicEntityShipHostile spawn(GameTestHelper helper, GameTestEntities entities, double x, double y) {
        Entity created = ModEntities.BB_KONGOU_MOB.get().create(helper.getLevel());
        helper.assertTrue(created instanceof BasicEntityShipHostile, "Failed to create hostile ship");
        BasicEntityShipHostile ship = entities.add((BasicEntityShipHostile) created);
        ship.setNoAi(true);
        ship.setNoGravity(true);
        ship.moveTo(x, y, helper.absoluteVec(new Vec3(1.5D, 0.0D, 1.5D)).z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Hostile ship was not added at y=" + y);
        GameTestEntities.assertRegistered(helper, ship);
        return ship;
    }

    private static void check(GameTestHelper helper, ServerLevel level, List<BasicEntityShipHostile> queried,
            BasicEntityShipHostile ship, String where, boolean expected) {
        boolean byQuery = queried.contains(ship);
        boolean byCount = EntityHelper.isInHostileCountBox(level, ship);
        helper.assertTrue(byQuery == expected,
                "World box query for the ship " + where + " was " + byQuery + " at " + ship.getBoundingBox());
        helper.assertTrue(byCount == byQuery,
                "Hostile count for the ship " + where + " was " + byCount + ", query was " + byQuery);
    }
}
