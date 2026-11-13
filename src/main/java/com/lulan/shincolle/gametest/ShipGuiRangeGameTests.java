package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.S2CGUISyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipGuiRangeGameTests {
    private ShipGuiRangeGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void pointerMenuRemainsValidAtTwentyBlocks(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.movePlayer(20);
            fixture.open(false);
            helper.assertTrue(fixture.player.containerMenu instanceof ContainerShipInventory,
                    "Three-value request must open the ship menu at distance 20");
            helper.assertTrue(fixture.player.containerMenu.stillValid(fixture.player),
                    "Remote ship menu must remain valid at distance 20");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void pointerMenuRejectsOneHundredBlocksAndIncludesSixtyFour(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.movePlayer(100);
            fixture.open(false);
            fixture.assertClosed();
            fixture.movePlayer(64);
            fixture.open(false);
            fixture.assertValid();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void fleetMenuOpensAtFiftyBlocksWithLargeRange(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.register(2);
            fixture.movePlayer(50);
            fixture.open(true);
            fixture.assertValid();
            fixture.capa.setShipGuiRangeStage(0, 0);
            fixture.assertValid();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void fleetMenuClosesBeyondItsOpeningRange(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.register(2);
            fixture.movePlayer(50);
            fixture.open(true);
            fixture.assertValid();
            fixture.movePlayer(64);
            fixture.assertValid();
            fixture.movePlayer(70);
            helper.assertTrue(!fixture.player.containerMenu.stillValid(fixture.player),
                    "Menu must reject distance 70 after opening with range 64");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void smallFleetMenuUsesSixteenBlockLifetime(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.register(0);
            fixture.movePlayer(12);
            fixture.open(true);
            fixture.assertValid();
            fixture.movePlayer(16);
            fixture.assertValid();
            fixture.movePlayer(20);
            helper.assertTrue(!fixture.player.containerMenu.stillValid(fixture.player),
                    "Menu opened with range 16 must reject distance 20");
            fixture.player.closeContainer();
            fixture.movePlayer(8);
            fixture.ship.openGUI(fixture.player);
            fixture.assertValid();
            fixture.movePlayer(8.01);
            helper.assertTrue(!fixture.player.containerMenu.stillValid(fixture.player),
                    "Direct ship opening must retain its eight-block limit");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void smallFleetMenuRejectsThirtyBlocksAndMediumUsesThirtyTwo(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.register(0);
            fixture.movePlayer(30);
            fixture.open(true);
            fixture.assertClosed();
            fixture.register(1);
            helper.assertTrue(fixture.capa.getShipGuiRange(0) == 32, "Medium range must be 32");
            fixture.movePlayer(32);
            fixture.open(true);
            fixture.assertValid();
            fixture.movePlayer(32.01);
            helper.assertTrue(!fixture.player.containerMenu.stillValid(fixture.player),
                    "Medium range must stop beyond 32");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void fleetMenuRequiresMembershipValidTeamPointerAndOwner(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.capa.setShipGuiRangeStage(0, 2);
            helper.assertTrue(!fixture.capa.isShipInTeam(0, fixture.ship.getShipUID())
                            && fixture.capa.getShipGuiRangeStage(0) == 2,
                    "Unregistered fixture must have large fleet range");
            fixture.movePlayer(5);
            fixture.open(true);
            fixture.assertClosed();
            fixture.register(2);
            for (int team : new int[]{-1, CapaTeitoku.TEAM_NUM, 1}) {
                fixture.openValues(new int[]{fixture.player.getId(), 0, fixture.ship.getId(), team});
                fixture.assertClosed();
            }
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fixture.open(true);
            fixture.assertClosed();
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));
            fixture.ship.setOwnerUUID(UUID.randomUUID());
            fixture.ship.setPlayerUID(734799);
            helper.assertTrue(!fixture.ship.isOwnedBy(fixture.player)
                            && fixture.ship.getPlayerUID() != fixture.capa.getPlayerUID(),
                    "Foreign ownership must be applied");
            fixture.open(true);
            fixture.assertClosed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void rangeSettingChangesOnlyRequestedFleetWithoutPointer(GameTestHelper helper) {
        withShip(helper, fixture -> {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(fixture.player.getMainHandItem().isEmpty(), "Setting must be tested without a pointer");
            for (int stage = 0; stage <= 2; stage++) {
                fixture.setRange(new int[]{fixture.player.getId(), 4, stage});
                for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                    helper.assertTrue(fixture.capa.getShipGuiRangeStage(team) == (team == 4 ? stage : 0),
                            "Setting changed the wrong fleet or stage");
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void malformedRangeSettingsAreIgnored(GameTestHelper helper) {
        withShip(helper, fixture -> {
            for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                fixture.capa.setShipGuiRangeStage(team, team % 3);
            }
            int pid = fixture.player.getId();
            for (int[] values : new int[][]{{}, {pid}, {pid, 0}, {pid, -1, 2}, {pid, 9, 2},
                    {pid, 0, -1}, {pid, 0, 3}, {pid, 4, Integer.MAX_VALUE}}) {
                fixture.setRange(values);
                for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                    helper.assertTrue(fixture.capa.getShipGuiRangeStage(team) == team % 3,
                            "Malformed request changed fleet range");
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedFleetRangesRoundTripAndInvalidOrMissingDefaultToZero(GameTestHelper helper) {
        CapaTeitoku source = new CapaTeitoku();
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            source.setShipGuiRangeStage(team, team % 3);
            source.setRetaliatePlayer(team, team % 2 == 0);
        }
        CapaTeitoku target = new CapaTeitoku();
        CompoundTag saved = source.serializeNBT();
        target.deserializeNBT(saved);
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            helper.assertTrue(target.getShipGuiRangeStage(team) == team % 3
                            && target.getRetaliatePlayer(team) == (team % 2 == 0), "NBT round trip changed fleet values");
        }
        ListTag teams = saved.getList("Teams", Tag.TAG_COMPOUND);
        teams.getCompound(0).remove("ShipGuiRange");
        teams.getCompound(1).putInt("ShipGuiRange", -1);
        teams.getCompound(2).putInt("ShipGuiRange", 3);
        teams.getCompound(3).putString("ShipGuiRange", "2");
        target.deserializeNBT(saved);
        for (int team = 0; team < 4; team++) {
            helper.assertTrue(target.getShipGuiRangeStage(team) == 0 && target.getShipGuiRange(team) == 16,
                    "Missing, malformed and out-of-range NBT must default to 16");
        }
        target.deserializeNBT(new CompoundTag());
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            helper.assertTrue(target.getShipGuiRangeStage(team) == 0, "Missing Teams must reset ranges");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void miscAndFullSyncRoundTripRangesPermissionsAndFollowingData(GameTestHelper helper) {
        CapaTeitoku source = new CapaTeitoku();
        source.setPlayerUID(734720);
        source.setHasRing(true);
        source.setRingActive(true);
        source.setRingFlying(false);
        source.setMarriageNum(7);
        source.setSelectTeam(3);
        source.setTeamCooldown(119);
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            source.setShipGuiRangeStage(team, team % 3);
            source.setRetaliatePlayer(team, team % 2 == 0);
            source.setFormatID(team, team % 5);
        }
        for (boolean full : new boolean[]{false, true}) {
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            FriendlyByteBuf payload = null;
            try {
                (full ? S2CGUISyncPacket.syncPlayerFull(source) : S2CGUISyncPacket.syncPlayerMisc(source)).encode(wire);
                helper.assertTrue(wire.readByte() == (full ? S2CGUISyncPacket.SyncPlayerProp
                        : S2CGUISyncPacket.SyncPlayerProp_Misc), "Wrong sync type");
                int length = wire.readVarInt();
                helper.assertTrue(length == (full ? 128 : 38), "Wrong misc/full sync length");
                payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(wire.readByteArray(length)));
                CapaTeitoku roundTrip = new CapaTeitoku();
                readMisc(payload, roundTrip);
                for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                    helper.assertTrue(roundTrip.getShipGuiRangeStage(team) == source.getShipGuiRangeStage(team)
                                    && roundTrip.getRetaliatePlayer(team) == source.getRetaliatePlayer(team),
                            "Unmodified misc round trip must preserve all nine fleet values");
                }
                payload.readerIndex(0);
                payload.setByte(29, -1);
                payload.setByte(30, 3);
                CapaTeitoku target = new CapaTeitoku();
                readMisc(payload, target);
                helper.assertTrue(target.getPlayerUID() == 734720 && target.hasRing() && target.isRingActive()
                                && !target.isRingFlying() && target.getMarriageNum() == 7 && target.getSelectTeam() == 3
                                && target.getTeamCooldown() == 119, "Misc prefix round trip changed values");
                for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                    helper.assertTrue(target.getShipGuiRangeStage(team) == (team < 2 ? 0 : team % 3)
                                    && target.getRetaliatePlayer(team) == (team % 2 == 0),
                            "Sync must retain fleet values and normalize invalid stages");
                }
                if (full) {
                    for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                        helper.assertTrue(payload.readInt() == source.getFormatID(team), "Formation offset shifted");
                    }
                    for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                        helper.assertTrue(payload.readInt() == source.getTeamMember(3, slot)
                                        && payload.readInt() == source.getTeamSID(3, slot)
                                        && payload.readBoolean() == source.isShipSelected(3, slot),
                                "Team data following misc shifted");
                    }
                }
                helper.assertTrue(!payload.isReadable() && !wire.isReadable(), "Unexpected trailing sync data");
            } finally {
                if (payload != null) payload.release();
                wire.release();
            }
        }
        helper.succeed();
    }

    private static void readMisc(FriendlyByteBuf buf, CapaTeitoku capa) {
        try {
            Method method = S2CGUISyncPacket.class.getDeclaredMethod("readPlayerMisc", FriendlyByteBuf.class, CapaTeitoku.class);
            method.setAccessible(true);
            method.invoke(null, buf, capa);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Cannot invoke misc reader: " + error);
        }
    }

    private static void withShip(GameTestHelper helper, Consumer<Fixture> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "ship_gui_range"));
                player.closeContainer();
                CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
                helper.assertTrue(capa != null, "Player must have admiral capability");
                capa.setPlayerUID(734720);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));
                BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                Vec3 pos = helper.absoluteVec(new Vec3(1, 2, 1));
                ship.moveTo(pos.x, pos.y, pos.z);
                ship.tame(player);
                ship.setOwnerUUID(player.getUUID());
                ship.setPlayerUID(capa.getPlayerUID());
                ship.setShipUID(734721);
                ship.setStateMinor(ID.M.NumGrudge, 1000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setInvulnerable(true);
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Ship must be added");
                helper.assertTrue(helper.getLevel().getEntity(ship.getId()) == ship && ship.isAlive()
                                && player.getUUID().equals(ship.getOwnerUUID()) && TeamHelper.checkSameOwner(player, ship)
                                && ship.getPlayerUID() == capa.getPlayerUID()
                                && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                                && player.getMainHandItem().is(ModItems.POINTER.get()),
                        "Fixture ownership, lookup and pointer must be applied");
                try {
                    verification.accept(new Fixture(helper, player, capa, ship));
                } finally {
                    player.closeContainer();
                }
            }
            helper.succeed();
        }, new Vec3(1, 2, 1));
    }

    private record Fixture(GameTestHelper helper, ServerPlayer player, CapaTeitoku capa, BasicEntityShip ship) {
        void movePlayer(double distance) {
            player.moveTo(ship.getX() + distance, ship.getY(), ship.getZ());
            helper.assertTrue(Math.abs(player.distanceToSqr(ship) - distance * distance) < 0.0001,
                    "Fixture distance must be applied: " + distance);
        }

        void open(boolean fleet) {
            int[] values = fleet ? new int[]{player.getId(), 0, ship.getId(), 0}
                    : new int[]{player.getId(), 0, ship.getId()};
            openValues(values);
        }

        void openValues(int[] values) {
            PointerSingleModeGameTests.invokePacketHandler(
                    new C2SGUIInputPacket(C2SGUIInputPacket.OpenShipGUI, values), "handleOpenShipGUI", player);
        }

        void register(int stage) {
            capa.setTeamMember(0, 0, ship.getShipUID());
            capa.setTeamSID(0, 0, ship.getId());
            capa.setShipGuiRangeStage(0, stage);
            helper.assertTrue(capa.isShipInTeam(0, ship.getShipUID()) && capa.getTeamSID(0, 0) == ship.getId()
                            && capa.getShipGuiRangeStage(0) == stage, "Fleet membership and range must be applied");
        }

        void assertValid() {
            helper.assertTrue(player.containerMenu instanceof ContainerShipInventory menu && menu.getShip() == ship
                            && menu.stillValid(player), "Expected a valid menu for this ship");
        }

        void assertClosed() {
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Request must not open a menu");
        }

        void setRange(int[] values) {
            PointerSingleModeGameTests.invokePacketHandler(
                    new C2SGUIInputPacket(C2SGUIInputPacket.SetShipGuiRange, values), "handleSetShipGuiRange", player);
        }
    }
}
