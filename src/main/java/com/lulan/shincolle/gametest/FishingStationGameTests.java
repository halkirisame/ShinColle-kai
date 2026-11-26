package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityShipFishingHook;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.function.BiConsumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FishingStationGameTests {
    private FishingStationGameTests() { }

    @GameTest(template = "arena", timeoutTicks = 220, batch = "isolated_fishing_station_single")
    public static void taskOffEndsSingleStationWithoutReturningToOldSpot(GameTestHelper helper) {
        endsStation(helper, true, PointerItem.MODE_SINGLE, 49301);
    }

    @GameTest(template = "arena", timeoutTicks = 220, batch = "isolated_fishing_station_explicit")
    public static void taskOffEndsExplicitSingleStationWithoutReturningToOldSpot(GameTestHelper helper) {
        endsStation(helper, false, PointerItem.MODE_SINGLE, 49302);
    }

    @GameTest(template = "arena", timeoutTicks = 220, batch = "isolated_fishing_station_group")
    public static void taskOffEndsGroupStationBeforeArrival(GameTestHelper helper) {
        endsStation(helper, true, PointerItem.MODE_GROUP, 49303);
    }

    private static void endsStation(GameTestHelper helper, boolean release, int mode, int id) {
        fixture(helper, id, (context, ship) -> {
            BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 4));
            task(context, ship, 2);
            order(context, spot, mode, true, release);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition,
                    "Fixture: fishing guard was accepted: " + ship.getCommandState());
            task(context, ship, 0);
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow
                                && !ship.hasGuardDestination() && ship.getStateFlag(ID.F.CanFollow),
                        "Fishing OFF must return the station to Follow on a natural tick");
                helper.assertTrue(ship.getLastCommandStateChange().issuer() instanceof CommandIssuer.Ship issuer
                                && issuer.reason().name().equals("FISHING_DISABLED"),
                        "Fishing OFF must record its transition reason");
                Vec3 away = helper.absoluteVec(new Vec3(31.5D, 2D, 4.5D));
                ship.moveTo(away.x, away.y, away.z, 0F, 0F);
                ship.setDeltaMovement(Vec3.ZERO);
                context.player().moveTo(away.x, away.y, away.z + 2D, 0F, 0F);
            });
            helper.onEachTick(() -> {
                if (helper.getTick() < 35) return;
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "The old station must not return");
                helper.assertTrue(ship.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) > 400D,
                        "No path or teleport may bring the ship back to the old station");
            });
            helper.runAtTickTime(170, helper::succeed);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_station")
    public static void taskOffPreservesGuardIssuedBeforeFishing(GameTestHelper helper) {
        fixture(helper, 49304, (context, ship) -> {
            order(context, helper.absolutePos(new BlockPos(8, 2, 4)), PointerItem.MODE_SINGLE, true, false);
            MovementOrder before = ship.getCommandState().movement();
            helper.assertTrue(before instanceof MovementOrder.GuardPosition, "Fixture: ordinary guard exists");
            task(context, ship, 2);
            task(context, ship, 0);
            helper.runAtTickTime(65, () -> {
                helper.assertTrue(before.equals(ship.getCommandState().movement()),
                        "A guard issued before fishing must survive ON then OFF");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_station")
    public static void newerMoveOnlySurvivesFishingOff(GameTestHelper helper) {
        fixture(helper, 49305, (context, ship) -> {
            task(context, ship, 2);
            order(context, helper.absolutePos(new BlockPos(8, 2, 4)), PointerItem.MODE_SINGLE, true, true);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition,
                    "Fixture: station exists before replacement");
            order(context, helper.absolutePos(new BlockPos(28, 2, 4)), PointerItem.MODE_SINGLE, false, false);
            MovementOrder before = ship.getCommandState().movement();
            helper.assertTrue(before instanceof MovementOrder.MoveTo, "Fixture: move-only replaced station");
            task(context, ship, 0);
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(before.equals(ship.getCommandState().movement()),
                        "Fishing OFF must not cancel a newer move-only order");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200, batch = "isolated_fishing_config_arrival")
    public static void globallyDisabledFishingReleasesSingleOrderOnArrival(GameTestHelper helper) {
        fixture(helper, 49306, (context, ship) -> {
            boolean enabled = ConfigHandler.enableTask[1];
            GameTestEntities.onCompletion(helper, () -> ConfigHandler.enableTask[1] = enabled, "fishing setting");
            ConfigHandler.enableTask[1] = false;
            task(context, ship, 2);
            order(context, helper.absolutePos(new BlockPos(8, 2, 4)), PointerItem.MODE_SINGLE, true, true);
            helper.assertTrue(!ConfigHandler.enableTask[1] && ship.getStateMinor(ID.M.Task) == 2,
                    "Fixture: task selected but globally disabled");
            helper.assertTrue(ship.shouldReleaseGuardOnArrival(),
                    "Globally disabled fishing must not turn SINGLE into a persistent hold");
            helper.runAtTickTime(140, () -> {
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "The globally disabled fishing order must release after natural arrival");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_config_off")
    public static void globalDisableEndsAnExistingStation(GameTestHelper helper) {
        fixture(helper, 49307, (context, ship) -> {
            task(context, ship, 2);
            order(context, helper.absolutePos(new BlockPos(8, 2, 4)), PointerItem.MODE_SINGLE, true, false);
            helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition,
                    "Fixture: station exists before global disable");
            boolean enabled = ConfigHandler.enableTask[1];
            GameTestEntities.onCompletion(helper, () -> ConfigHandler.enableTask[1] = enabled, "fishing setting");
            ConfigHandler.enableTask[1] = false;
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Global disable must end an existing fishing station");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_station")
    public static void fishingStationSurvivesNbtAndOldSavesHaveNoMark(GameTestHelper helper) {
        fixture(helper, 49308, (context, ship) -> {
            task(context, ship, 2);
            order(context, helper.absolutePos(new BlockPos(8, 2, 4)), PointerItem.MODE_SINGLE, true, false);
            CompoundTag saved = new CompoundTag();
            ship.addAdditionalSaveData(saved);
            helper.assertTrue(saved.getBoolean("FishingStation"), "Station mark must be saved");
            ship.readAdditionalSaveData(saved);
            helper.assertTrue(((MovementOrder.GuardPosition) ship.getCommandState().movement()).fishingStation(),
                    "Station mark must survive NBT round trip");
            task(context, ship, 0);
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Loaded fishing station must still end on OFF");
                for (int version : new int[]{0, 1}) {
                    CompoundTag old = saved.copy();
                    old.remove("FishingStation");
                    if (version == 0) old.remove("AiDataVersion");
                    else old.putInt("AiDataVersion", version);
                    ship.readAdditionalSaveData(old);
                    helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition guard
                                    && !guard.fishingStation(),
                            "Old saves must retain ordinary guards without inferring fishing ownership");
                }
                task(context, ship, 0);
                helper.runAtTickTime(38, () -> {
                    helper.assertTrue(ship.getCommandState().movement() instanceof MovementOrder.GuardPosition,
                            "Old unmarked guard must survive fishing OFF");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "isolated_fishing_station")
    public static void fishingOffRemovesTaskFloatWithoutAnyStation(GameTestHelper helper) {
        fixture(helper, 49309, (context, ship) -> {
            task(context, ship, 2);
            EntityShipFishingHook hook = context.entities().add(ModEntities.FISHING_HOOK.get().create(context.level()));
            helper.assertTrue(hook != null, "Fixture: task float can be created");
            hook.setHost(ship);
            hook.setPos(ship.getX(), ship.getY() + 1D, ship.getZ());
            ship.fishHook = hook;
            helper.assertTrue(context.level().addFreshEntity(hook), "Fixture: task float spawned");
            helper.runAtTickTime(34, () -> {
                helper.assertTrue(!hook.isRemoved() && ship.fishHook == hook
                                && ship.getCommandState().movement() instanceof MovementOrder.Follow,
                        "Fixture: active fishing retains its float without a station");
                task(context, ship, 0);
            });
            helper.runAtTickTime(38, () -> {
                helper.assertTrue(hook.isRemoved() && ship.fishHook == null,
                        "Fishing OFF must discard the task float and unlink it from its host");
                helper.succeed();
            });
        });
    }

    static void fixture(GameTestHelper helper, int id,
                                BiConsumer<PointerSingleModeGameTests.TestContext, BasicEntityShip> scenario) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            var context = PointerSingleModeGameTests.createContext(helper, "station_" + id, id);
            GameTestEntities.onCompletion(helper, context::close, "fishing station entities");
            for (int x = 1; x <= 34; x++) {
                for (int z = 1; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            BasicEntityShip ship = PointerSingleModeGameTests.addShip(context, 0, id * 100,
                    new Vec3(4.5D, 2D, 4.5D));
            ship.setOwnerUUID(context.player().getUUID());
            ship.setInvulnerable(true);
            ship.setStateMinor(ID.M.NumGrudge, 100_000);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.setStateFlag(ID.F.PassiveAI, true);
            ship.setStateFlag(ID.F.PickItem, false);
            ship.setStateMinor(ID.M.FollowMin, 0);
            ship.setStateMinor(ID.M.FollowMax, 1);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            ship.setNoAi(false);
            PointerSingleModeGameTests.select(context.capa(), 0);
            helper.runAtTickTime(30, () -> {
                helper.assertTrue(ship.tickCount > 24 && !ship.isNoAi()
                                && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                        "Fixture: natural AI registered and fueled");
                helper.assertTrue(ConfigHandler.enableTask[1], "Fixture: global fishing starts enabled");
                ship.goalSelector.removeAllGoals(goal -> goal instanceof com.lulan.shincolle.ai.ShipWanderGoal);
                ship.moveTo(helper.absoluteVec(new Vec3(4.5D, 2D, 4.5D)).x, ship.getY(),
                        helper.absoluteVec(new Vec3(4.5D, 2D, 4.5D)).z);
                scenario.accept(context, ship);
            });
        }, new Vec3(1D, 2D, 1D), new Vec3(16D, 2D, 4D), new Vec3(34D, 2D, 8D));
    }

    static void task(PointerSingleModeGameTests.TestContext context, BasicEntityShip ship, int task) {
        context.player().moveTo(ship.getX(), ship.getY(), ship.getZ() + 1D, 0F, 0F);
        try {
            PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.OpenShipGUI,
                    new int[]{context.player().getId(), 0, ship.getId()}), "handleOpenShipGUI", context.player());
            context.helper().assertTrue(context.player().containerMenu instanceof ContainerShipInventory menu
                            && menu.getShip() == ship && menu.stillValid(context.player()),
                    "Fixture: authorized ship menu is open");
            PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.ShipBtn,
                    new int[]{ship.getId(), 0, ID.B.ShipInv_Task, task}), "handleShipBtn", context.player());
            context.helper().assertTrue(ship.getStateMinor(ID.M.Task) == task, "Fixture: GUI accepted task change");
        } finally {
            context.player().closeContainer();
        }
    }

    static void order(PointerSingleModeGameTests.TestContext context, BlockPos spot,
                              int mode, boolean guard, boolean release) {
        BasicEntityShip ship = (BasicEntityShip) context.level().getEntity(context.capa().getTeamSID(
                context.capa().getSelectTeam(), 0));
        context.helper().assertTrue(ship != null
                        && context.capa().getTeamMember(context.capa().getSelectTeam(), 0) == ship.getShipUID()
                        && ship.getPlayerUID() == context.capa().getPlayerUID()
                        && context.capa().isShipSelected(context.capa().getSelectTeam(), 0),
                "Fixture: command recipient remains selected with matching UID, SID and owner");
        PointerSingleModeGameTests.invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetMove,
                new int[]{context.player().getId(), 0, mode, guard ? 1 : 0,
                        spot.getX(), spot.getY(), spot.getZ(), release ? 1 : 0}), "handleSetMove", context.player());
    }
}
