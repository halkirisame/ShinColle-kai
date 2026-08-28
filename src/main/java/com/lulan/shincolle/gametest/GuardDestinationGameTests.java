package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardDestinationGameTests {

    private GuardDestinationGameTests() {
    }

    @GameTest(template = "arena")
    public static void newShipStartsWithoutPhantomGuardDestination(GameTestHelper helper) {
        BasicEntityShip ship = createShip(helper, "new-ship guard initialization");
        ship.setStateMinor(ID.M.NumGrudge, 100);

        helper.assertTrue(ship.getStateFlag(ID.F.CanFollow),
                "A new friendly ship must start in follow mode");
        helper.assertTrue(!ship.hasGuardDestination(),
                "Cleared legacy coordinates must not become a guard destination");
        helper.assertTrue(!new ShipGuardingGoal(ship).canUse(),
                "Guarding goal must not start without an explicit command");

        ship.discard();
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void legacyClearedGuardStateRestoresFollowMode(GameTestHelper helper) {
        BasicEntityShip source = createShip(helper, "legacy guard source");
        source.setStateFlag(ID.F.CanFollow, false);
        CompoundTag saved = new CompoundTag();
        source.addAdditionalSaveData(saved);
        saved.remove("GuardDimension");
        saved.remove("GuardEntityUUID");

        BasicEntityShip restored = createShip(helper, "legacy guard restore");
        restored.readAdditionalSaveData(saved);

        helper.assertTrue(restored.getStateFlag(ID.F.CanFollow),
                "A legacy cleared guard state must be repaired to follow mode");
        helper.assertTrue(!restored.hasGuardDestination(),
                "A repaired legacy state must not expose a guard destination");

        source.discard();
        restored.discard();
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void negativeYGuardDestinationSurvivesNbtRoundTrip(GameTestHelper helper) {
        BasicEntityShip source = createShip(helper, "negative-Y guard source");
        BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 1));
        if (destination.getY() >= 0) {
            throw new AssertionError("GameTest arena no longer exercises a negative Y: " + destination);
        }
        source.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                helper.getLevel().dimension(), 1);
        source.setStateFlag(ID.F.CanFollow, false);
        CompoundTag saved = new CompoundTag();
        source.addAdditionalSaveData(saved);

        BasicEntityShip restored = createShip(helper, "negative-Y guard restore");
        restored.readAdditionalSaveData(saved);

        helper.assertTrue(!restored.getStateFlag(ID.F.CanFollow),
                "An active negative-Y guard must not be repaired to follow mode");
        helper.assertTrue(restored.hasGuardDestination(),
                "An active negative-Y guard must survive NBT");
        helper.assertTrue(restored.getGuardedPos(0) == destination.getX()
                        && restored.getGuardedPos(1) == destination.getY()
                        && restored.getGuardedPos(2) == destination.getZ(),
                "Guard coordinates changed during NBT round-trip");
        helper.assertTrue(restored.isGuardedInCurrentDimension(),
                "Guard dimension changed during NBT round-trip");

        source.discard();
        restored.discard();
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void negativeYBlockDestinationRemainsActive(GameTestHelper helper) {
        BasicEntityShip ship = createShip(helper, "negative-Y guard test");

        Vec3 shipPos = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 1));
        if (destination.getY() >= 0) {
            throw new AssertionError("GameTest arena no longer exercises a negative Y: " + destination);
        }

        ship.moveTo(shipPos.x, shipPos.y, shipPos.z, 0F, 0F);
        helper.getLevel().addFreshEntity(ship);
        ship.setStateMinor(ID.M.NumGrudge, 100);
        ship.setStateMinor(ID.M.FormatType, 1);
        ship.setGuardedPos(destination.getX(), destination.getY(), destination.getZ(),
                helper.getLevel().dimension(), 1);
        ship.setStateFlag(ID.F.CanFollow, false);

        ShipGuardingGoal goal = new ShipGuardingGoal(ship);
        if (!goal.canUse()) {
            throw new AssertionError("Guarding goal rejected a valid negative-Y block destination.");
        }
        if (!ship.hasGuardDestination() || ship.getGuardedPos(1) != destination.getY()
                || ship.getStateFlag(ID.F.CanFollow)) {
            throw new AssertionError("Negative-Y guard destination was cleared or marked inactive.");
        }
        if (!ship.isGuardedInCurrentDimension()) {
            throw new AssertionError("Guard destination lost its ResourceLocation dimension.");
        }

        ship.discard();
        helper.succeed();
    }

    private static BasicEntityShip createShip(GameTestHelper helper, String purpose) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (ship == null) {
            throw new AssertionError("Failed to create ship for " + purpose + ".");
        }
        ship.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
        helper.getLevel().addFreshEntity(ship);
        return ship;
    }
}
