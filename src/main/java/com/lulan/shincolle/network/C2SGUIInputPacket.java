package com.lulan.shincolle.network;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerFormation;
import com.lulan.shincolle.client.gui.inventory.ContainerCrane;
import com.lulan.shincolle.client.gui.inventory.ContainerLargeShipyard;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;
import com.lulan.shincolle.client.gui.inventory.ContainerVolCore;
import com.lulan.shincolle.crafting.SmallRecipes;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileEntityVolCore;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.lulan.shincolle.utility.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client-to-Server GUI input packet.
 * <p>
 * Sent when the player clicks buttons or submits text in ShinColle GUIs.
 * <p>
 * Ported from 1.10.2 C2SGUIPackets.
 */
public class C2SGUIInputPacket {
    private static final int MAX_VALUES = 7;
    private static final int MAX_STRING_LENGTH = 128;
    // simple GUI button clicks
    public static final byte ShipBtn = 0;

    // ========== Packet IDs ==========
    public static final byte TileBtn = 1;
    // pointer GUI commands
    public static final byte AddTeam = 20;
    public static final byte AttackTarget = 21;
    public static final byte OpenShipGUI = 22;
    public static final byte SetSitting = 23;
    public static final byte SyncPlayerItem = 24;
    public static final byte GuardEntity = 25;
    public static final byte ClearTeam = 26;
    public static final byte SetShipTeamID = 27;
    public static final byte SetMove = 28;
    public static final byte SetSelect = 29;
    public static final byte SetTarClass = 30;
    public static final byte SetFormation = 31;
    public static final byte OpenItemGUI = 32;
    public static final byte SwapShip = 33;
    public static final byte HitHeight = 35;
    public static final byte SetUnitName = 36;
    // op tool
    public static final byte SetUnatkClass = 50;
    public static final byte ShowUnatkClass = 51;
    // desk GUI
    public static final byte Desk_Create = 70;
    public static final byte Desk_Rename = 71;
    public static final byte Desk_Ally = 72;
    public static final byte Desk_Break = 73;
    public static final byte Desk_Ban = 74;
    public static final byte Desk_Unban = 75;
    public static final byte Desk_Disband = 76;
    public static final byte Desk_FuncSync = 77;
    private static final int LARGE_SHIPYARD_MAT_BUILD_MAX = 1000;
    private static final double POINTER_RANGE_SQR = 64D * 64D;
    private static final String TARGET_CLASS_RATE_TAG = "ShinColleTargetClassTick";
    private static final long TARGET_CLASS_RATE_TICKS = 5L;

    // ========== Fields ==========
    private final byte type;
    private final int[] values;
    private final String stringData;

    // ========== Constructors ==========

    public C2SGUIInputPacket(byte type, int[] values) {
        this(type, values, null);
    }

    public C2SGUIInputPacket(byte type, int[] values, String stringData) {
        this.type = type;
        this.values = values != null ? values : new int[0];
        this.stringData = stringData;
    }

    /**
     * Decoder constructor
     */
    public C2SGUIInputPacket(FriendlyByteBuf buf) {
        this.type = buf.readByte();
        this.values = PacketHelper.readIntArray(buf, MAX_VALUES);
        this.stringData = PacketHelper.readNullableString(buf, MAX_STRING_LENGTH);
    }

    private static void handleSmallShipyardBtn(TileEntitySmallShipyard tile, int buttonId, int value) {
        switch (buttonId) {
            case ID.B.Shipyard_Type:
                tile.setBuildType(Math.max(0, Math.min(value, 4)));
                break;
            case ID.B.Shipyard_SelectMat:
                tile.setSelectMat(Math.max(0, Math.min(value, 3)));
                break;
            case ID.B.Shipyard_INCDEC:
                int matIndex = Math.max(0, Math.min(tile.getSelectMat(), 3));
                int currentBuild = Math.max(0, tile.getMatBuild(matIndex));
                int stock = Math.max(0, tile.getMatStock(matIndex));
                int maxBuild = Math.min(SmallRecipes.MAX_MATERIAL, stock);
                int target = Math.max(0, Math.min(currentBuild + value, maxBuild));
                tile.setMatBuild(matIndex, target);
                break;
        }
    }

    // ========== Handler ==========

    private static void handleLargeShipyardBtn(TileMultiGrudgeHeavy tile, int buttonId, int value) {
        switch (buttonId) {
            case ID.B.Shipyard_Type:
                tile.setBuildType(Math.max(0, Math.min(value, 4)));
                break;
            case ID.B.Shipyard_InvMode:
                tile.setInvMode(value != 0 ? 1 : 0);
                break;
            case ID.B.Shipyard_SelectMat:
                tile.setSelectMat(Math.max(0, Math.min(value, 3)));
                break;
            case ID.B.Shipyard_INCDEC:
                int matIndex = Math.max(0, Math.min(tile.getSelectMat(), 3));
                applyLargeShipyardMaterialDelta(tile, matIndex, value);
                break;
        }
    }

    // ========== Handler Methods ==========

    private static void applyLargeShipyardMaterialDelta(TileMultiGrudgeHeavy tile, int matIndex, int delta) {
        if (delta == 0)
            return;

        int currentBuild = Math.max(0, tile.getMatBuild(matIndex));
        int stock = Math.max(0, tile.getMatStock(matIndex));
        int maxBuild = Math.min(LARGE_SHIPYARD_MAT_BUILD_MAX, stock);
        int target = currentBuild + delta;

        target = Math.max(0, Math.min(target, maxBuild));
        tile.setMatBuild(matIndex, target);
    }

    private static void handleCraneBtn(TileEntityCrane tile, int buttonId, int value) {
        switch (buttonId) {
            case ID.B.Crane_Power:
                tile.setActive(value != 0);
                break;
            case ID.B.Crane_Mode:
                tile.setCraneMode(value);
                break;
            case ID.B.Crane_Meta:
                tile.setCheckMetadata(value != 0);
                break;
            case ID.B.Crane_Dict:
                tile.setCheckDict(value != 0);
                break;
            case ID.B.Crane_Load:
                tile.setEnabLoad(value != 0);
                break;
            case ID.B.Crane_Unload:
                tile.setEnabUnload(value != 0);
                break;
            case ID.B.Crane_Nbt:
                tile.setCheckNbt(value != 0);
                break;
            case ID.B.Crane_Red:
                // [PORT] 1.10.2 -> 1.20.1: preserve explicit tri-state values from GUI packet.
                tile.setRedSignalMode(value);
                break;
            case ID.B.Crane_Liquid:
                tile.setLiquidMode(value);
                break;
            case ID.B.Crane_Energy:
                tile.setEnergyMode(value);
                break;
        }
    }

    private static void handleVolCoreBtn(TileEntityVolCore tile, int buttonId, int value) {
        if (buttonId == ID.B.VolCore_Power) {
            tile.setBtnActive(!tile.isBtnActive());
        }
    }

    /**
     * Apply a GUI button action to a ship entity.
     * Ported from PacketHelper.setEntityByGUI().
     *
     * @param ship   the target ship
     * @param button the button ID (from ID.B)
     * @param value  the new value
     */
    public static void applyShipGUIButton(BasicEntityShip ship, int button, int value) {
        boolean boolVal = (value != 0);

        switch (button) {
            case ID.B.ShipInv_Melee:
                ship.setStateFlag(ID.F.UseMelee, boolVal);
                break;
            case ID.B.ShipInv_AmmoLight:
                ship.setStateFlag(ID.F.UseAmmoLight, boolVal);
                break;
            case ID.B.ShipInv_AmmoHeavy:
                ship.setStateFlag(ID.F.UseAmmoHeavy, boolVal);
                break;
            case ID.B.ShipInv_AirLight:
                ship.setStateFlag(ID.F.UseAirLight, boolVal);
                break;
            case ID.B.ShipInv_AirHeavy:
                ship.setStateFlag(ID.F.UseAirHeavy, boolVal);
                break;
            case ID.B.ShipInv_FollowMin:
                value = Mth.clamp(value, 1, 31);
                ship.setStateMinor(ID.M.FollowMin, value);
                if (ship.getStateMinor(ID.M.FollowMin) >= ship.getStateMinor(ID.M.FollowMax)) {
                    ship.setStateMinor(ID.M.FollowMax, value + 1);
                }
                break;
            case ID.B.ShipInv_FollowMax:
                value = Mth.clamp(value, 2, 32);
                ship.setStateMinor(ID.M.FollowMax, value);
                if (ship.getStateMinor(ID.M.FollowMax) <= ship.getStateMinor(ID.M.FollowMin)) {
                    ship.setStateMinor(ID.M.FollowMin, value - 1);
                }
                break;
            case ID.B.ShipInv_FleeHP:
                ship.setStateMinor(ID.M.FleeHP, Mth.clamp(value, 0, 100));
                break;
            case ID.B.ShipInv_TarAI:
                ship.setStateFlag(ID.F.PassiveAI, boolVal);
                break;
            case ID.B.ShipInv_AuraEffect:
                ship.setStateFlag(ID.F.UseRingEffect, boolVal);
                break;
            case ID.B.ShipInv_OnSightAI:
                ship.setStateFlag(ID.F.OnSightChase, boolVal);
                break;
            case ID.B.ShipInv_PVPAI:
                ship.setStateFlag(ID.F.PVPFirst, boolVal);
                break;
            case ID.B.ShipInv_AAAI:
                ship.setStateFlag(ID.F.AntiAir, boolVal);
                break;
            case ID.B.ShipInv_ASMAI:
                ship.setStateFlag(ID.F.AntiSS, boolVal);
                break;
            case ID.B.ShipInv_TIMEKEEPAI:
                ship.setStateFlag(ID.F.TimeKeeper, boolVal);
                break;
            case ID.B.ShipInv_InvPage:
                ship.getCapaShipInventory().setInventoryPage(
                        Mth.clamp(value, 0, ContainerShipInventory.INV_PAGES - 1));
                break;
            case ID.B.ShipInv_PickitemAI:
                ship.setStateFlag(ID.F.PickItem, boolVal);
                break;
            case ID.B.ShipInv_WpStay:
                ship.setStateMinor(ID.M.WpStay, Mth.clamp(value, 0, 16));
                break;
            case ID.B.ShipInv_ShowHeld:
                ship.setStateFlag(ID.F.ShowHeldItem, boolVal);
                break;
            case ID.B.ShipInv_AutoCR:
                ship.setStateMinor(ID.M.UseCombatRation, Mth.clamp(value, 1, 4));
                break;
            case ID.B.ShipInv_AutoPump:
                ship.setStateFlag(ID.F.AutoPump, boolVal);
                break;
            case ID.B.ShipInv_Task:
                ship.setStateMinor(ID.M.Task, Mth.clamp(value, 0, 4));
                break;
            case ID.B.ShipInv_TaskSide:
                // Direction bits 0..17 plus crafting options 18 and 20.
                ship.setStateMinor(ID.M.TaskSide, value & 0x17FFFF);
                break;
            case ID.B.ShipInv_NoFuel:
                // Server-owned derived state; clients may not force it.
                break;
            default:
                // model state toggles and other buttons
                if (button >= ID.B.ShipInv_ModelState01 && button <= ID.B.ShipInv_ModelState01 + 15
                        && button - ID.B.ShipInv_ModelState01 < Mth.clamp(
                                ship.getStateMinor(ID.M.NumState), 0, 16)) {
                    int bit = button - ID.B.ShipInv_ModelState01;
                    int state = ship.getStateEmotion(ID.S.State);
                    ship.setStateEmotion(ID.S.State, state ^ (1 << bit), false);
                } else {
                    LogHelper.debug("C2SGUIInputPacket: unknown ship button=" + button + " value=" + value);
                }
                break;
        }
    }

    private static void syncDeskTeamData(ServerPlayer player, CapaTeitoku capa) {
        if (player == null || capa == null) {
            return;
        }

        int teamId = capa.getPlayerUID();
        TeamData myTeamData = teamId > 0 ? ServerDataManager.getTeamData(teamId) : null;
        ModNetworking.sendToPlayer(
                S2CGUISyncPacket.syncTeamData(capa, myTeamData, ServerDataManager.getAllTeamWorldData()),
                player);
    }

    /**
     * Find a team ID by the team leader's player name.
     *
     * @param name the leader name to search for
     * @return the team ID, or -1 if not found
     */
    private static int findTeamByLeaderName(String name) {
        if (name == null || name.isEmpty())
            return -1;
        HashMap<Integer, TeamData> allTeams = ServerDataManager.getAllTeamWorldData();
        if (allTeams == null)
            return -1;
        for (var entry : allTeams.entrySet()) {
            if (name.equals(entry.getValue().getTeamLeaderName())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private static int findTeamSlotByUID(CapaTeitoku capa, int teamId, int shipUid) {
        if (shipUid <= 0) {
            return -1;
        }
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            if (capa.getTeamMember(teamId, i) == shipUid) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstEmptySlot(CapaTeitoku capa, int teamId) {
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            if (capa.getTeamMember(teamId, i) <= 0) {
                return i;
            }
        }
        return -1;
    }

    private static BasicEntityShip resolveTeamShip(ServerLevel level, CapaTeitoku capa, int teamId, int slot) {
        int shipUid = capa.getTeamMember(teamId, slot);
        if (shipUid <= 0) {
            return null;
        }

        int entityId = capa.getTeamSID(teamId, slot);
        if (entityId > 0) {
            Entity shipEnt = level.getEntity(entityId);
            if (shipEnt instanceof BasicEntityShip ship
                    && ship.getStateMinor(ID.M.ShipUID) == shipUid
                    && ship.getPlayerUID() == capa.getPlayerUID()) {
                return ship;
            }
        }

        // Relink from the persistent ship UID. Entity IDs are transient and
        // can be reused after reload, chunk unload, or dimension travel.
        BasicEntityShip ship = ServerDataManager.getShipByUID(shipUid);
        if (ship != null && ship.level() == level && ship.getPlayerUID() == capa.getPlayerUID()) {
            capa.setTeamSID(teamId, slot, ship.getId());
            return ship;
        }
        capa.setTeamSID(teamId, slot, -1);
        return null;
    }

    /** Particle type for the red "this ship could not take the order" mark. */
    private static final byte OutOfFuelParticle = 30;

    /**
     * Mark ships that were dropped from an order because they are out of fuel, and mark the
     * destination they could not take.
     * <p>
     * The NoFuel gate is not in 1.10.2 -- FormationHelper.applyTeamAttack and applyTeamMove
     * never look at fuel. It stays (user decision, 2026-08-26) because a ship with no fuel
     * genuinely cannot act, but silently dropping the order reads as "the pointer is
     * broken". Only fuel is marked; the other exclusions (out of range, wrong level,
     * formation not satisfied) are left alone so the mark keeps one meaning.
     */
    private static void markOutOfFuel(ServerPlayer player, List<BasicEntityShip> outOfFuel,
                                      double destX, double destY, double destZ) {
        if (outOfFuel.isEmpty()) {
            return;
        }

        for (BasicEntityShip ship : outOfFuel) {
            ModNetworking.sendToAllTracking(
                    new S2CSpawnParticlePacket(OutOfFuelParticle, ship.getId(),
                            positionPayload(ship.getX(), ship.getY() + ship.getBbHeight() * 0.5D,
                                    ship.getZ())),
                    ship);
        }

        ModNetworking.sendToPlayer(
                new S2CSpawnParticlePacket(OutOfFuelParticle, player.getId(),
                        positionPayload(destX, destY, destZ)),
                player);
    }

    private static byte[] positionPayload(double x, double y, double z) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(24));
        try {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            return payload;
        } finally {
            buf.release();
        }
    }

    private static boolean includesSlot(CapaTeitoku capa, int teamId, int slot, int mode) {
        return mode == PointerItem.MODE_FORMATION || capa.isShipSelected(teamId, slot);
    }

    private static void selectFirstTeamMemberIfNeeded(CapaTeitoku capa, int teamId) {
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            if (capa.isShipSelected(teamId, slot) && capa.getTeamMember(teamId, slot) > 0) {
                return;
            }
        }
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            if (capa.getTeamMember(teamId, slot) > 0) {
                capa.setShipSelected(teamId, slot, true);
                return;
            }
        }
    }

    private static boolean canManageShip(ServerPlayer player, BasicEntityShip ship) {
        return ship.isAlive()
                && ship.level() == player.level()
                && (TeamHelper.checkSameOwner(player, ship) || ship.isOwnedBy(player));
    }

    private static boolean hasPointerInHand(ServerPlayer player) {
        return player.getMainHandItem().getItem() == ModItems.POINTER.get()
                || player.getOffhandItem().getItem() == ModItems.POINTER.get();
    }

    private static boolean isWithinPointerRange(ServerPlayer player, Entity target) {
        return target != null && target.level() == player.level()
                && player.distanceToSqr(target) <= POINTER_RANGE_SQR;
    }

    private static boolean isWithinPointerRange(ServerPlayer player, BlockPos target) {
        return player.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D) <= POINTER_RANGE_SQR;
    }

    /**
     * Encode
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type);
        PacketHelper.writeIntArray(buf, values);
        PacketHelper.writeNullableString(buf, stringData);
    }

    // ========== Ship GUI Button Handler ==========

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null)
                return;

            try {
                switch (type) {
                    case ShipBtn:
                        handleShipBtn(sender);
                        break;
                    case TileBtn:
                        handleTileBtn(sender);
                        break;
                    case OpenShipGUI:
                        handleOpenShipGUI(sender);
                        break;
                    case SetSitting:
                        handleSetSitting(sender);
                        break;
                    case HitHeight:
                        handleHitHeight(sender);
                        break;
                    case AddTeam:
                        handleAddTeam(sender);
                        break;
                    case AttackTarget:
                        handleAttackTarget(sender);
                        break;
                    case SyncPlayerItem:
                        handleSyncPlayerItem(sender);
                        break;
                    case GuardEntity:
                        handleGuardEntity(sender);
                        break;
                    case ClearTeam:
                        handleClearTeam(sender);
                        break;
                    case SetShipTeamID:
                        handleSetShipTeamID(sender);
                        break;
                    case SetMove:
                        handleSetMove(sender);
                        break;
                    case SetSelect:
                        handleSetSelect(sender);
                        break;
                    case SetTarClass:
                        handleSetTarClass(sender);
                        break;
                    case SetFormation:
                        handleSetFormation(sender);
                        break;
                    case OpenItemGUI:
                        handleOpenItemGUI(sender);
                        break;
                    case SwapShip:
                        handleSwapShip(sender);
                        break;
                    case SetUnitName:
                        handleSetUnitName(sender);
                        break;
                    case SetUnatkClass:
                        handleSetUnatkClass(sender);
                        break;
                    case ShowUnatkClass:
                        handleShowUnatkClass(sender);
                        break;
                    case Desk_Create:
                        handleDeskCreate(sender);
                        break;
                    case Desk_Rename:
                        handleDeskRename(sender);
                        break;
                    case Desk_Ally:
                        handleDeskAlly(sender);
                        break;
                    case Desk_Break:
                        handleDeskBreak(sender);
                        break;
                    case Desk_Ban:
                        handleDeskBan(sender);
                        break;
                    case Desk_Unban:
                        handleDeskUnban(sender);
                        break;
                    case Desk_Disband:
                        handleDeskDisband(sender);
                        break;
                    case Desk_FuncSync:
                        handleDeskFuncSync(sender);
                        break;
                    default:
                        LogHelper.debug("C2SGUIInputPacket: unknown type=" + type);
                        break;
                }
            } catch (Exception e) {
                LogHelper.debug("C2SGUIInputPacket: handler error type=" + type
                        + " err=" + e.getMessage());
            }
        });
        ctx.setPacketHandled(true);
    }

    // ========== Pointer GUI Command Handlers ==========

    /**
     * Ship entity GUI button click.
     * values: 0:entity id, 1:(unused dim), 2:button id, 3:value
     */
    private void handleShipBtn(ServerPlayer player) {
        if (values.length < 4)
            return;

        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(values[0]);

        if (entity instanceof BasicEntityShip ship
                && canManageShip(player, ship)
                && player.containerMenu instanceof ContainerShipInventory menu
                && menu.getShip() == ship
                && menu.stillValid(player)) {
            applyShipGUIButton(ship, values[2], values[3]);
            if (values[2] == ID.B.ShipInv_InvPage) {
                menu.setInventoryPage(ship.getCapaShipInventory().getInventoryPage());
            }
            ship.sendSyncPacketAll();
            ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncAllMisc(ship), player);
        }
    }

    /**
     * Tile entity GUI button click.
     * values: 0:(unused dim), 1:x, 2:y, 3:z, 4:button id, 5:value
     */
    private void handleTileBtn(ServerPlayer player) {
        if (values.length < 6)
            return;
        BlockPos pos = new BlockPos(values[1], values[2], values[3]);

        // Verify player is near the tile entity
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0)
            return;

        net.minecraft.world.level.block.entity.BlockEntity be = player.serverLevel().getBlockEntity(pos);
        if (be == null)
            return;

        int buttonId = values[4];
        int buttonValue = values[5];

        if (be instanceof TileEntitySmallShipyard tile
                && player.containerMenu instanceof ContainerSmallShipyard menu
                && menu.getTile() == tile && menu.stillValid(player)) {
            handleSmallShipyardBtn(tile, buttonId, buttonValue);
        } else if (be instanceof TileMultiGrudgeHeavy tile
                && player.containerMenu instanceof ContainerLargeShipyard menu
                && menu.getTile() == tile && menu.stillValid(player)) {
            handleLargeShipyardBtn(tile, buttonId, buttonValue);
        } else if (be instanceof TileEntityCrane tile
                && player.containerMenu instanceof ContainerCrane menu
                && menu.getTile() == tile && tile.canUse(player) && menu.stillValid(player)) {
            handleCraneBtn(tile, buttonId, buttonValue);
        } else if (be instanceof TileEntityVolCore tile
                && player.containerMenu instanceof ContainerVolCore menu
                && menu.getTile() == tile && menu.stillValid(player)) {
            handleVolCoreBtn(tile, buttonId, buttonValue);
        }
    }

    /**
     * Open ship GUI.
     * values: 0:player eid, 1:(unused dim), 2:entity id
     */
    private void handleOpenShipGUI(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;

        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(values[2]);

        if (entity instanceof BasicEntityShip ship && canManageShip(player, ship)
                && isWithinPointerRange(player, ship)) {
            ship.openGUI(player);
        }
    }

    /**
     * Set ship sitting state (team-aware).
     * values: 0:player eid, 1:(unused dim), 2:mode, 3:entity id
     * <p>
     * In single mode: toggles sit for the clicked ship only.
     * In group/formation mode: toggles sit for all ships in the current team.
     * If the clicked ship is not in any team, toggles sit for that ship only.
     */
    private void handleSetSitting(ServerPlayer player) {
        if (values.length < 4 || !hasPointerInHand(player))
            return;

        ServerLevel level = player.serverLevel();
        int mode = values[2];
        Entity entity = level.getEntity(values[3]);

        if (!(entity instanceof BasicEntityShip clickedShip))
            return;
        if ((!TeamHelper.checkSameOwner(player, clickedShip) && !clickedShip.isOwnedBy(player))
                || !isWithinPointerRange(player, clickedShip))
            return;

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            boolean newSit = !clickedShip.isOrderedToSit();
            clickedShip.setEntitySit(newSit);
            clickedShip.setRiderAndMountSit();
            return;
        }

        int teamId = capa.getSelectTeam();
        int clickedUid = clickedShip.getStateMinor(ID.M.ShipUID);
        boolean inTeam = findTeamSlotByUID(capa, teamId, clickedUid) >= 0;

        if (!inTeam) {
            // A ship outside the selected team is always handled individually.
            boolean newSit = !clickedShip.isOrderedToSit();
            clickedShip.setEntitySit(newSit);
            clickedShip.setRiderAndMountSit();
        } else {
            // Formation mode includes the whole team. Single/group modes use the
            // persisted pointer selection restored from the legacy implementation.
            boolean newSit = !clickedShip.isOrderedToSit();
            for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
                if (!includesSlot(capa, teamId, i, mode)) {
                    continue;
                }
                BasicEntityShip ship = resolveTeamShip(level, capa, teamId, i);
                if (ship != null) {
                    ship.setEntitySit(newSit);
                    ship.setRiderAndMountSit();
                }
            }
        }
    }

    /**
     * Set hit height for targeting.
     * values: 0:player eid, 1:(unused dim), 2:entity id, 3:height, 4:angle
     */
    private void handleHitHeight(ServerPlayer player) {
        if (values.length < 5 || !hasPointerInHand(player))
            return;

        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(values[2]);

        if (entity instanceof BasicEntityShip ship && canManageShip(player, ship)
                && isWithinPointerRange(player, ship)) {
            ship.setStateMinor(ID.M.HitHeight, Mth.clamp(values[3], 0, 100));
            ship.setStateMinor(ID.M.HitAngle, Mth.clamp(values[4], 0, 359));
        }
    }

    /**
     * Add a ship entity to the player's currently selected team.
     * values: 0:player eid, 1:(unused dim), 2:entity id
     */
    private void handleAddTeam(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(values[2]);
        if (!(entity instanceof BasicEntityShip ship) || !TeamHelper.checkSameOwner(player, ship)
                || !isWithinPointerRange(player, ship)) {
            return;
        }

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }

        int teamId = capa.getSelectTeam();
        int shipUid = ship.getStateMinor(ID.M.ShipUID);
        int existingSlot = findTeamSlotByUID(capa, teamId, shipUid);

        if (existingSlot >= 0) {
            // [PORT] 1.10.2 -> 1.20.1: AddTeam acts as toggle; existing member is removed.
            capa.setTeamMember(teamId, existingSlot, 0);
            capa.setTeamSID(teamId, existingSlot, 0);
            capa.setShipSelected(teamId, existingSlot, false);
            ship.setStateMinor(ID.M.FormatType, 0);
            ship.setStateMinor(ID.M.FormatPos, 0);
        } else {
            int insertSlot = findFirstEmptySlot(capa, teamId);
            if (insertSlot < 0) {
                return;
            }

            capa.setTeamMember(teamId, insertSlot, shipUid);
            capa.setTeamSID(teamId, insertSlot, ship.getId());
            ship.setStateMinor(ID.M.FormatType, capa.getFormatID(teamId));
            ship.setStateMinor(ID.M.FormatPos, insertSlot);
        }

        selectFirstTeamMemberIfNeeded(capa, teamId);

        // [PORT] 1.10.2 -> 1.20.1: changing team members clears formation selection.
        capa.setFormatID(teamId, 0);
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            BasicEntityShip teamShip = resolveTeamShip(level, capa, teamId, i);
            if (teamShip != null) {
                teamShip.setStateMinor(ID.M.FormatType, 0);
                teamShip.setStateMinor(ID.M.FormatPos, i);
            }
        }

        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsInTeam(capa, teamId), player);
    }

    /**
     * Order all ships in the selected team to attack a target entity.
     * values: 0:player eid, 1:(unused dim), 2:mode, 3:target entity id
     */
    private void handleAttackTarget(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;
        ServerLevel level = player.serverLevel();
        int mode = values.length >= 4 ? Mth.clamp(values[2], 0, 2) : PointerItem.MODE_FORMATION;
        int targetId = values.length >= 4 ? values[3] : values[2];
        Entity target = level.getEntity(targetId);
        if (!(target instanceof LivingEntity livingTarget)
                || !isWithinPointerRange(player, target))
            return;
        if (TargetHelper.isEntityInvulnerable(target)
                || TeamHelper.checkSameOwner(player, target)
                || TeamHelper.checkIsAlly(player, target)) {
            return;
        }

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }

        int teamId = capa.getSelectTeam();
        List<BasicEntityShip> outOfFuel = new ArrayList<>();
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            if (!includesSlot(capa, teamId, i, mode)) {
                continue;
            }
            BasicEntityShip ship = resolveTeamShip(level, capa, teamId, i);
            if (ship == null || ship.level() != level
                    || player.distanceToSqr(ship) >= POINTER_RANGE_SQR) {
                continue;
            }
            if (ship.getStateFlag(ID.F.NoFuel)) {
                outOfFuel.add(ship);
                continue;
            }
            ship.setEntitySit(false);
            ship.setEntityTarget(livingTarget);
            ship.applyEmotesReaction(5);
        }
        markOutOfFuel(player, outOfFuel, target.getX(),
                target.getY() + target.getBbHeight() * 0.5D, target.getZ());
    }

    /**
     * Order all ships in the selected team to guard a target entity.
     * values: 0:player eid, 1:(unused dim), 2:mode, 3:target entity id
     */
    private void handleGuardEntity(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;
        ServerLevel level = player.serverLevel();
        int mode = values.length >= 4 ? Mth.clamp(values[2], 0, 2) : PointerItem.MODE_FORMATION;
        int targetId = values.length >= 4 ? values[3] : values[2];
        Entity target = level.getEntity(targetId);
        if (!isWithinPointerRange(player, target))
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }

        int teamId = capa.getSelectTeam();
        boolean formationGateOk = mode != PointerItem.MODE_FORMATION
                || capa.getFormatID(teamId) <= 0
                || capa.getNumberOfShip(level, teamId) > 4;
        if (!formationGateOk) {
            return;
        }

        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            if (!includesSlot(capa, teamId, i, mode)) {
                continue;
            }
            BasicEntityShip ship = resolveTeamShip(level, capa, teamId, i);
            if (ship != null && ship.level() == level
                    && player.distanceToSqr(ship) < POINTER_RANGE_SQR
                    && !ship.getStateFlag(ID.F.NoFuel)) {
                FormationHelper.applyShipGuardEntity(ship, target);
                ship.sendSyncPacketGuard();
            }
        }
    }

    /**
     * Clear all ships from the selected team.
     */
    private void handleClearTeam(ServerPlayer player) {
        if (!hasPointerInHand(player)) {
            return;
        }
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }

        int teamId = capa.getSelectTeam();
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            int sid = capa.getTeamSID(teamId, i);
            if (sid > 0) {
                Entity shipEnt = player.serverLevel().getEntity(sid);
                if (shipEnt instanceof BasicEntityShip ship) {
                    ship.setStateMinor(ID.M.FormatType, 0);
                    ship.setStateMinor(ID.M.FormatPos, 0);
                }
            }
            capa.setTeamMember(teamId, i, 0);
            capa.setTeamSID(teamId, i, 0);
            capa.setShipSelected(teamId, i, false);
        }
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsInTeam(capa, teamId), player);
    }

    /**
     * Set a ship's team ID.
     * values: 0:player eid, 1:(unused dim), 2:entity id, 3:team id
     */
    private void handleSetShipTeamID(ServerPlayer player) {
        if (values.length < 4 || !hasPointerInHand(player))
            return;
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(values[2]);
        if (entity instanceof BasicEntityShip ship && TeamHelper.checkSameOwner(player, ship)
                && isWithinPointerRange(player, ship)) {
            ship.setStateMinor(ID.M.FormatType, Mth.clamp(values[3], 0, 5));
        }
    }

    /**
     * Order all ships in the selected team to start moving (unsit) and set guard
     * position.
     * values: 0:player eid, 1:(unused dim), 2:mode, 3:guardType, 4:x, 5:y, 6:z
     */
    private void handleSetMove(ServerPlayer player) {
        if (values.length < 7 || !hasPointerInHand(player))
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        int teamId = capa.getSelectTeam();
        int mode = Mth.clamp(values[2], 0, 2);
        int guardType = Mth.clamp(values[3], 0, 1);
        int gx = values[4];
        int gy = values[5];
        int gz = values[6];
        BlockPos destinationPos = new BlockPos(gx, gy, gz);
        if (!level.isInWorldBounds(destinationPos)
                || !isWithinPointerRange(player, destinationPos)) {
            return;
        }

        int formatId = capa.getFormatID(teamId);
        boolean formationMove = mode == 2 && formatId > 0;

        ArrayList<BasicEntityShip> ships = new ArrayList<>();
        List<BasicEntityShip> outOfFuel = new ArrayList<>();
        boolean formationMemberMissing = false;
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            if (!includesSlot(capa, teamId, i, mode)) {
                continue;
            }
            BasicEntityShip ship = resolveTeamShip(level, capa, teamId, i);
            if (ship == null && formationMove) {
                int shipUid = capa.getTeamMember(teamId, i);
                BasicEntityShip persistentShip = ServerDataManager.getShipByUID(shipUid);
                if (persistentShip != null && persistentShip.getPlayerUID() == capa.getPlayerUID()) {
                    ship = persistentShip;
                } else if (shipUid > 0) {
                    formationMemberMissing = true;
                }
            }
            if (ship != null) {
                if (ship.getStateFlag(ID.F.NoFuel)) {
                    outOfFuel.add(ship);
                } else {
                    ships.add(ship);
                }
            }
        }
        markOutOfFuel(player, outOfFuel, gx + 0.5D, gy + 0.5D, gz + 0.5D);

        if (formationMove && formationMemberMissing) {
            return;
        }
        if (ships.isEmpty()) {
            return;
        }

        if (formationMove && ships.size() < 5) {
            return;
        }

        if (formationMove) {
            for (BasicEntityShip ship : ships) {
                if (ship.getStateMinor(ID.M.FormatType) != formatId
                        || ship.level() != level
                        || player.distanceToSqr(ship) > 4096D) {
                    return;
                }
            }
        } else {
            ships.removeIf(ship -> ship.level() != level || player.distanceToSqr(ship) > 4096D);
            if (ships.isEmpty()) {
                return;
            }
        }

        BasicEntityShip flagship = ships.get(0);
        boolean[] facing = FormationHelper.getFormationDirection(
                gx, gz, flagship.getX(), flagship.getZ());

        int[] oldGuardPos = null;
        if (formationMove && flagship.hasGuardDestination()) {
            oldGuardPos = new int[]{flagship.getGuardedPos(0), flagship.getGuardedPos(1),
                    flagship.getGuardedPos(2)};
        }

        int[] cursor = {gx, gy, gz};
        for (BasicEntityShip ship : ships) {
            if (formationMove) {
                switch (formatId) {
                    case 1:
                    case 4:
                        cursor = FormationHelper.setFormationPosAndApplyGuardPos1(ship, formatId,
                                facing[0], facing[1], cursor[0], cursor[1], cursor[2], level);
                        break;
                    case 2:
                    case 3:
                    case 5:
                        FormationHelper.setFormationPosAndApplyGuardPos2(ship, formatId,
                                facing[0], facing[1], gx, gy, gz, level);
                        break;
                    default:
                        FormationHelper.applyShipGuard(ship, gx, gy, gz, true);
                        break;
                }
                ship.applyEmotesReaction(5);
            } else {
                FormationHelper.applyShipGuard(ship, gx, gy, gz, false, guardType);
            }
            ship.sendSyncPacketGuard();
        }

        if (formationMove && oldGuardPos != null
                && flagship.getGuardedPos(0) == oldGuardPos[0]
                && flagship.getGuardedPos(1) == oldGuardPos[1]
                && flagship.getGuardedPos(2) == oldGuardPos[2]) {
            for (BasicEntityShip ship : ships) {
                ship.setGuardedPos(-1, -1, -1, 0, 0);
                ship.setGuardedEntity(null);
                ship.setStateFlag(ID.F.CanFollow, true);
            }
        }
    }

    /**
     * Three-value form selects a team by index (formation GUI/hotkey).
     * Four-value form changes the pointer selection:
     * values: 0:player eid, 1:(unused dim), 2:mode, 3:ship UID
     */
    private void handleSetSelect(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }

        if (values.length == 3) {
            capa.setSelectTeam(values[2]);
            ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsInTeam(capa, capa.getSelectTeam()), player);
            return;
        }

        int teamId = capa.getSelectTeam();
        int mode = Mth.clamp(values[2], 0, 2);
        int slot = findTeamSlotByUID(capa, teamId, values[3]);
        if (slot < 0 || mode == PointerItem.MODE_FORMATION) {
            return;
        }
        if (mode == PointerItem.MODE_SINGLE) {
            capa.clearShipSelection(teamId);
            capa.setShipSelected(teamId, slot, true);
        } else {
            capa.setShipSelected(teamId, slot, !capa.isShipSelected(teamId, slot));
        }
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsInTeam(capa, teamId), player);
    }

    /**
     * Set the formation type for the selected team.
     * values: 0:player eid, 1:team index, 2:formation id
     */
    private void handleSetFormation(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int teamId = values[1];
        if (capa == null || teamId < 0 || teamId >= CapaTeitoku.TEAM_NUM) {
            return;
        }
        int requestedFormation = Mth.clamp(values[2], 0, 5);
        int numShips = capa.getNumberOfShip(player.serverLevel(), teamId);
        int formationId = numShips > 4 && requestedFormation > 0 ? requestedFormation : 0;
        capa.setFormatID(teamId, formationId);
        // Update all ships in team
        for (int i = 0; i < CapaTeitoku.SLOT_NUM; i++) {
            int sid = capa.getTeamSID(teamId, i);
            if (sid > 0) {
                Entity shipEnt = player.serverLevel().getEntity(sid);
                if (shipEnt instanceof BasicEntityShip ship) {
                    ship.setStateMinor(ID.M.FormatType, formationId);
                }
            }
        }
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncFormation(capa), player);
    }

    // ========== OP Tool Command Handlers ==========

    /**
     * Toggle a custom target class for the player.
     * Uses stringData for the class name.
     */
    private void handleSetTarClass(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null || !ServerDataManager.isValidTargetClassName(stringData)) {
            return;
        }

        int pid = capa.getPlayerUID();
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(TARGET_CLASS_RATE_TAG);
        if (last > 0L && now >= last && now - last < TARGET_CLASS_RATE_TICKS) {
            return;
        }

        boolean removing = ServerDataManager.hasPlayerTargetClass(pid, stringData);
        if (!removing) {
            if (values.length < 3 || !hasPointerInHand(player)) {
                return;
            }
            Entity target = player.serverLevel().getEntity(values[2]);
            if (!(target instanceof LivingEntity) || target.isInvisible()
                    || !isWithinPointerRange(player, target)
                    || !player.hasLineOfSight(target)
                    || !stringData.equals(target.getClass().getSimpleName())) {
                return;
            }
        }

        player.getPersistentData().putLong(TARGET_CLASS_RATE_TAG, now);
        ServerDataManager.setPlayerTargetClass(pid, stringData);
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncTargetClasses(
                ServerDataManager.getPlayerTargetClass(pid)), player);
    }

    /**
     * Swap two ships in the selected team's ship slots.
     * values: 0:player eid, 1:(unused dim), 2:slot1, 3:slot2
     */
    private void handleSwapShip(ServerPlayer player) {
        if (values.length < 4)
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int teamId = capa.getSelectTeam();
        int slot1 = values[2];
        int slot2 = values[3];
        if (slot1 >= 0 && slot1 < CapaTeitoku.SLOT_NUM && slot2 >= 0 && slot2 < CapaTeitoku.SLOT_NUM) {
            int m1 = capa.getTeamMember(teamId, slot1);
            int s1 = capa.getTeamSID(teamId, slot1);
            boolean selected1 = capa.isShipSelected(teamId, slot1);
            int m2 = capa.getTeamMember(teamId, slot2);
            int s2 = capa.getTeamSID(teamId, slot2);
            boolean selected2 = capa.isShipSelected(teamId, slot2);

            ServerLevel level = player.serverLevel();
            BasicEntityShip shipOriginallyAtSlot1 = resolveTeamShip(level, capa, teamId, slot1);
            BasicEntityShip shipOriginallyAtSlot2 = resolveTeamShip(level, capa, teamId, slot2);
            if (shipOriginallyAtSlot1 != null) {
                s1 = shipOriginallyAtSlot1.getId();
            }
            if (shipOriginallyAtSlot2 != null) {
                s2 = shipOriginallyAtSlot2.getId();
            }

            capa.setTeamMember(teamId, slot1, m2);
            capa.setTeamSID(teamId, slot1, s2);
            capa.setShipSelected(teamId, slot1, selected2);
            capa.setTeamMember(teamId, slot2, m1);
            capa.setTeamSID(teamId, slot2, s1);
            capa.setShipSelected(teamId, slot2, selected1);

            if (shipOriginallyAtSlot1 != null) {
                shipOriginallyAtSlot1.setStateMinor(ID.M.FormatPos, slot2);
            }
            if (shipOriginallyAtSlot2 != null) {
                shipOriginallyAtSlot2.setStateMinor(ID.M.FormatPos, slot1);
            }
            ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsInTeam(capa, teamId), player);
        }
    }

    // ========== Desk GUI Command Handlers ==========

    /**
     * Set a team's display name.
     * values: 0:player eid, 1:(unused dim), 2:team index
     * stringData: the new name
     */
    private void handleSetUnitName(ServerPlayer player) {
        if (values.length < 3 || stringData == null)
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int teamId = values[2];
        if (teamId >= 0 && teamId < CapaTeitoku.TEAM_NUM) {
            capa.setUnitName(teamId, stringData);
            ModNetworking.sendToPlayer(S2CGUISyncPacket.syncUnitNames(capa), player);
        }
    }

    /**
     * Open pointer item GUI.
     * values: 0:player eid, 1:(unused dim), 2:gui type (0=formation)
     */
    private void handleOpenItemGUI(ServerPlayer player) {
        if (values.length < 3 || !hasPointerInHand(player))
            return;

        if (values[2] == 0) {// [PORT] 1.10.2 -> 1.20.1: OpenItemGUI is the pointer formation GUI entry
            // point.
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new ContainerFormation(containerId, playerInv),
                    Component.translatable("gui.shincolle_kai.formation.formation")));
        } else {
            LogHelper.debug("C2SGUIInputPacket: unknown OpenItemGUI type=" + values[2]);
        }
    }

    /**
     * Sync selected player item between client and server.
     * Apply the client-predicted mode change to the authoritative held stack.
     */
    private void handleSyncPlayerItem(ServerPlayer player) {
        if (values.length < 3) {
            return;
        }
        int mode = Mth.clamp(values[2], 0, 5);
        ItemStack pointer = player.getMainHandItem();
        if (pointer.getItem() != ModItems.POINTER.get()) {
            pointer = player.getOffhandItem();
        }
        if (pointer.getItem() != ModItems.POINTER.get()) {
            return;
        }

        int oldMode = PointerItem.getMode(pointer);
        PointerItem.setMode(pointer, mode);

        if (mode % 3 == PointerItem.MODE_SINGLE && oldMode % 3 != PointerItem.MODE_SINGLE) {
            CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
            if (capa != null) {
                int teamId = capa.getSelectTeam();
                capa.clearShipSelection(teamId);
                selectFirstTeamMemberIfNeeded(capa, teamId);
                ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsInTeam(capa, teamId), player);
            }
        }
    }

    /**
     * Toggle an unattackable target class (OP tool).
     * Uses stringData for the class name.
     */
    private void handleSetUnatkClass(ServerPlayer player) {
        if (!ServerDataManager.checkOP(player))
            return;
        if (stringData != null && !stringData.isEmpty()) {
            boolean added = ServerDataManager.addUnattackableTargetClass(stringData);
            player.sendSystemMessage(Component.literal(
                    "[ShinColle] Unattackable class " + (added ? "added" : "removed") + ": " + stringData));
        }
    }

    /**
     * Show the unattackable class list to the player (OP tool).
     */
    private void handleShowUnatkClass(ServerPlayer player) {
        if (!ServerDataManager.checkOP(player))
            return;
        HashMap<Integer, String> map = ServerDataManager.getUnattackableTargetClass();
        String classList = (map != null) ? map.values().toString() : "[]";
        player.sendSystemMessage(Component.literal("[ShinColle] Unattackable classes: " + classList));
    }

    /**
     * Create a new team for the player.
     * stringData: the team name
     */
    private void handleDeskCreate(ServerPlayer player) {
        if (stringData == null || stringData.isEmpty())
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }
        ServerDataManager.teamCreate(player, stringData);

        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncPlayerMisc(capa), player);
        syncDeskTeamData(player, capa);
    }


    /**
     * Rename the player's own team.
     * stringData: the new team name
     */
    private void handleDeskRename(ServerPlayer player) {
        if (stringData == null || stringData.isEmpty())
            return;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        ServerDataManager.teamRename(capa.getPlayerUID(), stringData);
        syncDeskTeamData(player, capa);
    }

    /**
     * Add another team as an ally (by leader name).
     * stringData: the target team leader's name
     */
    private void handleDeskAlly(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int myTid = capa.getPlayerUID();
        int otherTid = resolveDeskTargetTeamId();
        if (otherTid > 0) {
            ServerDataManager.teamAddAlly(myTid, otherTid);
            syncDeskTeamData(player, capa);
        }
    }

    /**
     * Break alliance with another team (by leader name).
     * stringData: the target team leader's name
     */
    private void handleDeskBreak(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int myTid = capa.getPlayerUID();
        int otherTid = resolveDeskTargetTeamId();
        if (otherTid > 0) {
            ServerDataManager.teamRemoveAlly(myTid, otherTid);
            syncDeskTeamData(player, capa);
        }
    }

    /**
     * Ban another team (by leader name, bilateral).
     * stringData: the target team leader's name
     */
    private void handleDeskBan(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int myTid = capa.getPlayerUID();
        int otherTid = resolveDeskTargetTeamId();
        if (otherTid > 0) {
            ServerDataManager.teamAddBan(myTid, otherTid);
            syncDeskTeamData(player, capa);
        }
    }

    // ========== Utility ==========

    /**
     * Unban another team (by leader name, unilateral).
     * stringData: the target team leader's name
     */
    private void handleDeskUnban(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        int myTid = capa.getPlayerUID();
        int otherTid = resolveDeskTargetTeamId();
        if (otherTid > 0) {
            ServerDataManager.teamRemoveBan(myTid, otherTid);
            syncDeskTeamData(player, capa);
        }
    }

    private int resolveDeskTargetTeamId() {
        if (values.length > 0 && values[0] > 0) {
            return values[0];
        }
        if (stringData != null && !stringData.isEmpty()) {
            return findTeamByLeaderName(stringData);
        }
        return 0;
    }

    /**
     * Disband the player's own team.
     */
    private void handleDeskDisband(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            return;
        }
        ServerDataManager.teamDisband(player);

        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncPlayerMisc(capa), player);
        syncDeskTeamData(player, capa);
    }


    /**
     * Full player data sync requested from desk GUI.
     */
    private void handleDeskFuncSync(ServerPlayer player) {
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);

        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncPlayerFull(capa), player);
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncTargetClasses(
                ServerDataManager.getPlayerTargetClass(capa.getPlayerUID())), player);
        syncDeskTeamData(player, capa);
    }


    // ========== Getters ==========

    public byte getType() {
        return type;
    }

    public int[] getValues() {
        return values;
    }

    public String getStringData() {
        return stringData;
    }
}
