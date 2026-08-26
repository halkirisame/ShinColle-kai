package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
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
    public static void negativeYBlockDestinationRemainsActive(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (ship == null) {
            throw new AssertionError("Failed to create ship for negative-Y guard test.");
        }

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
}
