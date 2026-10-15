package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.PacketHelper;
import com.lulan.shincolle.utility.ShipNameTagHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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

        @Override
        public void close() {
            entities.close();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }
}
