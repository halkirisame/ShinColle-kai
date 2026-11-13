package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeathWaitTimingGameTests {
    private static final Vec3 POSITION = new Vec3(2.5D, 2D, 2.5D);

    private DeathWaitTimingGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_death_wait_zero")
    public static void zeroDeathWaitDropsOneEggOnFirstNaturalDeathTick(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            configure(helper, 0);
            BasicEntityShip ship = killedShip(helper, entities);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.deathTime > 0,
                    "waiting for first natural death tick")).thenExecute(() -> {
                helper.assertTrue(ship.deathTime == 1, "zero wait must finish on the first death tick");
                assertCompleted(helper, entities, ship);
                helper.succeed();
            });
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_death_wait_shortened")
    public static void shorteningDeathWaitCompletesOnNextNaturalDeathTick(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            configure(helper, 10);
            BasicEntityShip ship = killedShip(helper, entities);
            int[] changedAt = {0};
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.deathTime >= 3,
                    "waiting beyond the shorter duration")).thenExecute(() -> {
                helper.assertTrue(!ship.isRemoved() && eggs(helper).isEmpty(), "longer wait must still be active");
                changedAt[0] = ship.deathTime;
                ConfigHandler.COMMON.deathTime.set(2);
                helper.assertTrue(ConfigHandler.deathTime() == 2, "shorter duration must really be configured");
            }).thenWaitUntil(() -> helper.assertTrue(ship.deathTime > changedAt[0],
                    "waiting for next natural death tick after shortening")).thenExecute(() -> {
                helper.assertTrue(ship.deathTime == changedAt[0] + 1, "shortened wait must finish next tick");
                assertCompleted(helper, entities, ship);
                helper.succeed();
            });
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_death_wait_positive")
    public static void positiveDeathWaitStillUsesItsExactBoundary(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            configure(helper, 2);
            BasicEntityShip ship = killedShip(helper, entities);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.deathTime > 0,
                    "waiting for first natural death tick")).thenExecute(() -> {
                helper.assertTrue(ship.deathTime == 1 && !ship.isRemoved() && eggs(helper).isEmpty(),
                        "positive wait must not complete before its boundary");
            }).thenWaitUntil(() -> helper.assertTrue(ship.deathTime >= 2,
                    "waiting for exact configured boundary")).thenExecute(() -> {
                helper.assertTrue(ship.deathTime == 2, "positive wait must complete at its exact boundary");
                assertCompleted(helper, entities, ship);
                helper.succeed();
            });
        }, POSITION);
    }

    private static void configure(GameTestHelper helper, int ticks) {
        int previous = ConfigHandler.deathTime();
        GameTestEntities.onCompletion(helper, () -> ConfigHandler.COMMON.deathTime.set(previous),
                "death duration restoration");
        ConfigHandler.COMMON.deathTime.set(ticks);
        helper.assertTrue(ConfigHandler.deathTime() == ticks, "death duration fixture must be applied");
    }

    private static BasicEntityShip killedShip(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "ship fixture must exist");
        ship.moveTo(helper.absoluteVec(POSITION));
        ship.setNoGravity(true);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.CanDrop, true);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "ship must enter the ticking level");
        helper.assertTrue(ship.hurt(ship.damageSources().magic(), ship.getMaxHealth() * 2F)
                        && ship.isDeadOrDying() && ship.deathTime == 0,
                "real lethal damage must start a fresh death wait without changing its timer");
        return ship;
    }

    private static List<BasicEntityItem> eggs(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                new AABB(BlockPos.containing(helper.absoluteVec(POSITION))).inflate(3D),
                item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
    }

    private static void assertCompleted(GameTestHelper helper, GameTestEntities entities, BasicEntityShip ship) {
        List<BasicEntityItem> eggs = eggs(helper);
        eggs.forEach(entities::add);
        helper.assertTrue(ship.isRemoved() && eggs.size() == 1 && eggs.get(0).getEntityItem().getCount() == 1,
                "completed death wait must remove the corpse and leave exactly one egg; removed="
                        + ship.isRemoved() + " eggs=" + eggs.size());
        helper.assertTrue(!ship.getStateFlag(ID.F.CanDrop), "completed death must consume the drop guard");
    }
}
