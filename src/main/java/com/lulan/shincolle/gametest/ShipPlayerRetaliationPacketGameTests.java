package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.network.S2CGUISyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/** Pins matching-version writer/framing offsets, not client handler application or a handshake. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPlayerRetaliationPacketGameTests {
    private static final int SENTINEL = 0x12345678;
    private static final int[] FLAGS = {
            ID.F.CanFloatUp, ID.F.IsMarried, ID.F.NoFuel, ID.F.UseMelee,
            ID.F.UseAmmoLight, ID.F.UseAmmoHeavy, ID.F.UseAirLight, ID.F.UseAirHeavy,
            ID.F.UseRingEffect, ID.F.OnSightChase, ID.F.PVPFirst, ID.F.AntiAir,
            ID.F.AntiSS, ID.F.PassiveAI, ID.F.TimeKeeper, ID.F.PickItem,
            ID.F.CanPickItem, ID.F.ShowHeldItem, ID.F.AutoPump, ID.F.RetaliatePlayer
    };
    private static final int[] PREFIX = {
            ID.M.ShipLevel, ID.M.Kills, ID.M.ExpCurrent, ID.M.NumAmmoLight, ID.M.NumAmmoHeavy,
            ID.M.NumGrudge, ID.M.NumAirLight, ID.M.NumAirHeavy, ID.M.FollowMin, ID.M.FollowMax,
            ID.M.FleeHP, ID.M.GuardX, ID.M.GuardY, ID.M.GuardZ, ID.M.GuardDim, ID.M.GuardID, ID.M.GuardType
    };
    private static final int[] SUFFIX = {
            ID.M.PlayerUID, ID.M.ShipUID, ID.M.PlayerEID, ID.M.FormatType, ID.M.FormatPos,
            ID.M.Morale, ID.M.DrumState, ID.M.LevelChunkLoader, ID.M.LevelFlare, ID.M.LevelSearchlight,
            ID.M.WpStay, ID.M.UseCombatRation
    };

    private ShipPlayerRetaliationPacketGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void playerMiscAndFullWritersPreservePermissionAndFollowingOffsets(GameTestHelper helper) {
        CapaTeitoku capa = new CapaTeitoku();
        capa.setHasRing(true);
        capa.setRingActive(false);
        capa.setRingFlying(true);
        capa.setSelectTeam(3);
        capa.setMarriageNum(7);
        capa.setPlayerUID(732401);
        capa.setTeamCooldown(119);
        for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
            capa.setRetaliatePlayer(team, team % 2 == 0);
            capa.setFormatID(team, team % 5);
        }
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            capa.setTeamMember(3, slot, 732410 + slot);
            capa.setTeamSID(3, slot, 100 + slot);
            capa.setShipSelected(3, slot, slot % 2 == 0);
        }
        for (boolean full : new boolean[]{false, true}) {
            FriendlyByteBuf wire = buffer();
            FriendlyByteBuf encoded = buffer();
            FriendlyByteBuf payload = null;
            try {
                S2CGUISyncPacket packet = full ? S2CGUISyncPacket.syncPlayerFull(capa)
                        : S2CGUISyncPacket.syncPlayerMisc(capa);
                packet.encode(wire);
                byte[] expected = bytes(wire);
                wire.writeInt(SENTINEL);
                new S2CGUISyncPacket(wire).encode(encoded);
                helper.assertTrue(wire.readInt() == SENTINEL && !wire.isReadable()
                                && Arrays.equals(expected, bytes(encoded)), "GUI outer codec changed bytes/framing");
                helper.assertTrue(encoded.readByte() == (full ? S2CGUISyncPacket.SyncPlayerProp
                        : S2CGUISyncPacket.SyncPlayerProp_Misc), "Wrong GUI packet type");
                int length = encoded.readVarInt();
                helper.assertTrue(length == (full ? 119 : 29), "Wrong GUI payload length: " + length);
                payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded.readByteArray(length)));
                helper.assertTrue(payload.readBoolean() && !payload.readBoolean() && payload.readBoolean()
                                && payload.readBoolean() && payload.readInt() == 3 && payload.readInt() == 7
                                && payload.readInt() == 732401 && payload.readInt() == 119,
                        "GUI misc prefix changed order");
                for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                    helper.assertTrue(payload.readBoolean() == capa.getRetaliatePlayer(team),
                            "Wrong fleet permission offset: " + team);
                }
                if (full) {
                    for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                        helper.assertTrue(payload.readInt() == capa.getFormatID(team), "Formation offset shifted");
                    }
                    for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                        helper.assertTrue(payload.readInt() == capa.getTeamMember(3, slot)
                                        && payload.readInt() == capa.getTeamSID(3, slot)
                                        && payload.readBoolean() == capa.isShipSelected(3, slot),
                                "Team UID/EID/selection offset shifted at " + slot);
                    }
                }
                helper.assertTrue(!payload.isReadable() && !encoded.isReadable(), "Unexpected GUI trailing bytes");
            } finally {
                if (payload != null) payload.release();
                wire.release();
                encoded.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAllMiscAndFlagsWritersPreservePermissionTextureAndHeldItemOffsets(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        ship.setPlayerUID(732421);
        ship.setShipUID(732422);
        ship.setTextureID(1);
        ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.APPLE, 3));
        ship.getCapaShipInventory().setStackInSlot(23, new ItemStack(Items.STICK, 5));
        helper.assertTrue(ship.getTextureID() == 1 && ship.getMainHandItemShip().is(Items.APPLE)
                        && ship.getMainHandItemShip().getCount() == 3 && ship.getOffHandItemShip().is(Items.STICK)
                        && ship.getOffHandItemShip().getCount() == 5, "Packet sentinels must be applied");
        for (boolean permission : new boolean[]{false, true}) {
            ship.setStateFlag(ID.F.RetaliatePlayer, permission);
            for (boolean all : new boolean[]{false, true}) {
                FriendlyByteBuf wire = buffer();
                FriendlyByteBuf encoded = buffer();
                FriendlyByteBuf payload = null;
                try {
                    S2CEntitySyncPacket packet = all ? S2CEntitySyncPacket.syncAllMisc(ship)
                            : S2CEntitySyncPacket.syncFlags(ship);
                    packet.encode(wire);
                    byte[] expected = bytes(wire);
                    wire.writeInt(SENTINEL);
                    new S2CEntitySyncPacket(wire).encode(encoded);
                    helper.assertTrue(wire.readInt() == SENTINEL && !wire.isReadable()
                                    && Arrays.equals(expected, bytes(encoded)), "Entity outer codec changed framing");
                    helper.assertTrue(encoded.readByte() == (all ? S2CEntitySyncPacket.SyncShip_AllMisc
                                    : S2CEntitySyncPacket.SyncShip_Flag)
                                    && encoded.readInt() == ship.getId(), "Wrong entity envelope");
                    int length = encoded.readVarInt();
                    payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded.readByteArray(length)));
                    if (all) {
                        for (int id : PREFIX) {
                            helper.assertTrue(payload.readInt() == ship.getStateMinor(id), "Wrong minor prefix " + id);
                        }
                        helper.assertTrue(payload.readBoolean() == ship.hasGuardDestination()
                                        && payload.readBoolean() == ship.getStateFlag(ID.F.CanFollow),
                                "Guard metadata offset shifted");
                        boolean dimension = payload.readBoolean();
                        helper.assertTrue(dimension == (ship.getGuardedDimension() != null), "Wrong guard dimension bit");
                        if (dimension) {
                            helper.assertTrue(payload.readResourceLocation().equals(ship.getGuardedDimension().location()),
                                    "Guard dimension offset shifted");
                        }
                        for (int id : SUFFIX) {
                            helper.assertTrue(payload.readInt() == ship.getStateMinor(id), "Wrong minor suffix " + id);
                        }
                        helper.assertTrue(payload.readInt() == ship.getStateTimer(ID.T.CraneTime), "Wrong crane timer");
                        for (int id : new int[]{ID.M.SensBody, ID.M.NumState, ID.M.Task, ID.M.TaskSide}) {
                            helper.assertTrue(payload.readInt() == ship.getStateMinor(id), "Wrong remaining minor " + id);
                        }
                        for (int id : new int[]{ID.S.State, ID.S.HPState, ID.S.Emotion, ID.S.Emotion4, ID.S.Phase}) {
                            helper.assertTrue(payload.readInt() == ship.getStateEmotion(id), "Wrong emotion " + id);
                        }
                    }
                    for (int id : FLAGS) {
                        helper.assertTrue(payload.readBoolean() == ship.getStateFlag(id), "Wrong flag offset " + id);
                    }
                    if (all) {
                        helper.assertTrue(payload.readInt() == ship.getTextureID(), "Permission shifted texture offset");
                        helper.assertTrue(ItemStack.matches(payload.readItem(), ship.getMainHandItemShip())
                                        && ItemStack.matches(payload.readItem(), ship.getOffHandItemShip()),
                                "Permission shifted held-item offsets");
                    }
                    helper.assertTrue(!payload.isReadable() && !encoded.isReadable(), "Unexpected entity trailing bytes");
                } finally {
                    if (payload != null) payload.release();
                    wire.release();
                    encoded.release();
                }
            }
        }
        helper.succeed();
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static byte[] bytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }
}
