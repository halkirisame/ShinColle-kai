package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityShipFishingHook;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipFishingCommandGameTests {
    private ShipFishingCommandGameTests() { }

    @GameTest(template = "arena", timeoutTicks = 2400, batch = "isolated_ship_fishing")
    public static void singleFishingWalksToItsSpotAndRecastsAfterTheOwnerLeaves(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, "fishing_cycle", 33601);
            GameTestEntities.onCompletion(helper, context::close, "fishing command entities");
            context.level().addNewPlayer(context.player());
            GameTestEntities.onCompletion(helper, () -> context.level().removePlayerImmediately(
                    context.player(), Entity.RemovalReason.DISCARDED), "fishing admiral");
            context.player().setNoGravity(true);
            context.player().setInvulnerable(true);
            for (int x = 1; x <= 10; x++) {
                for (int z = 2; z <= 7; z++) {
                    if (x <= 7) {
                        helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                    } else {
                        for (int y = 1; y <= 3; y++) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                        }
                    }
                }
            }
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 3360100,
                    new Vec3(1.5D, 4D, 4.5D));
            ship.setOwnerUUID(context.player().getUUID());
            ship.setInvulnerable(true);
            ship.setShipLevel(50, true);
            ship.calcShipAttributes(31, false);
            ship.setHealth(ship.getMaxHealth());
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateMinor(ID.M.FollowMin, 0);
            ship.setStateMinor(ID.M.FollowMax, 2);
            ship.setStateMinor(ID.M.Task, 2);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            ship.setNoAi(false);
            PointerSingleModeGameTests.select(context.capa(), 0);
            BlockPos spot = helper.absolutePos(new BlockPos(6, 4, 4));
            Vec3 start = helper.absoluteVec(new Vec3(1.5D, 4D, 4.5D));
            boolean[] issued = {false};
            EntityShipFishingHook[] first = {null};
            int beforeItems = inventoryCount(ship);
            int[] beforeExp = {0};
            helper.runAtTickTime(30, () -> {
                helper.assertTrue(ship.tickCount > 16 && !ship.isNoAi(), "Fixture: natural AI is registered");
                helper.assertTrue(ConfigHandler.enableTask[1] && ship.getStateMinor(ID.M.Task) == 2,
                        "Fixture: fishing is selected and enabled");
                helper.assertTrue(!ship.getStateFlag(ID.F.NoFuel) && ship.getStateMinor(ID.M.NumGrudge) > 0,
                        "Fixture: ship has fuel");
                ship.moveTo(start.x, start.y, start.z, 0F, 0F);
                ship.setDeltaMovement(Vec3.ZERO);
                ship.getNavigation().stop();
                ship.goalSelector.removeAllGoals(goal -> goal instanceof com.lulan.shincolle.ai.ShipWanderGoal);
                beforeExp[0] = ship.getStateMinor(ID.M.ExpCurrent);
                PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                        new int[]{context.player().getId(), 0, PointerItem.MODE_SINGLE, 1,
                                spot.getX(), spot.getY(), spot.getZ(), 1}), "handleSetMove", context.player());
                PointerSingleModeGameTests.assertGuardDestination(helper, ship, spot, "single fishing spot");
                helper.assertTrue(ship.hasGuardDestination() && !ship.shouldReleaseGuardOnArrival(),
                        "Fishing single command must keep the spot before casting");
                Vec3 away = helper.absoluteVec(new Vec3(1.5D, 4D, 12.5D));
                context.player().moveTo(away.x, away.y, away.z, 0F, 0F);
                issued[0] = true;
            });
            helper.onEachTick(() -> {
                if (!issued[0]) return;
                helper.assertTrue(ship.hasGuardDestination() && !ship.shouldReleaseGuardOnArrival(),
                        "Walking or casting must not restore owner-follow");
                if (ship.fishHook != null && first[0] == null) {
                    first[0] = context.entities().add(ship.fishHook);
                    helper.assertTrue(ship.position().distanceToSqr(start) > 1D,
                            "Fishing must begin after the ship walks to the commanded spot");
                    helper.assertTrue(ship.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) <= 10D,
                            "The cast must be made near the commanded spot");
                }
                if (first[0] != null && first[0].isRemoved() && ship.fishHook != null
                        && ship.fishHook != first[0]) {
                    context.entities().add(ship.fishHook);
                    helper.assertTrue(inventoryCount(ship) > beforeItems, "Natural task ticks must deliver a catch");
                    helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) > beforeExp[0],
                            "Natural task ticks must award experience");
                    helper.assertTrue(ship.distanceToSqr(context.player()) > 25D,
                            "The ship must stay fishing when its owner walks away");
                    helper.succeed();
                }
            });
        }, new Vec3(1.5D, 4D, 4.5D), new Vec3(10.5D, 4D, 7.5D), new Vec3(1.5D, 4D, 12.5D));
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void fishingOrderCanBeCancelledWithoutChangingOtherModes(GameTestHelper helper) {
        PointerSingleModeGameTests.whenFixtureTicking(helper, () -> {
            try (var context = PointerSingleModeGameTests.createContext(helper, "fishing_modes", 33602)) {
                BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, 3360200,
                        new Vec3(4.5D, 2D, 4.5D));
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                PointerSingleModeGameTests.select(context.capa(), 0);
                BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
                ship.setStateMinor(ID.M.Task, 2);
                order(context, PointerItem.MODE_SINGLE, spot);
                helper.assertTrue(ship.hasGuardDestination() && !ship.shouldReleaseGuardOnArrival(),
                        "Single fishing command must hold its spot");
                order(context, PointerItem.MODE_SINGLE, spot);
                helper.assertTrue(!ship.hasGuardDestination() && ship.getStateFlag(ID.F.CanFollow),
                        "Repeating the same fishing order must return to follow");
                order(context, PointerItem.MODE_GROUP, spot);
                helper.assertTrue(ship.shouldReleaseGuardOnArrival(), "Group fishing keeps normal arrival release");
                ship.setStateMinor(ID.M.Task, 0);
                order(context, PointerItem.MODE_SINGLE, spot.offset(1, 0, 0));
                helper.assertTrue(ship.shouldReleaseGuardOnArrival(), "Non-fishing single move keeps arrival release");
                helper.succeed();
            }
        });
    }

    private static void order(PointerSingleModeGameTests.TestContext context, int mode, BlockPos spot) {
        PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                new int[]{context.player().getId(), 0, mode, 1, spot.getX(), spot.getY(), spot.getZ(), 1}),
                "handleSetMove", context.player());
    }

    private static int inventoryCount(BasicEntityShip ship) {
        int count = 0;
        for (int slot = 0; slot < ship.getCapaShipInventory().getSlots(); slot++) {
            count += ship.getCapaShipInventory().getStackInSlot(slot).getCount();
        }
        return count;
    }
}
