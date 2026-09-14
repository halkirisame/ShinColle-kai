package com.lulan.shincolle.command;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipCmdSummonMountGameTests {
    private ShipCmdSummonMountGameTests() {
    }

    private static BasicEntityShip supportedShip(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = ModEntities.CV_WD.get().create(helper.getLevel());
        helper.assertTrue(ship != null, "supported ship fixture");
        entities.add(ship);
        ship.setPos(helper.absoluteVec(new Vec3(1.0D, 2.0D, 1.0D)));
        ship.setNoGravity(true);
        ship.setNoAi(true);
        ship.setStateMinor(ID.M.NumGrudge, 10000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.calcShipAttributes(31, false);
        helper.getLevel().addFreshEntity(ship);
        return ship;
    }

    private static long mountCount(GameTestHelper helper, BasicEntityShip ship) {
        long count = 0L;
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof BasicEntityMount mount && mount.isAlive() && mount.getHost() == ship) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void commandEnablesStateAndSummonsImmediately(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = supportedShip(helper, entities);
            ship.setStateEmotion(ID.S.State, 6, false);

            ShipCmdSummonMount.SummonResult result = ShipCmdSummonMount.summon(ship);

            helper.assertTrue(result == ShipCmdSummonMount.SummonResult.SUMMONED, "summon result");
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 7, "must preserve other state bits");
            helper.assertTrue(ship.getVehicle() instanceof BasicEntityMount, "must ride mount immediately");
            entities.add(ship.getVehicle());
            helper.assertTrue(mountCount(helper, ship) == 1L, "must create exactly one mount");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void repeatedCommandReusesExistingMount(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = supportedShip(helper, entities);
            ShipCmdSummonMount.SummonResult first = ShipCmdSummonMount.summon(ship);
            Entity firstMount = entities.add(ship.getVehicle());

            ShipCmdSummonMount.SummonResult second = ShipCmdSummonMount.summon(ship);

            helper.assertTrue(first == ShipCmdSummonMount.SummonResult.SUMMONED, "first summon result");
            helper.assertTrue(second == ShipCmdSummonMount.SummonResult.ALREADY_PRESENT,
                    "repeat must be idempotent");
            helper.assertTrue(ship.getVehicle() == firstMount, "repeat must keep the original mount");
            helper.assertTrue(mountCount(helper, ship) == 1L, "repeat must not create a duplicate");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void noFuelRejectsWithoutChangingState(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = supportedShip(helper, entities);
            ship.setStateEmotion(ID.S.State, 6, false);
            ship.setStateFlag(ID.F.NoFuel, true);

            ShipCmdSummonMount.SummonResult result = ShipCmdSummonMount.summon(ship);

            helper.assertTrue(result == ShipCmdSummonMount.SummonResult.NO_FUEL, "no-fuel result");
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 6, "failure must preserve state");
            helper.assertTrue(!ship.isPassenger() && mountCount(helper, ship) == 0L,
                    "failure must not create a mount");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unsupportedShipRejectsWithoutChangingState(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ModEntities.DESTROYER_I.get().create(helper.getLevel());
            helper.assertTrue(ship != null && !ship.hasShipMounts(), "unsupported ship fixture");
            entities.add(ship);
            ship.setPos(helper.absoluteVec(new Vec3(1.0D, 2.0D, 1.0D)));
            ship.setNoGravity(true);
            ship.setNoAi(true);
            ship.setStateEmotion(ID.S.State, 6, false);
            ship.setStateFlag(ID.F.NoFuel, false);
            helper.getLevel().addFreshEntity(ship);

            ShipCmdSummonMount.SummonResult result = ShipCmdSummonMount.summon(ship);

            helper.assertTrue(result == ShipCmdSummonMount.SummonResult.NOT_SUPPORTED,
                    "unsupported result");
            helper.assertTrue(ship.getStateEmotion(ID.S.State) == 6, "failure must preserve state");
            helper.assertTrue(!ship.isPassenger() && mountCount(helper, ship) == 0L,
                    "failure must not create a mount");
            helper.succeed();
        }
    }
}
