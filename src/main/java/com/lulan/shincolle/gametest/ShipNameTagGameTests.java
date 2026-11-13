package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerFormation;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.PacketHelper;
import com.lulan.shincolle.utility.ShipNameTagHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipNameTagGameTests {
    private static final Vec3 POSITION = new Vec3(2D, 3D, 2D);
    private static final AtomicInteger NEXT_UID = new AtomicInteger(7_000_000);

    private ShipNameTagGameTests() {}

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void nameSyncBuildsEveryMembershipOnceInExistingPayload(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.member(0, 0, fixture.ship);
                fixture.member(0, 1, fixture.ship);
                fixture.member(4, 2, fixture.ship);
                fixture.capa.setUnitName(0, "First Fleet");
                fixture.capa.setUnitName(4, "Fifth Fleet");
                helper.assertTrue(fixture.ship.unitNames.isEmpty(), "Fixture must begin without derived names");
                FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
                FriendlyByteBuf payload = null;
                try {
                    S2CEntitySyncPacket.syncUnitName(fixture.ship).encode(encoded);
                    helper.assertTrue(encoded.readByte() == S2CEntitySyncPacket.SyncShip_UnitName,
                            "Existing name-sync type must be retained");
                    helper.assertTrue(encoded.readInt() == fixture.ship.getId(), "Existing entity id must be retained");
                    int length = encoded.readVarInt();
                    byte[] bytes = encoded.readByteArray();
                    helper.assertTrue(bytes.length == length && !encoded.isReadable(),
                            "Existing payload framing must not acquire new fields");
                    payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                    List<String> expected = List.of("First Fleet", "Fifth Fleet");
                    helper.assertTrue(PacketHelper.readStringList(payload).equals(expected) && !payload.isReadable(),
                            "Payload must contain all memberships, once per team, in team order");
                    helper.assertTrue(fixture.ship.unitNames.equals(expected), "Factory must rebuild names before sending");
                    helper.assertTrue(!ShipNameTagHelper.refresh(fixture.ship), "Unchanged names must not require periodic sync");
                    fixture.capa.setUnitName(4, "Renamed Fifth");
                    helper.assertTrue(ShipNameTagHelper.refresh(fixture.ship)
                                    && fixture.ship.unitNames.equals(List.of("First Fleet", "Renamed Fifth")),
                            "Changed names must be detected for periodic synchronization");
                } finally {
                    if (payload != null) payload.release();
                    encoded.release();
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void guiRenameUpdatesOwnedShipsWithoutTouchingForeignShip(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.member(0, 0, fixture.ship);
                BasicEntityShip foreign = fixture.newShip();
                foreign.setPlayerUID(fixture.ownerUid + 1);
                foreign.unitNames.add("Foreign name");
                fixture.member(0, 1, foreign);
                helper.assertTrue(foreign.getPlayerUID() != fixture.capa.getPlayerUID(), "Foreign owner fixture");
                invoke(new C2SGUIInputPacket(C2SGUIInputPacket.SetUnitName,
                        new int[]{fixture.player.getId(), 0, 0}, "New Fleet"), "handleSetUnitName", fixture.player);
                helper.assertTrue(fixture.capa.getUnitName(0).equals("New Fleet"), "Team must be renamed");
                helper.assertTrue(fixture.ship.unitNames.equals(List.of("New Fleet")),
                        "GUI rename must immediately rebuild the owned member's name");
                helper.assertTrue(foreign.unitNames.equals(List.of("Foreign name")),
                        "Stale or foreign team entity ids must not rename another owner's ship");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void guiMembershipAndClearRefreshNameImmediately(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.capa.setSelectTeam(0);
                fixture.capa.setUnitName(0, "Joined Fleet");
                fixture.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.POINTER.get()));
                C2SGUIInputPacket toggle = new C2SGUIInputPacket(C2SGUIInputPacket.AddTeam,
                        new int[]{fixture.player.getId(), 0, fixture.ship.getId(), 1});
                invoke(toggle, "handleAddTeam", fixture.player);
                helper.assertTrue(fixture.capa.getTeamMember(0, 0) == fixture.ship.getShipUID()
                                && fixture.capa.isShipSelected(0, 0), "Pointer must add and select the intended member");
                helper.assertTrue(fixture.ship.unitNames.equals(List.of("Joined Fleet")), "Added member must acquire its name");
                invoke(toggle, "handleAddTeam", fixture.player);
                helper.assertTrue(fixture.capa.getTeamMember(0, 0) == 0 && fixture.ship.unitNames.isEmpty(),
                        "Removed member must immediately lose the removed team name");
                invoke(toggle, "handleAddTeam", fixture.player);
                helper.assertTrue(fixture.ship.unitNames.equals(List.of("Joined Fleet")), "Re-added member fixture");
                invoke(new C2SGUIInputPacket(C2SGUIInputPacket.ClearTeam, new int[0]),
                        "handleClearTeam", fixture.player);
                helper.assertTrue(fixture.capa.getTeamMember(0, 0) == 0 && fixture.ship.unitNames.isEmpty(),
                        "Clearing a team must refresh its former members after all memberships are removed");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void unresolvedOwnerKeepsLegacyNamesUntilOwnerCanBeResolved(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.ship.unitNames = new ArrayList<>(List.of("Saved Fleet"));
                fixture.ship.setOwnerUUID(UUID.randomUUID());
                fixture.ship.setPlayerUID(fixture.ownerUid + 2);
                helper.assertTrue(fixture.ship.getOwner() == null, "Owner must be unresolved in this fixture");
                helper.assertTrue(!ShipNameTagHelper.refresh(fixture.ship), "Unresolved owner cannot rebuild derived names");
                S2CEntitySyncPacket.syncUnitName(fixture.ship);
                helper.assertTrue(fixture.ship.unitNames.equals(List.of("Saved Fleet")),
                        "Sync must retain names read from an old save when owner is absent");
                fixture.ship.setOwnerUUID(fixture.player.getUUID());
                fixture.ship.setPlayerUID(fixture.ownerUid);
                helper.assertTrue(fixture.ship.getOwner() == fixture.player, "Owner must resolve again");
                helper.assertTrue(ShipNameTagHelper.refresh(fixture.ship) && fixture.ship.unitNames.isEmpty(),
                        "Resolved owner with no memberships must clear stale saved names");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void guiRemoveUnloadedUidLeavesOtherSlotsAndSelectionsInPlace(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.openFormation();
                int uid = NEXT_UID.getAndIncrement();
                int otherUid = NEXT_UID.getAndIncrement();
                fixture.capa.setTeamMember(0, 0, uid);
                fixture.capa.setTeamSID(0, 0, 123456);
                fixture.capa.setShipSelected(0, 0, true);
                fixture.capa.setTeamMember(0, 3, otherUid);
                fixture.capa.setTeamSID(0, 3, -1);
                fixture.capa.setShipSelected(0, 3, true);
                fixture.capa.setFormatID(0, 2);
                int emptyUid = fixture.capa.getTeamMember(0, 1);
                helper.assertTrue(ServerDataManager.getShipByUID(uid) == null
                                && ServerDataManager.getShipByUID(otherUid) == null
                                && fixture.capa.getTeamMember(0, 0) == uid
                                && fixture.capa.getTeamSID(0, 0) == 123456
                                && fixture.capa.isShipSelected(0, 0) && fixture.capa.getFormatID(0) == 2
                                && fixture.capa.getTeamMember(0, 3) == otherUid
                                && fixture.capa.getTeamSID(0, 3) == -1 && fixture.capa.isShipSelected(0, 3)
                                && emptyUid <= 0,
                        "Unloaded membership and active formation fixture");

                invoke(fixture.removePacket(0, 0, uid), "handleRemoveTeamShip", fixture.player);

                helper.assertTrue(fixture.capa.getTeamMember(0, 0) == 0
                                && fixture.capa.getTeamSID(0, 0) == 0 && !fixture.capa.isShipSelected(0, 0),
                        "Unloaded member must be cleared by uid and slot alone");
                helper.assertTrue(fixture.capa.getTeamMember(0, 3) == otherUid
                                && fixture.capa.getTeamSID(0, 3) == -1 && fixture.capa.isShipSelected(0, 3)
                                && fixture.capa.getTeamMember(0, 1) == emptyUid && fixture.capa.getFormatID(0) == 0,
                        "Removal must not compact slots or change other selections, and must clear formation");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void guiRemoveLoadedShipRefreshesNamesAndResetsFiveRemainingShips(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.openFormation();
                fixture.capa.setUnitName(0, "Removed Fleet");
                List<BasicEntityShip> remaining = new ArrayList<>();
                for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                    BasicEntityShip member = slot == 1 ? fixture.ship : fixture.newShip();
                    fixture.member(0, slot, member);
                    fixture.capa.setShipSelected(0, slot, true);
                    member.setStateMinor(ID.M.FormatType, 2);
                    member.setStateMinor(ID.M.FormatPos, slot);
                    if (slot != 1) remaining.add(member);
                    helper.assertTrue(member.getStateMinor(ID.M.FormatType) == 2
                                    && member.getStateMinor(ID.M.FormatPos) == slot,
                            "Loaded formation fixture must retain each member's position");
                }
                fixture.capa.setFormatID(0, 2);
                ShipNameTagHelper.refresh(fixture.ship);
                helper.assertTrue(fixture.ship.unitNames.equals(List.of("Removed Fleet"))
                                && ServerDataManager.getShipByUID(fixture.ship.getShipUID()) == fixture.ship
                                && fixture.capa.getFormatID(0) == 2 && remaining.size() == 5,
                        "Loaded uid lookup, name and six-member formation fixture");

                invoke(fixture.removePacket(0, 1, fixture.ship.getShipUID()), "handleRemoveTeamShip", fixture.player);

                helper.assertTrue(fixture.ship.getStateMinor(ID.M.FormatType) == 0
                                && fixture.ship.getStateMinor(ID.M.FormatPos) == 0 && fixture.ship.unitNames.isEmpty()
                                && fixture.capa.getTeamMember(0, 1) == 0 && fixture.capa.getTeamSID(0, 1) == 0
                                && !fixture.capa.isShipSelected(0, 1) && fixture.capa.getFormatID(0) == 0,
                        "Removed loaded member must lose formation and team name even with five members left");
                for (BasicEntityShip member : remaining) {
                    int slot = member.getStateMinor(ID.M.FormatPos);
                    helper.assertTrue(member.getStateMinor(ID.M.FormatType) == 0
                                    && fixture.capa.getTeamMember(0, slot) == member.getShipUID()
                                    && fixture.capa.isShipSelected(0, slot),
                            "Remaining members must keep their original slots and selections without formation buffs");
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void guiRemoveOneMembershipKeepsOtherFleetAndName(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.openFormation();
                fixture.member(0, 1, fixture.ship);
                fixture.member(4, 3, fixture.ship);
                fixture.capa.setShipSelected(4, 3, true);
                fixture.capa.setFormatID(4, 3);
                fixture.capa.setUnitName(0, "First Fleet");
                fixture.capa.setUnitName(4, "Fifth Fleet");
                ShipNameTagHelper.refresh(fixture.ship);
                helper.assertTrue(fixture.ship.unitNames.equals(List.of("First Fleet", "Fifth Fleet"))
                                && fixture.capa.isShipSelected(4, 3) && fixture.capa.getFormatID(4) == 3,
                        "Multiple fleet membership fixture");

                invoke(fixture.removePacket(0, 1, fixture.ship.getShipUID()), "handleRemoveTeamShip", fixture.player);

                helper.assertTrue(fixture.capa.getTeamMember(0, 1) == 0
                                && fixture.capa.getTeamMember(4, 3) == fixture.ship.getShipUID()
                                && fixture.capa.getTeamSID(4, 3) == fixture.ship.getId()
                                && fixture.capa.isShipSelected(4, 3) && fixture.capa.getFormatID(4) == 3
                                && fixture.ship.unitNames.equals(List.of("Fifth Fleet")),
                        "Only the requested membership and its derived name must be removed");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_name_tag")
    public static void guiRemoveRejectsStaleUidInvalidPayloadAndMissingAuthority(GameTestHelper helper) {
        ready(helper, () -> {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.openFormation();
                fixture.member(0, 1, fixture.ship);
                fixture.capa.setTeamMember(0, 0, 0);
                fixture.capa.setTeamMember(0, 2, -1);
                fixture.capa.setShipSelected(0, 1, true);
                fixture.capa.setFormatID(0, 2);
                helper.assertTrue(fixture.capa.isShipSelected(0, 1) && fixture.capa.getFormatID(0) == 2
                                && fixture.capa.getTeamMember(0, 0) == 0 && fixture.capa.getTeamMember(0, 2) == -1,
                        "Rejection fixture must begin with a selected member and formation");
                int uid = fixture.ship.getShipUID();
                int[][] invalid = {
                        {fixture.player.getId(), 0, 1, uid + 1},
                        {fixture.player.getId(), -1, 1, uid},
                        {fixture.player.getId(), CapaTeitoku.TEAM_NUM, 1, uid},
                        {fixture.player.getId(), 0, -1, uid},
                        {fixture.player.getId(), 0, CapaTeitoku.SLOT_NUM, uid},
                        {fixture.player.getId(), 0, 0, 0},
                        {fixture.player.getId(), 0, 2, -1},
                        {}, {fixture.player.getId()}, {fixture.player.getId(), 0},
                        {fixture.player.getId(), 0, 1}, {fixture.player.getId(), 0, 1, uid, 1}
                };
                for (int[] values : invalid) {
                    fixture.assertRemoveRejected(new C2SGUIInputPacket(C2SGUIInputPacket.RemoveTeamShip, values));
                }
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                fixture.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                helper.assertTrue(fixture.player.getMainHandItem().isEmpty()
                                && fixture.player.getOffhandItem().isEmpty(), "Missing pointer fixture");
                fixture.assertRemoveRejected(fixture.removePacket(0, 1, uid));
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));
                fixture.player.closeContainer();
                helper.assertTrue(!(fixture.player.containerMenu instanceof ContainerFormation)
                                && fixture.player.getMainHandItem().is(ModItems.POINTER.get()),
                        "Missing formation menu fixture must still hold the pointer");
                fixture.assertRemoveRejected(fixture.removePacket(0, 1, uid));
            }
            helper.succeed();
        });
    }

    private static void ready(GameTestHelper helper, Runnable verification) {
        GameTestEntities.whenPositionsTicking(helper, verification, POSITION);
    }

    private static void invoke(C2SGUIInputPacket packet, String name, ServerPlayer player) {
        try {
            Method method = C2SGUIInputPacket.class.getDeclaredMethod(name, ServerPlayer.class);
            method.setAccessible(true);
            method.invoke(packet, player);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("GUI handler failed", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot invoke GUI handler", error);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final int ownerUid = NEXT_UID.getAndAdd(10);
        private final FakePlayer player;
        private final CapaTeitoku capa;
        private final BasicEntityShip ship;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.entities = GameTestEntities.open(helper);
            this.player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "name_tag_owner"));
            this.capa = player.getCapability(CapaTeitokuProvider.CAPABILITY)
                    .orElseThrow(() -> new IllegalStateException("Owner capability fixture"));
            capa.setPlayerUID(ownerUid);
            player.moveTo(helper.absoluteVec(POSITION));
            helper.getLevel().addNewPlayer(player);
            GameTestEntities.assertRegistered(helper, player);
            this.ship = newShip();
            helper.assertTrue(ship.getOwner() == player && ship.getPlayerUID() == capa.getPlayerUID(),
                    "Ship must resolve the actual owner capability");
        }

        private BasicEntityShip newShip() {
            BasicEntityShip result = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            helper.assertTrue(result != null, "Ship must be constructible");
            result.setTame(true);
            result.setOwnerUUID(player.getUUID());
            result.setPlayerUID(ownerUid);
            result.setStateMinor(ID.M.ShipUID, NEXT_UID.getAndIncrement());
            result.setStateMinor(ID.M.NumGrudge, 10000);
            result.setStateFlag(ID.F.NoFuel, false);
            result.setNoAi(true);
            result.setNoGravity(true);
            result.setInvulnerable(true);
            result.setHealth(result.getMaxHealth());
            result.moveTo(helper.absoluteVec(POSITION));
            helper.assertTrue(helper.getLevel().addFreshEntity(result), "Ship must enter level");
            GameTestEntities.assertRegistered(helper, result);
            helper.assertTrue(result.getShipUID() > 0 && result.getGrudge() > 0
                            && !result.getStateFlag(ID.F.NoFuel) && result.isNoGravity() && result.isInvulnerable(),
                    "Ship uid, fuel and physical protection fixture");
            return result;
        }

        private void member(int team, int slot, BasicEntityShip member) {
            capa.setTeamMember(team, slot, member.getShipUID());
            capa.setTeamSID(team, slot, member.getId());
            helper.assertTrue(capa.getTeamMember(team, slot) == member.getShipUID()
                            && capa.getTeamSID(team, slot) == member.getId(), "Team membership fixture");
        }

        private void openFormation() {
            ServerDataManager.updateShipID(ship);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));
            invoke(new C2SGUIInputPacket(C2SGUIInputPacket.OpenItemGUI, new int[]{player.getId(), 0, 0}),
                    "handleOpenItemGUI", player);
            helper.assertTrue(player.containerMenu instanceof ContainerFormation
                            && player.getMainHandItem().is(ModItems.POINTER.get())
                            && ServerDataManager.getShipByUID(ship.getShipUID()) == ship,
                    "Removal requires the actual formation menu, a held pointer and a registered loaded ship");
        }

        private C2SGUIInputPacket removePacket(int team, int slot, int uid) {
            return new C2SGUIInputPacket(C2SGUIInputPacket.RemoveTeamShip, new int[]{player.getId(), team, slot, uid});
        }

        private void assertRemoveRejected(C2SGUIInputPacket packet) {
            CompoundTag before = capa.serializeNBT();
            int[][] entityIds = new int[CapaTeitoku.TEAM_NUM][CapaTeitoku.SLOT_NUM];
            for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                    entityIds[team][slot] = capa.getTeamSID(team, slot);
                }
            }
            invoke(packet, "handleRemoveTeamShip", player);
            helper.assertTrue(capa.serializeNBT().equals(before), "Rejected removal must not change persistent state");
            for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                    helper.assertTrue(capa.getTeamSID(team, slot) == entityIds[team][slot],
                            "Rejected removal must not change runtime entity ids");
                }
            }
        }

        @Override
        public void close() {
            entities.close();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }
}
