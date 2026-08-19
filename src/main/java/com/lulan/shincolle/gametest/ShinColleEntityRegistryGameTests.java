package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.*;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.client.gui.inventory.ContainerFormation;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.crafting.EquipCalc;
import com.lulan.shincolle.crafting.ShipCalc;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.block.BlockWaypoint;
import com.lulan.shincolle.entity.other.EntityFloatingFort;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.entity.other.EntityProjectileStatic;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.MarriageRing;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.network.C2SInputPacket;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.S2CGUISyncPacket;
import com.lulan.shincolle.network.S2CShipyardStockPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.tileentity.BasicTileMulti;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.ClientRuntimeHelper;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.MulitBlockHelper;
import com.lulan.shincolle.utility.PacketHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.lulan.shincolle.utility.TileEntityHelper;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntSupplier;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShinColleEntityRegistryGameTests {

    private ShinColleEntityRegistryGameTests() {
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void criticalEntityTypesCreate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        assertCanCreate(level, ModEntities.BB_KIRISHIMA_MOB.get(), "bb_kirishima_mob");
        assertCanCreate(level, ModEntities.SS_KA.get(), "ss_ka");
        assertCanCreate(level, ModEntities.ABYSS_MISSILE.get(), "abyss_missile");
        assertCanCreate(level, ModEntities.BASIC_ENTITY_ITEM.get(), "basic_entity_item");

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void criticalItemsServerSafe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TooltipFlag tooltipFlag = TooltipFlag.Default.NORMAL;

        assertItemTooltipSafe(level, ModItems.MARRIAGE_RING.get(), "marriage_ring", tooltipFlag);
        assertItemTooltipSafe(level, ModItems.POINTER.get(), "pointer", tooltipFlag);
        assertItemTooltipSafe(level, ModItems.EQUIP_CANNON.get(), "equip_cannon", tooltipFlag);

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void allRegisteredItemsTooltipServerSafe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TooltipFlag tooltipFlag = TooltipFlag.Default.NORMAL;

        for (RegistryObject<Item> itemObject : ModItems.ITEMS.getEntries()) {
            Item item = itemObject.get();
            assert itemObject.getId() != null;
            assertItemTooltipSafe(level, item, itemObject.getId().toString(), tooltipFlag);
        }

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void allRegisteredEntityTypesCreate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        for (RegistryObject<EntityType<?>> typeObject : ModEntities.ENTITIES.getEntries()) {
            EntityType<?> type = typeObject.get();
            assert typeObject.getId() != null;
            assertCanCreate(level, type, typeObject.getId().toString());
        }

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pointerAndRingServerInteractionSafe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer fakePlayer = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000001"), "shincolle_gametest"));

        ItemStack pointer = new ItemStack(ModItems.POINTER.get());
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, pointer);
        InteractionResultHolder<ItemStack> pointerUse = ModItems.POINTER.get().use(level, fakePlayer,
                InteractionHand.MAIN_HAND);
        if (pointerUse.getResult() != InteractionResult.PASS) {
            throw new AssertionError("Pointer use result is not PASS on server: " + pointerUse.getResult());
        }
        ModItems.POINTER.get().inventoryTick(pointer, level, fakePlayer, 0, true);

        ItemStack ring = new ItemStack(ModItems.MARRIAGE_RING.get());
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ModItems.MARRIAGE_RING.get().use(level, fakePlayer, InteractionHand.MAIN_HAND);
        if (!ring.getOrCreateTag().getBoolean("isActive")) {
            throw new AssertionError("Marriage ring should be active after first use.");
        }
        ModItems.MARRIAGE_RING.get().use(level, fakePlayer, InteractionHand.MAIN_HAND);
        if (ring.getOrCreateTag().getBoolean("isActive")) {
            throw new AssertionError("Marriage ring should be inactive after second use.");
        }
        for (int i = 0; i < 5; i++) {
            ModItems.MARRIAGE_RING.get().inventoryTick(ring, level, fakePlayer, 0, true);
        }

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pointerModeNbtAndInvalidModeGuard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer fakePlayer = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000002"), "shincolle_gametest_mode"));

        ItemStack pointer = new ItemStack(ModItems.POINTER.get());
        PointerItem.setMode(pointer, PointerItem.MODE_FORMATION);
        if (PointerItem.getMode(pointer) != PointerItem.MODE_FORMATION) {
            throw new AssertionError("Pointer mode NBT round-trip failed.");
        }

        PointerItem.setMode(pointer, 3);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, pointer);
        InteractionResultHolder<ItemStack> useResult = ModItems.POINTER.get().use(level, fakePlayer,
                InteractionHand.MAIN_HAND);
        if (useResult.getResult() != InteractionResult.SUCCESS) {
            throw new AssertionError("Pointer invalid mode should return SUCCESS guard path on server.");
        }

        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pointerOpenItemGuiOpensFormationMenu(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "shincolle_pointer_open_item_gui");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));

        C2SGUIInputPacket packet = new C2SGUIInputPacket(
                C2SGUIInputPacket.OpenItemGUI,
                new int[]{player.getId(), 0, 0});
        invokePacketHandler(packet, "handleOpenItemGUI", player);

        if (!(player.containerMenu instanceof ContainerFormation)) {
            throw new AssertionError("Pointer OpenItemGUI should open ContainerFormation menu on server.");
        }

        player.closeContainer();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void formationSetUnitNamePacketUpdatesTeamName(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "shincolle_set_unit_name");

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);


        int teamId = 2;
        String expectedName = "Unit-Alpha";
        C2SGUIInputPacket packet = new C2SGUIInputPacket(
                C2SGUIInputPacket.SetUnitName,
                new int[]{player.getId(), 0, teamId},
                expectedName);
        invokePacketHandler(packet, "handleSetUnitName", player);

        String actualName = capa.getUnitName(teamId);
        if (!expectedName.equals(actualName)) {
            throw new AssertionError("SetUnitName packet should update team name. expected="
                    + expectedName + " actual=" + actualName);
        }

        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void formationSwapShipPacketSwapsSelectedTeamSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                "shincolle_swap_ship");

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);


        int teamId = 3;
        capa.setSelectTeam(teamId);

        int slotA = 1;
        int slotB = 4;
        int memberA = 1111;
        int sidA = 2111;
        int memberB = 4444;
        int sidB = 2444;
        capa.setTeamMember(teamId, slotA, memberA);
        capa.setTeamSID(teamId, slotA, sidA);
        capa.setTeamMember(teamId, slotB, memberB);
        capa.setTeamSID(teamId, slotB, sidB);

        C2SGUIInputPacket packet = new C2SGUIInputPacket(
                C2SGUIInputPacket.SwapShip,
                new int[]{player.getId(), 0, slotA, slotB});
        invokePacketHandler(packet, "handleSwapShip", player);

        if (capa.getTeamMember(teamId, slotA) != memberB || capa.getTeamSID(teamId, slotA) != sidB) {
            throw new AssertionError("SwapShip should move slotB values into slotA for selected team.");
        }
        if (capa.getTeamMember(teamId, slotB) != memberA || capa.getTeamSID(teamId, slotB) != sidA) {
            throw new AssertionError("SwapShip should move slotA values into slotB for selected team.");
        }

        helper.succeed();
    }

    // 2026/04/12：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deskBreakPacketSupportsTeamIdPayload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                "shincolle_desk_break_by_id");

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);


        int myTid = 8001;
        int allyTid = 8002;
        capa.setPlayerUID(myTid);

        ServerDataManager.setTeamData(new TeamData(myTid, "DeskBreakOwner", "desk_break_owner"));
        ServerDataManager.setTeamData(new TeamData(allyTid, "DeskBreakAlly", "desk_break_ally"));
        ServerDataManager.teamAddAlly(myTid, allyTid);

        C2SGUIInputPacket packet = new C2SGUIInputPacket(
                C2SGUIInputPacket.Desk_Break,
                new int[]{allyTid});
        invokePacketHandler(packet, "handleDeskBreak", player);

        TeamData myTeam = ServerDataManager.getTeamData(myTid);
        TeamData allyTeam = ServerDataManager.getTeamData(allyTid);
        if (myTeam == null || allyTeam == null) {
            throw new AssertionError("TeamData missing after desk break packet test setup.");
        }
        if (myTeam.isTeamAlly(allyTid) || allyTeam.isTeamAlly(myTid)) {
            throw new AssertionError("Desk_Break packet with team ID should remove alliance on both teams.");
        }

        helper.succeed();
    }

    // 2026/04/12：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deskUnbanPacketSupportsTeamIdPayload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000008"),
                "shincolle_desk_unban_by_id");

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);


        int myTid = 8101;
        int bannedTid = 8102;
        capa.setPlayerUID(myTid);

        ServerDataManager.setTeamData(new TeamData(myTid, "DeskUnbanOwner", "desk_unban_owner"));
        ServerDataManager.setTeamData(new TeamData(bannedTid, "DeskUnbanTarget", "desk_unban_target"));
        ServerDataManager.teamAddBan(myTid, bannedTid);

        C2SGUIInputPacket packet = new C2SGUIInputPacket(
                C2SGUIInputPacket.Desk_Unban,
                new int[]{bannedTid});
        invokePacketHandler(packet, "handleDeskUnban", player);

        TeamData myTeam = ServerDataManager.getTeamData(myTid);
        TeamData bannedTeam = ServerDataManager.getTeamData(bannedTid);
        if (myTeam == null || bannedTeam == null) {
            throw new AssertionError("TeamData missing after desk unban packet test setup.");
        }
        if (myTeam.isTeamBanned(bannedTid)) {
            throw new AssertionError("Desk_Unban packet with team ID should clear ban from player team.");
        }
        if (!bannedTeam.isTeamBanned(myTid)) {
            throw new AssertionError("Desk_Unban should remain unilateral; target team keeps hostile relation.");
        }

        helper.succeed();
    }

    // 2026/04/12：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void syncTeamDataPacketIncludesKnownTeamIds(GameTestHelper helper) {
        CapaTeitoku capa = new CapaTeitoku();
        capa.setPlayerUID(8201);

        TeamData myTeam = new TeamData(8201, "DeskSyncOwner", "desk_sync_owner");
        myTeam.addTeamAlly(8203);
        myTeam.addTeamBanned(8202);

        HashMap<Integer, TeamData> allTeams = new HashMap<>();
        allTeams.put(8205, new TeamData(8205, "DeskTeamC", "desk_c"));
        allTeams.put(8201, myTeam);
        allTeams.put(8202, new TeamData(8202, "DeskTeamB", "desk_b"));

        S2CGUISyncPacket packet = S2CGUISyncPacket.syncTeamData(capa, myTeam, allTeams);
        if (packet.getType() != S2CGUISyncPacket.SyncPlayerProp_TeamData) {
            throw new AssertionError("syncTeamData should create SyncPlayerProp_TeamData packet.");
        }

        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.getPayload()));
        String teamName = PacketHelper.readNullableString(payload);
        List<Integer> allyList = PacketHelper.readIntList(payload);
        List<Integer> banList = PacketHelper.readIntList(payload);
        List<Integer> knownTeamIds = PacketHelper.readIntList(payload);

        if (!"DeskSyncOwner".equals(teamName)) {
            throw new AssertionError("syncTeamData should include current team name in payload.");
        }
        if (!allyList.equals(List.of(8203))) {
            throw new AssertionError("syncTeamData should include ally list in payload.");
        }
        if (!banList.equals(List.of(8202))) {
            throw new AssertionError("syncTeamData should include ban list in payload.");
        }
        if (!knownTeamIds.equals(List.of(8201, 8202, 8205))) {
            throw new AssertionError("syncTeamData should include sorted known team IDs.");
        }

        helper.succeed();
    }

    // 2026/04/12：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneTileBtnAppliesExplicitValues(GameTestHelper helper) {
        TileEntityCrane tile = new TileEntityCrane(BlockPos.ZERO, Blocks.AIR.defaultBlockState());

        try {
            Method method = C2SGUIInputPacket.class
                    .getDeclaredMethod("handleCraneBtn", TileEntityCrane.class, int.class, int.class);
            method.setAccessible(true);

            // [PORT] 1.10.2 -> 1.20.1: packet payload must apply explicit values, not blind
            // toggles.
            method.invoke(null, tile, (int) ID.B.Crane_Power, 1);
            if (!tile.isActive()) {
                throw new AssertionError("Crane_Power value=1 should enable crane.");
            }
            method.invoke(null, tile, (int) ID.B.Crane_Power, 0);
            if (tile.isActive()) {
                throw new AssertionError("Crane_Power value=0 should disable crane.");
            }

            method.invoke(null, tile, (int) ID.B.Crane_Mode, -3);
            if (tile.getCraneMode() != 0) {
                throw new AssertionError("Crane_Mode should clamp low values to 0.");
            }
            method.invoke(null, tile, (int) ID.B.Crane_Mode, 999);
            if (tile.getCraneMode() != TileEntityCrane.MODE_NAMES.length - 1) {
                throw new AssertionError("Crane_Mode should clamp high values to max mode index.");
            }

            method.invoke(null, tile, (int) ID.B.Crane_Dict, 1);
            if (!tile.isCheckDict()) {
                throw new AssertionError("Crane_Dict value=1 should enable dict check.");
            }
            method.invoke(null, tile, (int) ID.B.Crane_Dict, 0);
            if (tile.isCheckDict()) {
                throw new AssertionError("Crane_Dict value=0 should disable dict check.");
            }

            method.invoke(null, tile, (int) ID.B.Crane_Red, 2);
            if (tile.getRedSignalMode() != 2) {
                throw new AssertionError("Crane_Red should keep explicit mode 2.");
            }
            method.invoke(null, tile, (int) ID.B.Crane_Red, 3);
            if (tile.getRedSignalMode() != 0) {
                throw new AssertionError("Crane_Red values above 2 should wrap to 0.");
            }

            method.invoke(null, tile, (int) ID.B.Crane_Liquid, 1);
            if (tile.getLiquidMode() != 1) {
                throw new AssertionError("Crane_Liquid should apply explicit mode value.");
            }
            method.invoke(null, tile, (int) ID.B.Crane_Energy, 2);
            if (tile.getEnergyMode() != 2) {
                throw new AssertionError("Crane_Energy should apply explicit mode value.");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Reflection call to handleCraneBtn failed.", e);
        }

        helper.succeed();
    }

    // 2026/04/12：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipInvPagePacketUpdatesOpenedContainerPage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000009"),
                "shincolle_ship_inv_page"));

        Entity entity = ModEntities.BB_KONGOU.get().create(level);
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in inventory-page packet test.");
        }
        ship.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 2.5D, 0F, 0F);
        if (!level.addFreshEntity(ship)) {
            throw new AssertionError("Failed to add ship for inventory-page packet test.");
        }

        ship.tame(player);
        ship.setOwnerUUID(player.getUUID());
        player.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> capa.setPlayerUID(9009));
        ship.setStateMinor(ID.M.PlayerUID, 9009);
        ship.setStateMinor(ID.M.DrumState, 2);
        player.moveTo(ship.getX(), ship.getY(), ship.getZ());
        ship.openGUI(player);
        if (!(player.containerMenu instanceof ContainerShipInventory menu)) {
            throw new AssertionError("Ship GUI should open ContainerShipInventory in inventory-page packet test.");
        }
        if (!com.lulan.shincolle.utility.TeamHelper.checkSameOwner(player, ship)
                || !ship.isAlive() || ship.level() != player.level()
                || menu.getShip() != ship || !menu.stillValid(player)
                || level.getEntity(ship.getId()) != ship) {
            throw new AssertionError("Authorized ship menu failed its server validation guards: owned="
                    + com.lulan.shincolle.utility.TeamHelper.checkSameOwner(player, ship)
                    + " alive=" + ship.isAlive()
                    + " sameLevel=" + (ship.level() == player.level())
                    + " sameMenuShip=" + (menu.getShip() == ship)
                    + " stillValid=" + menu.stillValid(player)
                    + " entityResolved=" + (level.getEntity(ship.getId()) == ship));
        }

        C2SGUIInputPacket packet = new C2SGUIInputPacket(
                C2SGUIInputPacket.ShipBtn,
                new int[]{ship.getId(), 0, ID.B.ShipInv_InvPage, 2});
        invokePacketHandler(packet, "handleShipBtn", player);

        int invPageCap = ship.getCapaShipInventory().getInventoryPage();
        if (invPageCap != 2) {
            throw new AssertionError(
                    "ShipInv_InvPage should update capability inventory page to 2, actual=" + invPageCap);
        }
        if (menu.getInventoryPage() != 2) {
            throw new AssertionError("ShipInv_InvPage should update opened ContainerShipInventory page to 2.");
        }

        ServerPlayer otherPlayer = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                "shincolle_ship_inv_unauthorized"));
        otherPlayer.moveTo(ship.getX(), ship.getY(), ship.getZ());
        otherPlayer.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> capa.setPlayerUID(9010));
        C2SGUIInputPacket unauthorized = new C2SGUIInputPacket(
                C2SGUIInputPacket.ShipBtn,
                new int[]{ship.getId(), 0, ID.B.ShipInv_InvPage, 1});
        invokePacketHandler(unauthorized, "handleShipBtn", otherPlayer);
        if (ship.getCapaShipInventory().getInventoryPage() != 2) {
            throw new AssertionError("A non-owner must not change another ship's GUI state.");
        }

        player.closeContainer();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void teamRuntimeEntityIdsAreNotPersisted(GameTestHelper helper) {
        CapaTeitoku source = new CapaTeitoku();
        source.setTeamMember(2, 3, 12345);
        source.setTeamSID(2, 3, 67890);
        source.setShipSelected(2, 3, true);

        CapaTeitoku loaded = new CapaTeitoku();
        loaded.deserializeNBT(source.serializeNBT());
        if (loaded.getTeamMember(2, 3) != 12345 || loaded.getTeamSID(2, 3) != -1
                || !loaded.isShipSelected(2, 3)) {
            throw new AssertionError("Ship UID and selection must survive; runtime entity ID must be invalidated.");
        }

        loaded.clearTeamEntityIDs();
        if (loaded.getTeamMember(2, 3) != 12345 || loaded.getTeamSID(2, 3) != -1) {
            throw new AssertionError("Clearing runtime entity IDs must not erase persistent team membership.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pointerSelectionAndModeAreAppliedServerSide(GameTestHelper helper) {
        ServerPlayer player = createFollowTestOwner(helper, helper.getLevel(),
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                "shincolle_pointer_selection");
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            throw new AssertionError("Test player has no CapaTeitoku capability.");
        }
        capa.setTeamMember(0, 0, 1001);
        capa.setTeamMember(0, 1, 1002);
        capa.setShipSelected(0, 0, true);

        ItemStack pointer = new ItemStack(ModItems.POINTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pointer);
        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SyncPlayerItem,
                new int[]{player.getId(), 0, PointerItem.MODE_GROUP}), "handleSyncPlayerItem", player);
        if (PointerItem.getMode(player.getMainHandItem()) != PointerItem.MODE_GROUP) {
            throw new AssertionError("Pointer mode was not applied to the authoritative server stack.");
        }

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSelect,
                new int[]{player.getId(), 0, PointerItem.MODE_GROUP, 1002}), "handleSetSelect", player);
        if (!capa.isShipSelected(0, 0) || !capa.isShipSelected(0, 1)) {
            throw new AssertionError("Group mode should toggle the addressed ship without clearing the existing selection.");
        }

        invokePacketHandler(new C2SGUIInputPacket(C2SGUIInputPacket.SetSelect,
                new int[]{player.getId(), 0, PointerItem.MODE_SINGLE, 1002}), "handleSetSelect", player);
        if (capa.isShipSelected(0, 0) || !capa.isShipSelected(0, 1)) {
            throw new AssertionError("Single mode should retain only the addressed ship.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileShipsParticipateInVanillaEnemyClassification(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShipHostile) || !(entity instanceof Enemy)) {
            throw new AssertionError("Hostile ship must implement vanilla Enemy classification.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileBossBarSurvivesNbtReload(GameTestHelper helper) {
        Entity sourceEntity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        Entity loadedEntity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(sourceEntity instanceof BasicEntityShipHostile source)
                || !(loadedEntity instanceof BasicEntityShipHostile loaded)) {
            throw new AssertionError("Failed to create hostile ships for boss bar reload test.");
        }

        source.initAttrs(2);
        float savedHealth = source.getMaxHealth() * 0.35F;
        source.setHealth(savedHealth);
        CompoundTag saved = new CompoundTag();
        source.saveWithoutId(saved);

        loaded.load(saved);
        if (loaded.getScaleLevel() != 2) {
            throw new AssertionError("Boss scale level was not restored from NBT.");
        }
        if (Math.abs(loaded.getHealth() - savedHealth) > 0.01F) {
            throw new AssertionError("Loading boss bar state must not heal the boss. expected="
                    + savedHealth + " actual=" + loaded.getHealth());
        }

        ServerBossEvent firstEvent = extractBossEvent(loaded);
        if (firstEvent == null) {
            throw new AssertionError("NBT-loaded boss did not recreate its ServerBossEvent.");
        }
        loaded.creatBossEvent();
        if (extractBossEvent(loaded) != firstEvent) {
            throw new AssertionError("Boss event recreation must be idempotent.");
        }

        ServerPlayer player = createFollowTestOwner(helper, helper.getLevel(),
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                "shincolle_boss_bar_reload");
        loaded.startSeenByPlayer(player);
        if (!firstEvent.getPlayers().contains(player)) {
            throw new AssertionError("Tracking player was not registered with the restored boss event.");
        }

        loaded.stopSeenByPlayer(player);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAiSettingsClampUntrustedPacketValues(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in AI clamp test.");
        }

        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_FollowMin, Integer.MAX_VALUE);
        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_FollowMax, Integer.MIN_VALUE);
        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_FleeHP, 500);
        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_WpStay, -10);
        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_Task, 99);
        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_TaskSide, -1);
        ship.setStateFlag(ID.F.NoFuel, false);
        C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_NoFuel, 1);

        if (ship.getStateMinor(ID.M.FollowMin) != 1 || ship.getStateMinor(ID.M.FollowMax) != 2
                || ship.getStateMinor(ID.M.FleeHP) != 100 || ship.getStateMinor(ID.M.WpStay) != 0
                || ship.getStateMinor(ID.M.Task) != 4
                || ship.getStateMinor(ID.M.TaskSide) != 0x17FFFF
                || ship.getStateFlag(ID.F.NoFuel)) {
            throw new AssertionError("Ship AI packet values were not clamped to server-owned ranges.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyTargetSelectorRejectsPassiveMobsAndUsesCustomList(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity entity = ModEntities.BB_KONGOU.get().create(level);
        Entity cow = EntityType.COW.create(level);
        Entity zombie = EntityType.ZOMBIE.create(level);
        if (!(entity instanceof BasicEntityShip ship) || cow == null || zombie == null) {
            throw new AssertionError("Failed to create entities for target selector test.");
        }
        cow.moveTo(2.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D);
        zombie.moveTo(3.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D);
        level.addFreshEntity(cow);
        level.addFreshEntity(zombie);

        int playerUid = 54321;
        ship.setStateMinor(ID.M.PlayerUID, playerUid);
        ship.setStateFlag(ID.F.OnSightChase, false);
        TargetHelper.Selector selector = new TargetHelper.Selector(ship);
        if (selector.test(cow)) {
            throw new AssertionError("Friendly ships must not automatically target passive mobs.");
        }
        if (!selector.test(zombie)) {
            throw new AssertionError("Friendly ships should target vanilla monsters.");
        }

        ServerDataManager.setPlayerTargetClass(playerUid, cow.getClass().getSimpleName());
        if (!selector.test(cow)) {
            throw new AssertionError("The per-player custom target class list is not used by the selector.");
        }
        ServerDataManager.setPlayerTargetClass(playerUid, cow.getClass().getSimpleName());
        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void clientRuntimeHelperServerGuardSafe(GameTestHelper helper) {
        if (ClientRuntimeHelper.getClientPlayer() != null) {
            throw new AssertionError("ClientRuntimeHelper#getClientPlayer must be null on dedicated server.");
        }
        if (ClientRuntimeHelper.isControlDown()) {
            throw new AssertionError("ClientRuntimeHelper#isControlDown must be false on dedicated server.");
        }

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void tooltipFallbackAndHideFlagsServerBehavior(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        ItemStack ring = new ItemStack(ModItems.MARRIAGE_RING.get());
        List<Component> ringTooltip = new ArrayList<>();
        ModItems.MARRIAGE_RING.get().appendHoverText(ring, level, ringTooltip, TooltipFlag.Default.NORMAL);
        if (ringTooltip.isEmpty()) {
            throw new AssertionError("MarriageRing tooltip fallback should provide at least one line on server.");
        }

        Item basicEquipItem = null;
        Item baseTooltipEquipItem = null;
        for (RegistryObject<Item> itemObject : ModItems.ITEMS.getEntries()) {
            Item candidate = itemObject.get();
            if (candidate instanceof BasicEquip) {
                basicEquipItem = candidate;
                try {
                    Class<?> declaringClass = candidate.getClass()
                            .getMethod("appendHoverText", ItemStack.class, Level.class, List.class, TooltipFlag.class)
                            .getDeclaringClass();
                    if (declaringClass == BasicEquip.class) {
                        baseTooltipEquipItem = candidate;
                        break;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Failed to inspect BasicEquip tooltip method.", e);
                }
            }
        }
        if (basicEquipItem == null) {
            throw new AssertionError("No BasicEquip item found in registry.");
        }

        if (baseTooltipEquipItem != null) {
            ItemStack equip = new ItemStack(baseTooltipEquipItem);
            equip.getOrCreateTag();
            List<Component> equipTooltip = new ArrayList<>();
            baseTooltipEquipItem.appendHoverText(equip, level, equipTooltip, TooltipFlag.Default.NORMAL);
            int hideFlags = equip.getOrCreateTag().getInt("HideFlags");
            if (hideFlags != 0 && hideFlags != 1) {
                throw new AssertionError("BasicEquip HideFlags should stay within expected range [0,1], got: "
                        + hideFlags);
            }
        }

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void criticalEntitySpawnAndTickServerSafe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        assertCanSpawnAndTick(level, ModEntities.BB_KIRISHIMA_MOB.get(), "bb_kirishima_mob");
        assertCanSpawnAndTick(level, ModEntities.SS_KA.get(), "ss_ka");
        assertCanSpawnAndTick(level, ModEntities.ABYSS_MISSILE.get(), "abyss_missile");
        assertCanSpawnAndTick(level, ModEntities.BASIC_ENTITY_ITEM.get(), "basic_entity_item");

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void blackHoleSpecialEffectSpawnsStaticProjectile(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity hostEntity = ModEntities.BB_KONGOU.get().create(level);
        if (!(hostEntity instanceof IShipAttackBase host)) {
            throw new AssertionError("BB_KONGOU does not implement IShipAttackBase in test context.");
        }

        hostEntity.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostEntity)) {
            throw new AssertionError("Failed to add host entity for black hole special effect test.");
        }

        double x = hostEntity.getX();
        double y = hostEntity.getY();
        double z = hostEntity.getZ();
        CombatHelper.specialAttackEffect(host, 5, new float[]{(float) x, (float) y, (float) z});

        AABB search = new AABB(x - 2D, y - 2D, z - 2D, x + 2D, y + 2D, z + 2D);
        List<EntityProjectileStatic> effects = level.getEntitiesOfClass(EntityProjectileStatic.class, search);
        if (effects.isEmpty()) {
            throw new AssertionError("Black hole special effect did not spawn EntityProjectileStatic.");
        }

        hostEntity.discard();
        for (EntityProjectileStatic effect : effects) {
            effect.discard();
        }

        helper.succeed();
    }

    // 2026/04/07：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void floatingFortHeavyAttackDetonatesAndDespawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity hostEntity = ModEntities.BB_KONGOU.get().create(level);
        Entity targetEntity = ModEntities.SS_KA.get().create(level);
        Entity floatingEntity = ModEntities.FLOATING_FORT.get().create(level);

        if (!(hostEntity instanceof IShipAttackBase host)) {
            throw new AssertionError("BB_KONGOU is not IShipAttackBase in floating fort test.");
        }
        if (!(floatingEntity instanceof EntityFloatingFort floatingFort)) {
            throw new AssertionError("FLOATING_FORT entity type did not create EntityFloatingFort.");
        }
        if (targetEntity == null) {
            throw new AssertionError("Failed to create SS_KA target entity.");
        }

        hostEntity.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        targetEntity.moveTo(3.0D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostEntity) || !level.addFreshEntity(targetEntity)) {
            throw new AssertionError("Failed to add host or target entity for floating fort test.");
        }

        floatingFort.initAttrs(host, targetEntity, 0, (float) hostEntity.getY());
        if (!level.addFreshEntity(floatingFort)) {
            throw new AssertionError("Failed to add floating fort entity for test.");
        }

        boolean attacked = floatingFort.attackEntityWithHeavyAmmo(targetEntity);
        if (!attacked) {
            throw new AssertionError("Floating fort heavy attack should detonate when target is close.");
        }
        if (floatingFort.isAlive()) {
            throw new AssertionError("Floating fort should despawn after heavy impact attack.");
        }

        hostEntity.discard();
        targetEntity.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileSearchlightPlacesLightOnlyAtNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in searchlight test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for searchlight test.");
        }

        hostile.setStateMinor(ID.M.LevelSearchlight, 1);
        BlockPos lightPos = hostile.blockPosition().above(2);

        level.setDayTime(6000L);
        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).isAir()) {
            throw new AssertionError("Searchlight should not place light block during daytime.");
        }

        level.setDayTime(13000L);
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).is(ModBlocks.LIGHT_AIR.get())) {
            throw new AssertionError("Searchlight should place LIGHT_AIR block at night.");
        }

        hostile.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileSearchlightSkipsWhenNoFuel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in no-fuel searchlight test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for no-fuel searchlight test.");
        }

        hostile.setStateMinor(ID.M.LevelSearchlight, 1);
        hostile.setStateFlag(ID.F.NoFuel, true);
        BlockPos lightPos = hostile.blockPosition().above(2);

        level.setDayTime(13000L);
        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).isAir()) {
            throw new AssertionError("Searchlight should not place LIGHT_AIR when NoFuel flag is set.");
        }

        hostile.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileSearchlightSkipsWhenNotAlive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in not-alive searchlight test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for not-alive searchlight test.");
        }

        hostile.setStateMinor(ID.M.LevelSearchlight, 1);
        BlockPos lightPos = hostile.blockPosition().above(2);

        level.setDayTime(13000L);
        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        hostile.discard();
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).isAir()) {
            throw new AssertionError("Searchlight should not place LIGHT_AIR when entity is not alive.");
        }

        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileSearchlightNightWindowBoundaries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in boundary searchlight test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for boundary searchlight test.");
        }

        hostile.setStateMinor(ID.M.LevelSearchlight, 1);
        BlockPos lightPos = hostile.blockPosition().above(2);

        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        level.setDayTime(12499L);
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).isAir()) {
            throw new AssertionError("Searchlight should not place LIGHT_AIR at time 12499.");
        }

        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        level.setDayTime(12500L);
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).is(ModBlocks.LIGHT_AIR.get())) {
            throw new AssertionError("Searchlight should place LIGHT_AIR at time 12500.");
        }

        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        level.setDayTime(23500L);
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).is(ModBlocks.LIGHT_AIR.get())) {
            throw new AssertionError("Searchlight should place LIGHT_AIR at time 23500.");
        }

        level.setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        level.setDayTime(23501L);
        hostile.updateSearchlight();
        if (!level.getBlockState(lightPos).isAir()) {
            throw new AssertionError("Searchlight should not place LIGHT_AIR at time 23501.");
        }

        hostile.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipPickItemGoalRespectsFuelAndPickFlag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KONGOU.get().create(level);
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in pick-item goal test.");
        }

        // Position relative to this test's own structure: absolute world
        // coordinates are shared by every test here, and game tests run
        // concurrently, so another test's dropped items would decide the result.
        Vec3 shipPos = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        BlockPos clearFrom = BlockPos.containing(shipPos.x - 2D, shipPos.y - 1D, shipPos.z - 2D);
        BlockPos clearTo = BlockPos.containing(shipPos.x + 2D, shipPos.y + 3D, shipPos.z + 2D);
        for (BlockPos pos : BlockPos.betweenClosed(clearFrom, clearTo)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        ship.moveTo(shipPos.x, shipPos.y, shipPos.z, 0F, 0F);
        if (!level.addFreshEntity(ship)) {
            throw new AssertionError("Failed to add ship entity for pick-item goal test.");
        }

        // The goal is only usable when there is something to pick up, so the
        // guard flags can only be exercised with an item actually present.
        ItemEntity bait = new ItemEntity(level, shipPos.x + 1D, shipPos.y, shipPos.z,
                new ItemStack(Items.STICK));
        if (!level.addFreshEntity(bait)) {
            throw new AssertionError("Failed to add bait item for pick-item goal test.");
        }

        // [PORT] 1.10.2 -> 1.20.1: lock AI guard behavior for item pickup goal.
        ShipPickItemGoal goal = new ShipPickItemGoal(ship, 6.0F);

        ship.setStateFlag(ID.F.PickItem, true);
        ship.setStateFlag(ID.F.NoFuel, false);
        if (!goal.canUse()) {
            throw new AssertionError("ShipPickItemGoal should be usable when PickItem=true and NoFuel=false.");
        }

        ship.setStateFlag(ID.F.NoFuel, true);
        if (goal.canUse()) {
            throw new AssertionError("ShipPickItemGoal should be blocked when NoFuel=true.");
        }

        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PickItem, false);
        if (goal.canUse()) {
            throw new AssertionError("ShipPickItemGoal should be blocked when PickItem=false.");
        }

        ship.setStateFlag(ID.F.PickItem, true);
        ship.setOrderedToSit(true);
        if (goal.canUse()) {
            throw new AssertionError("ShipPickItemGoal should be blocked when ship is sitting.");
        }
        ship.setOrderedToSit(false);

        ship.setStateMinor(ID.M.CraneState, 1);
        if (goal.canUse()) {
            throw new AssertionError("ShipPickItemGoal should be blocked when CraneState > 0.");
        }
        ship.setStateMinor(ID.M.CraneState, 0);

        ship.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void yamatoBeamTravelsAfterInitialization(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity hostEntity = ModEntities.BB_KONGOU.get().create(level);
        if (!(hostEntity instanceof IShipAttackBase host)) {
            throw new AssertionError("BB_KONGOU does not implement IShipAttackBase for beam travel test.");
        }

        hostEntity.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        EntityProjectileBeam beam = new EntityProjectileBeam(ModEntities.PROJECTILE_BEAM.get(), level);
        beam.initBeam(host, 1.0D, 0.0D, 0.0D, 1.0F);
        Vec3 initialPosition = beam.position();
        beam.tick();

        if (beam.position().distanceToSqr(initialPosition) < 15.99D) {
            throw new AssertionError("Yamato beam did not travel four blocks after one tick. initial="
                    + initialPosition + " actual=" + beam.position());
        }

        hostEntity.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void goalThrottlesFireOnBothTickParities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KONGOU.get().create(level);
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in goal throttle parity test.");
        }

        assertStartInitializesThrottleFields(new ShipPickItemGoal(ship, 6.0F), ship, 101,
                "nextItemScanTick");
        assertStartInitializesThrottleFields(new ShipGuardingGoal(ship), ship, 101,
                "nextAttrTick", "nextFindTargetTick", "nextGuardPosTick");
        assertStartInitializesThrottleFields(new ShipFollowOwnerGoal(ship), ship, 101,
                "nextOwnerResolveTick", "nextParticleTick");
        assertStartInitializesThrottleFields(new ShipRangeAttackGoal(ship), ship, 101,
                "nextAttrTick", "nextRepathTick");
        assertStartInitializesThrottleFields(new ShipAttackOnCollideGoal(ship, 1.0D), ship, 101,
                "nextRepathTick");

        Entity carrierEntity = ModEntities.CV_AKAGI.get().create(level);
        if (!(carrierEntity instanceof com.lulan.shincolle.entity.IShipAircraftAttack carrier)) {
            throw new AssertionError("CV_AKAGI is not IShipAircraftAttack in goal throttle parity test.");
        }
        assertStartInitializesThrottleFields(new ShipCarrierAttackGoal(carrier), carrierEntity, 101,
                "nextAttrTick", "nextRepathTick");

        Entity airplaneEntity = ModEntities.AIRPLANE.get().create(level);
        if (!(airplaneEntity instanceof BasicEntityAirplane airplane)) {
            throw new AssertionError("AIRPLANE is not BasicEntityAirplane in goal throttle parity test.");
        }
        assertStartInitializesThrottleFields(new ShipAircraftAttackGoal(airplane), airplane, 101,
                "nextCirclePathTick");

        int evenFires = countPickItemThrottleFires(ship, 2);
        int oddFires = countPickItemThrottleFires(ship, 3);
        if (evenFires != 8 || oddFires != 8) {
            throw new AssertionError("Goal throttle should fire eight times on both tick parities over 128 ticks."
                    + " even=" + evenFires + " odd=" + oddFires);
        }

        Entity targetEntity = ModEntities.SS_KA.get().create(level);
        if (!(targetEntity instanceof LivingEntity target)) {
            throw new AssertionError("SS_KA is not LivingEntity in goal throttle parity test.");
        }

        Vec3 shipPos = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        ship.moveTo(shipPos.x, shipPos.y, shipPos.z, 0F, 0F);
        target.moveTo(shipPos.x + 16D, shipPos.y, shipPos.z, 0F, 0F);
        if (!level.addFreshEntity(ship) || !level.addFreshEntity(target)) {
            throw new AssertionError("Failed to add entities for melee throttle parity test.");
        }
        ship.setTarget(target);

        ShipAttackOnCollideGoal meleeGoal = new ShipAttackOnCollideGoal(ship, 1.0D);
        if (!meleeGoal.canUse()) {
            throw new AssertionError("Melee goal should acquire the live target in throttle parity test.");
        }
        assertGoalThrottleTiming(meleeGoal, ship, "nextRepathTick", 32, 2);
        assertGoalThrottleTiming(meleeGoal, ship, "nextRepathTick", 32, 3);

        assertDeclaredIntFields(ShipPickItemGoal.class, "nextItemScanTick");
        assertDeclaredIntFields(ShipGuardingGoal.class,
                "nextAttrTick", "nextFindTargetTick", "nextGuardPosTick");
        assertDeclaredIntFields(ShipFollowOwnerGoal.class,
                "nextOwnerResolveTick", "nextParticleTick");
        assertDeclaredIntFields(ShipRangeAttackGoal.class,
                "nextAttrTick", "nextRepathTick");
        assertDeclaredIntFields(ShipCarrierAttackGoal.class,
                "nextAttrTick", "nextRepathTick");
        assertDeclaredIntFields(ShipAttackOnCollideGoal.class, "nextRepathTick");
        assertDeclaredIntFields(ShipAircraftAttackGoal.class, "nextCirclePathTick");

        target.discard();
        ship.discard();
        carrierEntity.discard();
        airplaneEntity.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipFollowOwnerGoalRespectsCoreGuards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KONGOU.get().create(level);
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in follow-owner goal test.");
        }

        ServerPlayer owner = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "shincolle_follow_owner");
        owner.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);

        ship.moveTo(24.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(ship)) {
            throw new AssertionError("Failed to add ship entity for follow-owner goal test.");
        }

        // [PORT] 1.10.2 -> 1.20.1: lock owner-follow activation and guard branches.
        ship.tame(owner);
        ship.setOwnerUUID(owner.getUUID());
        ship.setEntitySit(false);
        ship.setStateFlag(ID.F.CanFollow, true);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 120);
        ship.setStateMinor(ID.M.FollowMin, 2);
        ship.setStateMinor(ID.M.FollowMax, 6);

        ShipFollowOwnerGoal goal = new ShipFollowOwnerGoal(ship);
        boolean ownerResolved = ship.getOwner() != null;

        if (ownerResolved) {
            if (!goal.canUse()) {
                throw new AssertionError(
                        "ShipFollowOwnerGoal should be usable with valid owner and follow conditions.");
            }

            ship.setStateFlag(ID.F.CanFollow, false);
            if (goal.canUse()) {
                throw new AssertionError("ShipFollowOwnerGoal should be blocked when CanFollow=false.");
            }

            ship.setStateFlag(ID.F.CanFollow, true);
            ship.setStateMinor(ID.M.NumGrudge, 0);
            if (goal.canUse()) {
                throw new AssertionError("ShipFollowOwnerGoal should be blocked when NumGrudge<=0.");
            }

            ship.setStateMinor(ID.M.NumGrudge, 120);
            ship.setStateMinor(ID.M.CraneState, 1);
            if (goal.canUse()) {
                throw new AssertionError("ShipFollowOwnerGoal should be blocked when CraneState>=1.");
            }

            ship.setStateMinor(ID.M.CraneState, 0);
            ship.setEntitySit(true);
            if (goal.canUse()) {
                throw new AssertionError("ShipFollowOwnerGoal should be blocked when ship is sitting.");
            }
        } else {
            if (goal.canUse()) {
                throw new AssertionError(
                        "ShipFollowOwnerGoal should not activate when owner resolution is unavailable.");
            }
        }

        ship.discard();
        helper.succeed();
    }

    // 2026/04/15：GitHub Copilotによって追加
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void lightCruiserSkillAttackGoalUsesLegacyPriority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        assertLightCruiserGoalPriority(level, ModEntities.CL_TENRYUU.get(), "cl_tenryuu");
        assertLightCruiserGoalPriority(level, ModEntities.CL_TATSUTA.get(), "cl_tatsuta");

        helper.succeed();
    }

    // 2026/04/15：GitHub Copilotによって追加
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountFollowHostTeleportsWhenFarAndUnridden(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity hostEntity = ModEntities.BB_KONGOU.get().create(level);
        if (!(hostEntity instanceof BasicEntityShip host)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in mount-follow test.");
        }

        Entity mountEntity = ModEntities.MOUNT_BAH.get().create(level);
        if (!(mountEntity instanceof BasicEntityMount mount)) {
            throw new AssertionError("MOUNT_BAH is not BasicEntityMount in mount-follow test.");
        }

        host.moveTo(48.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        mount.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);

        if (!level.addFreshEntity(host)) {
            throw new AssertionError("Failed to add host ship entity in mount-follow test.");
        }
        if (!level.addFreshEntity(mount)) {
            throw new AssertionError("Failed to add mount entity in mount-follow test.");
        }

        mount.setHost(host);
        mount.setAIList();

        double initialDistSq = mount.distanceToSqr(host);
        if (initialDistSq <= 1024.0D) {
            throw new AssertionError("Mount-follow test requires long initial distance. actual=" + initialDistSq);
        }

        for (int i = 0; i < 20; i++) {
            mount.tick();
            if (mount.distanceToSqr(host) <= 4.0D) {
                break;
            }
        }

        double finalDistSq = mount.distanceToSqr(host);
        if (finalDistSq > 4.0D) {
            throw new AssertionError("Mount should follow/teleport to host when far and unridden. finalDistSq="
                    + finalDistSq);
        }

        mount.discard();
        host.discard();
        helper.succeed();
    }

    // 2026/04/15：GitHub Copilotによって追加
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mountFollowHostBlockedWhenRidden(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity hostEntity = ModEntities.BB_KONGOU.get().create(level);
        if (!(hostEntity instanceof BasicEntityShip host)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in mount-ridden test.");
        }

        Entity mountEntity = ModEntities.MOUNT_BAH.get().create(level);
        if (!(mountEntity instanceof BasicEntityMount mount)) {
            throw new AssertionError("MOUNT_BAH is not BasicEntityMount in mount-ridden test.");
        }

        ServerPlayer rider = createFollowTestOwner(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                "shincolle_mount_rider");

        host.moveTo(48.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        mount.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        rider.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);

        if (!level.addFreshEntity(host)) {
            throw new AssertionError("Failed to add host ship entity in mount-ridden test.");
        }
        if (!level.addFreshEntity(mount)) {
            throw new AssertionError("Failed to add mount entity in mount-ridden test.");
        }

        mount.setHost(host);
        mount.setAIList();

        if (!rider.startRiding(mount, true)) {
            throw new AssertionError("Failed to mount rider onto BasicEntityMount in mount-ridden test.");
        }

        double initialDistSq = mount.distanceToSqr(host);
        if (initialDistSq <= 1024.0D) {
            throw new AssertionError("Mount-ridden test requires long initial distance. actual=" + initialDistSq);
        }

        for (int i = 0; i < 20; i++) {
            mount.tick();
        }

        double finalDistSq = mount.distanceToSqr(host);
        if (finalDistSq < 256.0D) {
            throw new AssertionError("Mount follow should be blocked while ridden. finalDistSq=" + finalDistSq);
        }

        rider.stopRiding();
        mount.discard();
        host.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipSpawnEggSpecificClassPriorityAndLegacyTypeConversion(GameTestHelper helper) {
        ItemStack classOnly = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        ShipSpawnEgg.setShipClass(classOnly, ID.ShipClass.DDShimakaze);

        ItemStack mixed = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        CompoundTag mixedTag = mixed.getOrCreateTag();
        mixedTag.putByte("BuildType", (byte) 1);
        mixedTag.putInt(ShipSpawnEgg.TAG_SHIP_TYPE, ID.ShipClass.DDShimakaze + 2);

        int convertedClass = ShipSpawnEgg.getShipClass(mixed);
        if (convertedClass != ID.ShipClass.DDShimakaze) {
            throw new AssertionError("Legacy ShipType metadata conversion failed. expected="
                    + ID.ShipClass.DDShimakaze + " actual=" + convertedClass);
        }

        String expectedDescription = classOnly.getDescriptionId();
        String actualDescription = mixed.getDescriptionId();
        if (!expectedDescription.equals(actualDescription)) {
            throw new AssertionError("Specific-class egg name should override BuildType name. expected="
                    + expectedDescription + " actual=" + actualDescription);
        }

        int expectedIcon = ShipSpawnEgg.getEggIcon(classOnly);
        int actualIcon = ShipSpawnEgg.getEggIcon(mixed);
        if (expectedIcon != actualIcon) {
            throw new AssertionError("Specific-class egg icon should override BuildType icon. expected="
                    + expectedIcon + " actual=" + actualIcon);
        }

        ItemStack hostileLegacy = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        hostileLegacy.getOrCreateTag().putInt(ShipSpawnEgg.TAG_SHIP_TYPE,
                ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.DDI + 2);
        int hostileConverted = ShipSpawnEgg.getShipClass(hostileLegacy);
        int expectedHostileClass = ShipSpawnEgg.MOB_OFFSET + ID.ShipClass.DDI;
        if (hostileConverted != expectedHostileClass) {
            throw new AssertionError("Hostile legacy ShipType metadata conversion failed. expected="
                    + expectedHostileClass + " actual=" + hostileConverted);
        }

        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipSpawnEggIconMappingMatchesLegacyCategories(GameTestHelper helper) {
        ItemStack smallBuild = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        smallBuild.getOrCreateTag().putByte("BuildType", (byte) 0);
        if (ShipSpawnEgg.getEggIcon(smallBuild) != 0) {
            throw new AssertionError("Small build egg icon mismatch. expected=0 actual="
                    + ShipSpawnEgg.getEggIcon(smallBuild));
        }

        ItemStack largeBuild = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        largeBuild.getOrCreateTag().putByte("BuildType", (byte) 1);
        if (ShipSpawnEgg.getEggIcon(largeBuild) != 1) {
            throw new AssertionError("Large build egg icon mismatch. expected=1 actual="
                    + ShipSpawnEgg.getEggIcon(largeBuild));
        }

        assertEggIconForShipClass(ID.ShipClass.DDI, 2);
        assertEggIconForShipClass(ID.ShipClass.CLTenryuu, 3);
        assertEggIconForShipClass(ID.ShipClass.CAAtago, 4);
        assertEggIconForShipClass(ID.ShipClass.BBKongou, 5);
        assertEggIconForShipClass(ID.ShipClass.APWA, 6);
        assertEggIconForShipClass(ID.ShipClass.SSKA, 7);
        assertEggIconForShipClass(ID.ShipClass.CVWD, 8);
        assertEggIconForShipClass(ID.ShipClass.CVHime, 9);
        assertEggIconForShipClass(ID.ShipClass.CVKaga, 10);

        assertEggIconForShipClass(ID.ShipClass.DDI + ShipSpawnEgg.MOB_OFFSET, 2);
        assertEggIconForShipClass(ID.ShipClass.CVKaga + ShipSpawnEgg.MOB_OFFSET, 10);

        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipSpawnEggModelLayerAssignmentsMatchLegacy(GameTestHelper helper) {
        // [PORT] 1.10.2 -> 1.20.1: lock legacy icon->texture assignment to avoid silent
        // model drift in spawn eggs.
        assertModelLayer0("ship_spawn_egg.json", "shincolle:item/ship_spawn_egg_primary");
        assertModelLayer0("ship_spawn_egg_l.json", "shincolle:item/ship_spawn_egg_l");
        assertModelLayer0("ship_spawn_egg_dd.json", "shincolle:item/ship_spawn_egg_0");
        assertModelLayer0("ship_spawn_egg_cl.json", "shincolle:item/ship_spawn_egg_1");
        assertModelLayer0("ship_spawn_egg_ca.json", "shincolle:item/ship_spawn_egg_2");
        assertModelLayer0("ship_spawn_egg_bb.json", "shincolle:item/ship_spawn_egg_3");
        assertModelLayer0("ship_spawn_egg_ap.json", "shincolle:item/ship_spawn_egg_4");
        assertModelLayer0("ship_spawn_egg_ss.json", "shincolle:item/ship_spawn_egg_5");
        assertModelLayer0("ship_spawn_egg_wd.json", "shincolle:item/ship_spawn_egg_6");
        assertModelLayer0("ship_spawn_egg_hime.json", "shincolle:item/ship_spawn_egg_7");
        assertModelLayer0("ship_spawn_egg_cv.json", "shincolle:item/ship_spawn_egg_8");

        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileGoalListContainsWanderAndOpenDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in hostile goal list test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for goal list test.");
        }

        invokeNoArgProtected(hostile, "clearAITasks");
        invokeNoArgProtected(hostile, "setAIList");

        GoalSelector selector = extractGoalSelector(hostile);
        boolean hasWander = false;
        boolean hasOpenDoor = false;

        for (WrappedGoal wrappedGoal : selector.getAvailableGoals()) {
            Goal goal = wrappedGoal.getGoal();
            if (goal instanceof ShipHostileWanderGoal) {
                hasWander = true;
            }
            if (goal instanceof ShipOpenDoorGoal) {
                hasOpenDoor = true;
            }
        }

        if (!hasWander || !hasOpenDoor) {
            throw new AssertionError("Hostile goal list missing expected mobility goals. hasWander="
                    + hasWander + " hasOpenDoor=" + hasOpenDoor);
        }

        hostile.discard();
        helper.succeed();
    }

    // 2026/04/15：GitHub Copilotによって追加
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileTargetGoalPrioritiesMatchLegacy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in target-priority test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for target-priority test.");
        }

        invokeNoArgProtected(hostile, "clearAITargetTasks");
        invokeNoArgProtected(hostile, "setAITargetList");

        GoalSelector targetSelector = extractTargetSelector(hostile);
        int revengePriority = findGoalPriority(targetSelector, ShipRevengeTargetGoal.class);
        int rangePriority = findGoalPriority(targetSelector, ShipRangeTargetGoal.class);

        if (revengePriority != 1) {
            throw new AssertionError("Hostile revenge target priority mismatch. expected=1 actual=" + revengePriority);
        }
        if (rangePriority != 3) {
            throw new AssertionError("Hostile range target priority mismatch. expected=3 actual=" + rangePriority);
        }

        hostile.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileShipEggDropRatesMatchScaleLevels(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in egg drop test.");
        }

        final int samples = 3000;
        int[] minimumDrops = {450, 850, 2550, samples};
        int[] maximumDrops = {750, 1150, 2850, samples};
        int expectedShipClass = hostile.getShipClass();

        for (int scaleLevel = 0; scaleLevel <= 3; scaleLevel++) {
            hostile.initAttrs(scaleLevel);
            int drops = 0;
            for (int attempt = 0; attempt < samples; attempt++) {
                ItemStack egg = hostile.getDropEgg();
                if (!egg.isEmpty()) {
                    if (ShipSpawnEgg.getShipClass(egg) != expectedShipClass) {
                        throw new AssertionError("Hostile drop egg lost its ship class.");
                    }
                    drops++;
                }
            }

            if (drops < minimumDrops[scaleLevel] || drops > maximumDrops[scaleLevel]) {
                throw new AssertionError("Unexpected hostile egg drop rate for scale=" + scaleLevel
                        + " drops=" + drops);
            }
        }

        hostile.discard();
        helper.succeed();
    }

    // 2026/04/20：GitHub Copilotによって追加
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileRangeTargetGoalAcquiresFriendlyShip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity hostileEntity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        Entity friendlyEntity = ModEntities.BB_KONGOU.get().create(level);

        if (!(hostileEntity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in hostile target-acquire test.");
        }
        if (!(friendlyEntity instanceof BasicEntityShip friendly)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in hostile target-acquire test.");
        }

        hostile.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        friendly.moveTo(4.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);

        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for hostile target-acquire test.");
        }
        if (!level.addFreshEntity(friendly)) {
            throw new AssertionError("Failed to add friendly ship for hostile target-acquire test.");
        }

        invokeNoArgProtected(hostile, "clearAITargetTasks");
        invokeNoArgProtected(hostile, "setAITargetList");

        GoalSelector selector = extractTargetSelector(hostile);
        ShipRangeTargetGoal rangeGoal = null;
        for (WrappedGoal wrappedGoal : selector.getAvailableGoals()) {
            if (wrappedGoal.getGoal() instanceof ShipRangeTargetGoal goal) {
                rangeGoal = goal;
                break;
            }
        }

        if (rangeGoal == null) {
            throw new AssertionError("Hostile target selector has no ShipRangeTargetGoal instance.");
        }

        if (!rangeGoal.canUse()) {
            throw new AssertionError("Hostile ShipRangeTargetGoal failed to acquire nearby friendly ship.");
        }

        rangeGoal.start();
        Entity selected = hostile.getEntityTarget();
        if (!(selected instanceof BasicEntityShip)) {
            throw new AssertionError("Hostile acquired unexpected target type. expected=friendly ship actual="
                    + (selected == null ? "null" : selected.getType().toShortString()));
        }

        hostile.discard();
        friendly.discard();
        helper.succeed();
    }

    // 2026/04/20：GitHub Copilotによって追加
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyRangeTargetGoalAcquiresHostileShip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Entity friendlyEntity = ModEntities.BB_KONGOU.get().create(level);
        Entity hostileEntity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);

        if (!(friendlyEntity instanceof BasicEntityShip friendly)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in friendly target-acquire test.");
        }
        if (!(hostileEntity instanceof BasicEntityShipHostile hostile)) {
            throw new AssertionError("BB_KIRISHIMA_MOB is not BasicEntityShipHostile in friendly target-acquire test.");
        }

        // Position relative to this test's own structure. Absolute world
        // coordinates are shared by every test in the file, and because game
        // tests run concurrently the ship would otherwise find other tests'
        // entities stacked at the identical spot - at distance 0, so they win
        // the nearest-target sort and the assertion fails for the wrong reason.
        Vec3 friendlyPos = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        Vec3 hostilePos = helper.absoluteVec(new Vec3(4.5D, 1.0D, 0.5D));

        // The test structure sits underground, so carve an air pocket first:
        // the target selector checks line of sight, and entities buried in
        // deepslate can never see each other.
        BlockPos clearFrom = BlockPos.containing(
                Math.min(friendlyPos.x, hostilePos.x) - 1D, friendlyPos.y - 1D, friendlyPos.z - 1D);
        BlockPos clearTo = BlockPos.containing(
                Math.max(friendlyPos.x, hostilePos.x) + 1D, friendlyPos.y + 3D, friendlyPos.z + 1D);
        for (BlockPos pos : BlockPos.betweenClosed(clearFrom, clearTo)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        friendly.moveTo(friendlyPos.x, friendlyPos.y, friendlyPos.z, 0F, 0F);
        hostile.moveTo(hostilePos.x, hostilePos.y, hostilePos.z, 0F, 0F);

        if (!level.addFreshEntity(friendly)) {
            throw new AssertionError("Failed to add friendly ship for friendly target-acquire test.");
        }
        if (!level.addFreshEntity(hostile)) {
            throw new AssertionError("Failed to add hostile ship for friendly target-acquire test.");
        }

        invokeNoArgProtected(friendly, "clearAITargetTasks");
        invokeNoArgProtected(friendly, "setAITargetList");

        GoalSelector selector = extractTargetSelector(friendly);
        ShipRangeTargetGoal rangeGoal = null;
        for (WrappedGoal wrappedGoal : selector.getAvailableGoals()) {
            if (wrappedGoal.getGoal() instanceof ShipRangeTargetGoal goal) {
                rangeGoal = goal;
                break;
            }
        }

        if (rangeGoal == null) {
            throw new AssertionError("Friendly target selector has no ShipRangeTargetGoal instance.");
        }

        if (!rangeGoal.canUse()) {
            throw new AssertionError("Friendly ShipRangeTargetGoal failed to acquire nearby hostile ship.");
        }

        rangeGoal.start();
        Entity selected = friendly.getEntityTarget();
        if (!(selected instanceof BasicEntityShipHostile)) {
            throw new AssertionError("Friendly acquired unexpected target type. expected=hostile ship actual="
                    + (selected == null ? "null" : selected.getType().toShortString())
                    + (selected != null ? " id=" + selected.getId() : "")
                    + " selfId=" + friendly.getId());
        }

        friendly.discard();
        hostile.discard();
        helper.succeed();
    }

    // 2026/04/11：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipCalcRollTablesExcludeRemovedKanmusuClasses(GameTestHelper helper) {
        Set<Integer> forbiddenSmall = new HashSet<>();
        forbiddenSmall.add((int) ID.ShipClass.DDAkatsuki);
        forbiddenSmall.add((int) ID.ShipClass.DDHibiki);
        forbiddenSmall.add((int) ID.ShipClass.DDIkazuchi);
        forbiddenSmall.add((int) ID.ShipClass.DDInazuma);
        forbiddenSmall.add((int) ID.ShipClass.DDShimakaze);
        forbiddenSmall.add((int) ID.ShipClass.SSU511);
        forbiddenSmall.add((int) ID.ShipClass.SSRo500);
        forbiddenSmall.add((int) ID.ShipClass.CLTenryuu);
        forbiddenSmall.add((int) ID.ShipClass.CLTatsuta);
        forbiddenSmall.add((int) ID.ShipClass.CAAtago);
        forbiddenSmall.add((int) ID.ShipClass.CATakao);

        Set<Integer> forbiddenLarge = new HashSet<>();
        forbiddenLarge.add((int) ID.ShipClass.BBKongou);
        forbiddenLarge.add((int) ID.ShipClass.BBHiei);
        forbiddenLarge.add((int) ID.ShipClass.BBHaruna);
        forbiddenLarge.add((int) ID.ShipClass.BBKirishima);
        forbiddenLarge.add((int) ID.ShipClass.CVKaga);
        forbiddenLarge.add((int) ID.ShipClass.CVAkagi);
        forbiddenLarge.add((int) ID.ShipClass.BBNagato);
        forbiddenLarge.add((int) ID.ShipClass.BBYamato);

        RandomSource random = RandomSource.create(0x5C0FFEE);

        for (int i = 0; i < 2048; i++) {
            int[] matsSmall = new int[]{
                    60 + random.nextInt(700),
                    60 + random.nextInt(700),
                    60 + random.nextInt(700),
                    60 + random.nextInt(700)
            };
            int rolledSmall = ShipCalc.rollShipType(0, matsSmall, random);
            if (forbiddenSmall.contains(rolledSmall)) {
                throw new AssertionError("Small build roll returned forbidden class: " + rolledSmall);
            }

            int[] matsLarge = new int[]{
                    200 + random.nextInt(2400),
                    200 + random.nextInt(2400),
                    200 + random.nextInt(2400),
                    200 + random.nextInt(2400)
            };
            int rolledLarge = ShipCalc.rollShipType(1, matsLarge, random);
            if (forbiddenLarge.contains(rolledLarge)) {
                throw new AssertionError("Large build roll returned forbidden class: " + rolledLarge);
            }
        }

        helper.succeed();
    }

    // 2026/04/12：GitHub Copilotによって確認済み
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeShipyardMultiblockReformsAfterStaleCoreCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // [PORT] 1.10.2 -> 1.20.1: keep this fixture near template height so low-Y
        // worlds are covered by regression tests.
        BlockPos core = helper.absolutePos(new BlockPos(2, 4, 2));

        // Base layer: full 3x3 polymetal
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(core.offset(dx, -2, dz), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
            }
        }

        // Mid layer: four polymetal corners
        level.setBlock(core.offset(-1, -1, -1), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
        level.setBlock(core.offset(-1, -1, 1), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
        level.setBlock(core.offset(1, -1, -1), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
        level.setBlock(core.offset(1, -1, 1), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);

        // Top center: heavy grudge core block
        level.setBlock(core, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);

        BlockPos staleCorePos = core.offset(6, 0, 0);
        if (!(level.getBlockEntity(core) instanceof TileMultiGrudgeHeavy coreTile)) {
            throw new AssertionError("Large shipyard core tile was not created.");
        }
        coreTile.setCorePos(staleCorePos);

        BlockPos polyPos = core.offset(-1, -1, -1);
        if (!(level.getBlockEntity(polyPos) instanceof BasicTileMulti polyTile)) {
            throw new AssertionError("Polymetal multiblock tile was not created.");
        }
        polyTile.setCorePos(staleCorePos);

        int formType = MulitBlockHelper.checkMultiBlockForm(level, core.getX(), core.getY(), core.getZ());
        if (formType <= 0) {
            throw new AssertionError("Large shipyard form check failed after stale core cleanup: " + formType);
        }
        MulitBlockHelper.setupStructure(level, core.getX(), core.getY(), core.getZ(), formType);

        if (!(level.getBlockEntity(core) instanceof TileMultiGrudgeHeavy formedCore) || !formedCore.hasCorePos()) {
            throw new AssertionError("Large shipyard core was not formed after stale core cleanup.");
        }
        if (!formedCore.getCorePos().equals(core)) {
            throw new AssertionError("Core position mismatch after structure setup: " + formedCore.getCorePos());
        }

        if (!(level.getBlockEntity(polyPos) instanceof BasicTileMulti formedPoly) || !formedPoly.hasCorePos()) {
            throw new AssertionError("Polymetal tile did not join formed structure.");
        }
        if (!formedPoly.getCorePos().equals(core)) {
            throw new AssertionError("Polymetal core position mismatch: " + formedPoly.getCorePos());
        }

        // Verify helper path also sees structure as occupied after successful setup.
        int checkResult = MulitBlockHelper.checkMultiBlockForm(level, core.getX(), core.getY(), core.getZ());
        if (checkResult != -1) {
            throw new AssertionError("Structure occupancy check should fail once formed, got: " + checkResult);
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipyardsRejectInvalidBuildsAndAllowInstantConstruction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos largePos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(largePos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(largePos) instanceof TileMultiGrudgeHeavy large)) {
            throw new AssertionError("Large shipyard tile was not created.");
        }
        large.setBuildType(1);
        large.setPowerGoal(1); // Simulates stale saved state from an earlier valid recipe.
        for (int i = 0; i < 4; i++) {
            large.setMatStock(i, 1000);
            large.setMatBuild(i, 1); // Below LargeRecipes.MIN_MATERIAL.
        }
        if (large.canBuild()) {
            throw new AssertionError("Large shipyard accepted an invalid material recipe.");
        }

        BlockPos smallPos = helper.absolutePos(new BlockPos(6, 2, 2));
        level.setBlock(smallPos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(smallPos) instanceof TileEntitySmallShipyard small)) {
            throw new AssertionError("Small shipyard tile was not created.");
        }
        small.setBuildType(3);
        small.getInventory().setStackInSlot(TileEntitySmallShipyard.SLOT_INPUT_END,
                new ItemStack(ModItems.INSTANT_CON_MAT.get()));
        TileEntitySmallShipyard.serverTick(level, smallPos, level.getBlockState(smallPos), small);
        if (small.getInventory().getStackInSlot(TileEntitySmallShipyard.SLOT_INPUT_END).isEmpty()) {
            throw new AssertionError("Small shipyard consumed instant material without valid inputs.");
        }

        for (int material = 0; material < 4; material++) {
            small.setMatStock(material, 16);
            small.setMatBuild(material, 16);
        }
        TileEntitySmallShipyard.serverTick(level, smallPos, level.getBlockState(smallPos), small);
        if (!small.getInventory().getStackInSlot(TileEntitySmallShipyard.SLOT_INPUT_END).isEmpty()) {
            throw new AssertionError("Small shipyard did not consume instant material for a valid build.");
        }
        if (small.getInventory().getStackInSlot(TileEntitySmallShipyard.SLOT_OUTPUT).isEmpty()) {
            throw new AssertionError("Instant construction did not complete the valid small build.");
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void smallShipyardProcessesUnifiedMaterialAndFuelInputs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos shipyardPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(shipyardPos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(shipyardPos) instanceof TileEntitySmallShipyard shipyard)) {
            throw new AssertionError("Small shipyard tile was not created for unified input test.");
        }

        shipyard.getInventory().setStackInSlot(0, new ItemStack(ModItems.ABYSS_METAL.get()));
        shipyard.getInventory().setStackInSlot(1, new ItemStack(Items.COAL));
        shipyard.getInventory().setStackInSlot(2, new ItemStack(ModItems.POLYMETAL_NODULE.get()));
        shipyard.getInventory().setStackInSlot(3, new ItemStack(ModItems.GRUDGE.get()));
        shipyard.getInventory().setStackInSlot(4, new ItemStack(ModItems.AMMO.get()));

        TileEntitySmallShipyard.serverTick(level, shipyardPos, level.getBlockState(shipyardPos), shipyard);

        for (int material = 0; material < 4; material++) {
            if (shipyard.getMatStock(material) <= 0) {
                throw new AssertionError("Unified input did not add small shipyard stock " + material);
            }
        }
        if (shipyard.getPowerRemained() <= 0) {
            throw new AssertionError("Unified input did not convert coal into small shipyard power.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipyardsAcceptLavaThroughFluidCapability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos smallPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(smallPos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(smallPos) instanceof TileEntitySmallShipyard small)) {
            throw new AssertionError("Small shipyard tile was not created for fluid test.");
        }
        IFluidHandler smallFluid = small.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new AssertionError("Small shipyard did not expose a fluid handler."));
        assertLavaFuelConversion(smallFluid, small.getPowerRemained(), small.getFuelMagni(),
                () -> TileEntitySmallShipyard.serverTick(level, smallPos, level.getBlockState(smallPos), small),
                small::getPowerRemained, "small shipyard");

        BlockPos largePos = helper.absolutePos(new BlockPos(6, 2, 2));
        level.setBlock(largePos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(largePos) instanceof TileMultiGrudgeHeavy large)) {
            throw new AssertionError("Large shipyard tile was not created for fluid test.");
        }
        IFluidHandler largeFluid = large.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new AssertionError("Large shipyard did not expose a fluid handler."));
        assertLavaFuelConversion(largeFluid, large.getPowerRemained(), large.getFuelMagni(),
                () -> TileMultiGrudgeHeavy.serverTick(level, largePos, level.getBlockState(largePos), large),
                large::getPowerRemained, "large shipyard");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeShipyardRecyclesShipSpawnEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos shipyardPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(shipyardPos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(shipyardPos) instanceof TileMultiGrudgeHeavy shipyard)) {
            throw new AssertionError("Large shipyard tile was not created for spawn egg recycling.");
        }

        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        ShipSpawnEgg.setShipClass(egg, ID.ShipClass.DDI);
        if (!shipyard.isItemValidForSlot(2, egg)) {
            throw new AssertionError("Large shipyard rejected a ship spawn egg input.");
        }
        shipyard.getInventory().setStackInSlot(2, egg);
        TileMultiGrudgeHeavy.serverTick(level, shipyardPos, level.getBlockState(shipyardPos), shipyard);

        if (!shipyard.getInventory().getStackInSlot(2).isEmpty()) {
            throw new AssertionError("Large shipyard did not consume a recycled ship spawn egg.");
        }
        for (int material = 0; material < 4; material++) {
            if (shipyard.getMatStock(material) <= 0) {
                throw new AssertionError("Ship spawn egg recycling did not add material stock index " + material);
            }
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeShipyardOldFuelSlotAcceptsMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos shipyardPos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(shipyardPos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(shipyardPos) instanceof TileMultiGrudgeHeavy shipyard)) {
            throw new AssertionError("Large shipyard tile was not created for unified slot test.");
        }

        int inputSlot = TileMultiGrudgeHeavy.SLOT_INPUT_START;
        shipyard.getInventory().setStackInSlot(inputSlot, new ItemStack(ModItems.ABYSS_METAL.get()));
        TileMultiGrudgeHeavy.serverTick(level, shipyardPos, level.getBlockState(shipyardPos), shipyard);

        if (shipyard.getMatStock(1) <= 0) {
            throw new AssertionError("Large shipyard old fuel slot did not add material stock.");
        }
        if (!shipyard.getInventory().getStackInSlot(inputSlot).isEmpty()) {
            throw new AssertionError("Large shipyard old fuel slot did not consume material input.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipyardStockPacketPreservesFullIntegerValues(GameTestHelper helper) {
        int[] expected = {32768, 65535, 500000, 1000000};
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new S2CShipyardStockPacket(37, expected).encode(buffer);

        S2CShipyardStockPacket decoded = new S2CShipyardStockPacket(buffer);
        if (decoded.getContainerId() != 37) {
            throw new AssertionError("Shipyard stock packet lost its container id.");
        }
        if (!Arrays.equals(decoded.getStocks(), expected)) {
            throw new AssertionError("Shipyard stock packet truncated full integer values: "
                    + Arrays.toString(decoded.getStocks()));
        }
        buffer.release();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void serverboundPacketsRejectOversizedAndTruncatedArrays(GameTestHelper helper) {
        FriendlyByteBuf exact = new FriendlyByteBuf(Unpooled.buffer());
        exact.writeByte(C2SInputPacket.MountMove);
        PacketHelper.writeIntArray(exact, new int[9]);
        new C2SInputPacket(exact);

        FriendlyByteBuf oversizedInput = new FriendlyByteBuf(Unpooled.buffer());
        oversizedInput.writeByte(C2SInputPacket.MountMove);
        oversizedInput.writeVarInt(10);
        assertPacketDecodeRejected(() -> new C2SInputPacket(oversizedInput),
                "C2SInputPacket accepted an oversized array length.");

        FriendlyByteBuf truncatedInput = new FriendlyByteBuf(Unpooled.buffer());
        truncatedInput.writeByte(C2SInputPacket.MountMove);
        truncatedInput.writeVarInt(9);
        for (int i = 0; i < 8; i++) {
            truncatedInput.writeInt(i);
        }
        assertPacketDecodeRejected(() -> new C2SInputPacket(truncatedInput),
                "C2SInputPacket accepted a truncated array payload.");

        FriendlyByteBuf oversizedGui = new FriendlyByteBuf(Unpooled.buffer());
        oversizedGui.writeByte(C2SGUIInputPacket.ShipBtn);
        oversizedGui.writeVarInt(8);
        assertPacketDecodeRejected(() -> new C2SGUIInputPacket(oversizedGui),
                "C2SGUIInputPacket accepted an oversized array length.");

        FriendlyByteBuf oversizedString = new FriendlyByteBuf(Unpooled.buffer());
        oversizedString.writeByte(C2SGUIInputPacket.ShipBtn);
        oversizedString.writeVarInt(0);
        oversizedString.writeBoolean(true);
        oversizedString.writeUtf("x".repeat(129));
        assertPacketDecodeRejected(() -> new C2SGUIInputPacket(oversizedString),
                "C2SGUIInputPacket accepted an overlength string.");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipInventoryInsertionIsAtomicAndCargoOnly(GameTestHelper helper) {
        CapaShipInventory inventory = new CapaShipInventory(CapaShipInventory.EquipSlots + 2, null);
        inventory.setStackInSlot(CapaShipInventory.EquipSlots, new ItemStack(Items.IRON_INGOT, 63));
        inventory.setStackInSlot(CapaShipInventory.EquipSlots + 1, new ItemStack(Items.STONE, 64));

        ItemStack rejected = new ItemStack(Items.IRON_INGOT, 2);
        if (inventory.addItemStackToInventory(rejected)) {
            throw new AssertionError("A full cargo inventory accepted a stack that did not completely fit.");
        }
        if (inventory.getStackInSlot(CapaShipInventory.EquipSlots).getCount() != 63
                || rejected.getCount() != 2) {
            throw new AssertionError("Failed cargo insertion partially mutated source or destination.");
        }
        if (inventory.getFirstSlotForItem() != -1) {
            throw new AssertionError("Empty equipment slots were exposed as general cargo slots.");
        }

        inventory.setStackInSlot(CapaShipInventory.EquipSlots + 1, ItemStack.EMPTY);
        ItemStack accepted = new ItemStack(Items.IRON_INGOT, 2);
        if (!inventory.addItemStackToInventory(accepted) || !accepted.isEmpty()) {
            throw new AssertionError("Cargo insertion failed despite sufficient total capacity.");
        }
        if (inventory.getStackInSlot(CapaShipInventory.EquipSlots).getCount() != 64
                || inventory.getStackInSlot(CapaShipInventory.EquipSlots + 1).getCount() != 1) {
            throw new AssertionError("Cargo insertion did not split the stack across available capacity.");
        }
        for (int slot = 0; slot < CapaShipInventory.EquipSlots; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                throw new AssertionError("Cargo insertion polluted equipment slot " + slot + ".");
            }
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentEnchantTypeControlsWeaponAndArmorStats(GameTestHelper helper) {
        float[] raw = new float[Attrs.AttrsLength];
        float[] enchant = new float[Attrs.AttrsLength];
        raw[ID.Attrs.ATK_L] = 10F;
        raw[ID.Attrs.DEF] = 0.5F;
        raw[ID.Attrs.XP] = 1F;
        raw[ID.Attrs.GRUDGE] = 1F;
        enchant[ID.Attrs.ATK_L] = 0.5F;
        enchant[ID.Attrs.DEF] = 0.5F;
        enchant[ID.Attrs.XP] = 0.25F;
        enchant[ID.Attrs.GRUDGE] = 0.25F;

        float[] weapon = EquipCalc.calcEquipStatWithEnchant(1, raw, enchant);
        float[] armor = EquipCalc.calcEquipStatWithEnchant(2, raw, enchant);
        assertFloatEquals(15F, weapon[ID.Attrs.ATK_L], "Weapon enchant did not modify attack.");
        assertFloatEquals(0.5F, weapon[ID.Attrs.DEF], "Weapon enchant incorrectly modified defense.");
        assertFloatEquals(1.25F, weapon[ID.Attrs.XP], "Weapon enchant did not modify XP gain.");
        assertFloatEquals(1F, weapon[ID.Attrs.GRUDGE], "Weapon enchant incorrectly modified grudge gain.");
        assertFloatEquals(10F, armor[ID.Attrs.ATK_L], "Armor enchant incorrectly modified attack.");
        assertFloatEquals(0.75F, armor[ID.Attrs.DEF], "Armor enchant did not modify defense.");
        assertFloatEquals(1F, armor[ID.Attrs.XP], "Armor enchant incorrectly modified XP gain.");
        assertFloatEquals(1.25F, armor[ID.Attrs.GRUDGE], "Armor enchant did not modify grudge gain.");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rangeTargetStopClearsOnlyItsOwnTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity friendlyEntity = ModEntities.BB_KONGOU.get().create(level);
        Entity hostileEntity = ModEntities.BB_KIRISHIMA_MOB.get().create(level);
        Entity replacement = EntityType.ZOMBIE.create(level);
        if (!(friendlyEntity instanceof BasicEntityShip friendly)
                || !(hostileEntity instanceof BasicEntityShipHostile hostile)
                || !(replacement instanceof Mob replacementMob)) {
            throw new AssertionError("Failed to create entities for target-goal stop test.");
        }

        ShipRangeTargetGoal goal = new ShipRangeTargetGoal(friendly);
        setRangeGoalTarget(goal, hostile);
        friendly.setTarget(hostile);
        goal.stop();
        if (friendly.getTarget() != null) {
            throw new AssertionError("Range target goal left its own stopped target on the mob.");
        }

        setRangeGoalTarget(goal, hostile);
        friendly.setTarget(replacementMob);
        goal.stop();
        if (friendly.getTarget() != replacementMob) {
            throw new AssertionError("Range target goal cleared a replacement target selected elsewhere.");
        }

        friendly.discard();
        hostile.discard();
        replacement.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void marriageRingScanChecksEveryInventoryAndOffhandStack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000015"), "shincolle_ring_scan"));
        player.getInventory().clearContent();

        ItemStack inactive = new ItemStack(ModItems.MARRIAGE_RING.get());
        ItemStack active = new ItemStack(ModItems.MARRIAGE_RING.get());
        active.getOrCreateTag().putBoolean("isActive", true);
        player.getInventory().setItem(0, inactive);
        player.getInventory().setItem(1, active);
        if (!MarriageRing.hasAnyRing(player) || !MarriageRing.hasActiveRing(player)) {
            throw new AssertionError("Ring state depended on the first matching inventory stack.");
        }

        active.getOrCreateTag().putBoolean("isActive", false);
        ItemStack activeOffhand = new ItemStack(ModItems.MARRIAGE_RING.get());
        activeOffhand.getOrCreateTag().putBoolean("isActive", true);
        player.getInventory().offhand.set(0, activeOffhand);
        if (!MarriageRing.hasActiveRing(player)) {
            throw new AssertionError("Active marriage ring in offhand was not detected.");
        }

        player.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneOwnerUuidPersistsAndRejectsAnotherPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer owner = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000016"), "shincolle_crane_owner"));
        FakePlayer other = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000017"), "shincolle_crane_other"));
        CapaTeitoku ownerCapa = owner.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        CapaTeitoku otherCapa = other.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (ownerCapa == null || otherCapa == null) {
            throw new AssertionError("Crane ownership test players have no Teitoku capability.");
        }
        ownerCapa.setPlayerUID(9401);
        otherCapa.setPlayerUID(9402);
        owner.getAbilities().instabuild = false;
        other.getAbilities().instabuild = false;

        BlockPos cranePos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(cranePos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(cranePos) instanceof TileEntityCrane crane)) {
            throw new AssertionError("Crane block did not create its block entity.");
        }
        if (!crane.claimOrVerifyOwner(owner) || crane.claimOrVerifyOwner(other)) {
            throw new AssertionError("Crane claim did not bind the authenticated owner UUID.");
        }
        if (!crane.canUse(owner) || (!other.hasPermissions(2) && crane.canUse(other))) {
            throw new AssertionError("Crane access control did not enforce its owner UUID.");
        }

        CompoundTag saved = crane.getUpdateTag();
        TileEntityCrane loaded = new TileEntityCrane(cranePos, ModBlocks.CRANE.get().defaultBlockState());
        loaded.load(saved);
        if (!owner.getUUID().equals(loaded.getOwnerUUID()) || loaded.getPlayerUID() != 9401) {
            throw new AssertionError("Crane owner identity did not survive NBT save/load.");
        }
        if (loaded.claimOrVerifyOwner(other)) {
            throw new AssertionError("Reloaded crane could be claimed by another player.");
        }

        Entity otherShipEntity = ModEntities.BB_KONGOU.get().create(level);
        Entity ownerShipEntity = ModEntities.BB_HIEI.get().create(level);
        if (!(otherShipEntity instanceof BasicEntityShip otherShip)
                || !(ownerShipEntity instanceof BasicEntityShip ownerShip)) {
            throw new AssertionError("Failed to create ships for crane ownership filter test.");
        }
        otherShip.setOwnerUUID(other.getUUID());
        otherShip.setPlayerUID(9402);
        ownerShip.setOwnerUUID(owner.getUUID());
        ownerShip.setPlayerUID(9401);
        otherShip.moveTo(cranePos.getX() + 0.5D, cranePos.getY() + 1D, cranePos.getZ() + 0.5D, 0F, 0F);
        ownerShip.moveTo(cranePos.getX() + 1.5D, cranePos.getY() + 1D, cranePos.getZ() + 0.5D, 0F, 0F);
        if (!level.addFreshEntity(otherShip) || !level.addFreshEntity(ownerShip)) {
            throw new AssertionError("Failed to add ships for crane ownership filter test.");
        }
        invokeNoArgProtected(crane, "checkCraningShip");
        if (crane.getDockedShip() != ownerShip) {
            throw new AssertionError("Crane selected another player's ship as its transfer destination.");
        }

        otherShip.discard();
        ownerShip.discard();

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void teamCooldownRejectsCreateAndDisbandReplay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000018"), "shincolle_team_cooldown"));
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null || !ServerDataManager.isInitialized()) {
            throw new AssertionError("Team cooldown test requires initialized server data and capability.");
        }
        int uid = 9501;
        capa.setPlayerUID(uid);
        ServerDataManager.removeTeamData(uid);

        capa.setTeamCooldown(10);
        if (ServerDataManager.teamCreate(player, "CooldownTest")) {
            throw new AssertionError("Team creation ignored an active cooldown.");
        }
        capa.setTeamCooldown(0);
        if (!ServerDataManager.teamCreate(player, "CooldownTest")) {
            throw new AssertionError("Team creation failed after cooldown expired.");
        }
        capa.setTeamCooldown(10);
        if (ServerDataManager.teamDisband(player) || ServerDataManager.getTeamData(uid) == null) {
            throw new AssertionError("Team disband replay ignored an active cooldown.");
        }
        capa.setTeamCooldown(0);
        if (!ServerDataManager.teamDisband(player) || ServerDataManager.getTeamData(uid) != null) {
            throw new AssertionError("Team disband failed after cooldown expired.");
        }

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipOwnerTransferUpdatesUuidAndNumericOwnerTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity entity = ModEntities.BB_KONGOU.get().create(level);
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("BB_KONGOU is not BasicEntityShip in owner-transfer test.");
        }
        FakePlayer newOwner = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000019"), "shincolle_new_owner"));
        CapaTeitoku capa = newOwner.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) {
            throw new AssertionError("Owner-transfer test player has no Teitoku capability.");
        }
        capa.setPlayerUID(9601);
        ship.setOwnerUUID(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        ship.setPlayerUID(1234);
        if (!level.addFreshEntity(ship) || !ServerDataManager.changeShipOwner(ship, newOwner)) {
            throw new AssertionError("Server owner transfer failed.");
        }
        // FakePlayer is not registered in ServerLevel's normal player lookup,
        // so TamableAnimal#isOwnedBy cannot resolve it even when the persisted
        // UUID is correct. Assert the two authoritative owner fields directly.
        if (!newOwner.getUUID().equals(ship.getOwnerUUID()) || ship.getPlayerUID() != 9601) {
            throw new AssertionError("Owner transfer left identity inconsistent. expectedUuid=" + newOwner.getUUID()
                    + " actualUuid=" + ship.getOwnerUUID() + " expectedUid=9601 actualUid=" + ship.getPlayerUID()
                    + " vanillaOwned=" + ship.isOwnedBy(newOwner));
        }

        int shipUid = ship.getShipUID();
        ship.discard();
        ServerDataManager.removeShipData(shipUid);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void targetClassPacketRequiresObservedNearbyEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000020"), "shincolle_target_class"));
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        Entity cow = EntityType.COW.create(level);
        if (capa == null || cow == null) {
            throw new AssertionError("Failed to create target-class packet test state.");
        }
        int uid = 9701;
        capa.setPlayerUID(uid);
        HashMap<Integer, String> oversizedLegacyList = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String name = "LegacyClass" + i;
            oversizedLegacyList.put(name.hashCode(), name);
        }
        ServerDataManager.setPlayerTargetClass(uid, oversizedLegacyList);
        HashMap<Integer, String> sanitized = ServerDataManager.getPlayerTargetClass(uid);
        if (sanitized == null || sanitized.size() != ServerDataManager.MAX_CUSTOM_TARGET_CLASSES) {
            throw new AssertionError("Oversized target-class state was not capped during migration.");
        }
        ServerDataManager.setPlayerTargetClass(uid, new HashMap<>());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POINTER.get()));

        Vec3 playerPos = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        Vec3 cowPos = helper.absoluteVec(new Vec3(3.5D, 2D, 1.5D));
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(playerPos.x - 1D, playerPos.y - 1D, playerPos.z - 1D),
                BlockPos.containing(cowPos.x + 1D, cowPos.y + 3D, cowPos.z + 1D))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0F, 0F);
        cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0F, 0F);
        if (!level.addFreshEntity(cow)) {
            throw new AssertionError("Failed to add observed entity for target-class packet test.");
        }

        C2SGUIInputPacket forged = new C2SGUIInputPacket(C2SGUIInputPacket.SetTarClass,
                new int[]{player.getId(), 0, cow.getId()}, "Zombie");
        invokePacketHandler(forged, "handleSetTarClass", player);
        if (ServerDataManager.hasPlayerTargetClass(uid, "Zombie")) {
            throw new AssertionError("Packet persisted a class name that did not match its target entity.");
        }

        String observedClass = cow.getClass().getSimpleName();
        C2SGUIInputPacket observed = new C2SGUIInputPacket(C2SGUIInputPacket.SetTarClass,
                new int[]{player.getId(), 0, cow.getId()}, observedClass);
        invokePacketHandler(observed, "handleSetTarClass", player);
        if (!ServerDataManager.hasPlayerTargetClass(uid, observedClass)) {
            throw new AssertionError("Packet rejected an observed nearby target class.");
        }

        ServerDataManager.setPlayerTargetClass(uid, observedClass);
        cow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void waypointPairingRequiresActualWaypointOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000013"), "shincolle_waypoint_owner"));
        ServerPlayer otherPlayer = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000014"), "shincolle_waypoint_other"));

        int ownerUid = 9100;
        owner.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> capa.setPlayerUID(ownerUid));
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos to = helper.absolutePos(new BlockPos(5, 2, 2));
        level.setBlock(from, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        level.setBlock(to, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        TileEntityWaypoint fromWaypoint = (TileEntityWaypoint) level.getBlockEntity(from);
        TileEntityWaypoint toWaypoint = (TileEntityWaypoint) level.getBlockEntity(to);
        if (fromWaypoint == null || toWaypoint == null) {
            throw new AssertionError("Waypoint block did not create its block entity.");
        }
        BlockWaypoint waypointBlock = (BlockWaypoint) ModBlocks.WAYPOINT.get();
        waypointBlock.setPlacedBy(level, from, level.getBlockState(from), owner,
                new ItemStack(ModItems.WAYPOINT_BLOCK_ITEM.get()));
        waypointBlock.setPlacedBy(level, to, level.getBlockState(to), owner,
                new ItemStack(ModItems.WAYPOINT_BLOCK_ITEM.get()));
        if (fromWaypoint.getPlayerUID() != ownerUid || !owner.getUUID().equals(fromWaypoint.getOwnerUUID())
                || toWaypoint.getPlayerUID() != ownerUid || !owner.getUUID().equals(toWaypoint.getOwnerUUID())) {
            throw new AssertionError("Waypoint placement did not persist its owner identity.");
        }

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TARGET_WRENCH.get()));
        otherPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TARGET_WRENCH.get()));
        Vec3 pairingPos = Vec3.atCenterOf(from);
        owner.moveTo(pairingPos.x, pairingPos.y, pairingPos.z, 0F, 0F);
        otherPlayer.moveTo(pairingPos.x, pairingPos.y, pairingPos.z, 0F, 0F);

        // A forged legacy UID is insufficient: the authenticated player's UUID
        // must be the owner before a route may be changed.
        TileEntityHelper.pairingWaypoints(otherPlayer, ownerUid, level, from, to);
        if (fromWaypoint.hasNextWaypoint()) {
            throw new AssertionError("A non-owner changed a waypoint route using a forged UID.");
        }

        TileEntityHelper.pairingWaypoints(owner, ownerUid, level, from, to);
        if (!to.equals(fromWaypoint.getNextWaypoint()) || !from.equals(toWaypoint.getLastWaypoint())) {
            throw new AssertionError("The actual owner could not pair their waypoints.");
        }
        helper.succeed();
    }

    private static void assertLavaFuelConversion(IFluidHandler fluidHandler, int initialPower, float fuelMagnifier,
                                                  Runnable serverTick, IntSupplier powerAfterTick, String shipyardName) {
        if (fluidHandler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) != 0) {
            throw new AssertionError(shipyardName + " accepted a non-lava fluid.");
        }
        if (fluidHandler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) != 1000) {
            throw new AssertionError(shipyardName + " did not accept one lava bucket through its fluid handler.");
        }

        serverTick.run();

        int expectedPower = initialPower + (int) (20000 * fuelMagnifier);
        if (powerAfterTick.getAsInt() != expectedPower) {
            throw new AssertionError(shipyardName + " converted lava to the wrong power value. expected="
                    + expectedPower + " actual=" + powerAfterTick.getAsInt());
        }
        if (!fluidHandler.getFluidInTank(0).isEmpty()) {
            throw new AssertionError(shipyardName + " did not consume the converted lava.");
        }
    }

    private static void assertPacketDecodeRejected(Runnable decoder, String message) {
        try {
            decoder.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void assertFloatEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.0001F) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void setRangeGoalTarget(ShipRangeTargetGoal goal, Entity target) {
        try {
            Field targetField = ShipRangeTargetGoal.class.getDeclaredField("targetEntity");
            targetField.setAccessible(true);
            targetField.set(goal, target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set range target goal state for regression test.", e);
        }
    }

    private static ServerPlayer createFollowTestOwner(GameTestHelper helper, ServerLevel level, UUID uuid,
                                                      String name) {
        try {
            Method m = GameTestHelper.class.getMethod("makeMockPlayer");
            Object obj = m.invoke(helper);
            if (obj instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback below.
        }

        return FakePlayerFactory.get(level, new GameProfile(uuid, name));
    }

    private static GoalSelector extractGoalSelector(Mob mob) {
        try {
            Field goalSelectorField = Mob.class.getDeclaredField("goalSelector");
            goalSelectorField.setAccessible(true);
            Object value = goalSelectorField.get(mob);
            if (value instanceof GoalSelector selector) {
                return selector;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect goal selector via reflection.", e);
        }

        throw new AssertionError("Failed to resolve goal selector via reflection.");
    }

    private static GoalSelector extractTargetSelector(Mob mob) {
        try {
            Field targetSelectorField = Mob.class.getDeclaredField("targetSelector");
            targetSelectorField.setAccessible(true);
            Object value = targetSelectorField.get(mob);
            if (value instanceof GoalSelector selector) {
                return selector;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect target selector via reflection.", e);
        }

        throw new AssertionError("Failed to resolve target selector via reflection.");
    }

    private static ServerBossEvent extractBossEvent(BasicEntityShipHostile hostile) {
        try {
            Field field = BasicEntityShipHostile.class.getDeclaredField("bossEvent");
            field.setAccessible(true);
            return (ServerBossEvent) field.get(hostile);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect hostile boss event via reflection.", e);
        }
    }

    private static void assertLightCruiserGoalPriority(ServerLevel level, EntityType<?> type, String id) {
        Entity entity = type.create(level);
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Entity is not BasicEntityShip in light cruiser AI test: " + id);
        }

        ship.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(ship)) {
            throw new AssertionError("Failed to add entity for light cruiser AI test: " + id);
        }

        invokeNoArgProtected(ship, "clearAITasks");
        invokeNoArgProtected(ship, "setAIList");

        GoalSelector selector = extractGoalSelector(ship);
        int skillPriority = findGoalPriority(selector, ShipSkillAttackGoal.class);
        int rangePriority = findGoalPriority(selector, ShipRangeAttackGoal.class);

        if (skillPriority != 0) {
            throw new AssertionError("SkillAttack goal priority mismatch for " + id + ". expected=0 actual="
                    + skillPriority);
        }

        if (rangePriority != 11) {
            throw new AssertionError("RangeAttack goal priority mismatch for " + id + ". expected=11 actual="
                    + rangePriority);
        }

        ship.discard();
    }

    private static int findGoalPriority(GoalSelector selector, Class<? extends Goal> goalClass) {
        int bestPriority = Integer.MAX_VALUE;
        for (WrappedGoal wrappedGoal : selector.getAvailableGoals()) {
            if (goalClass.isInstance(wrappedGoal.getGoal())) {
                bestPriority = Math.min(bestPriority, wrappedGoal.getPriority());
            }
        }

        return bestPriority == Integer.MAX_VALUE ? -1 : bestPriority;
    }

    private static void invokePacketHandler(C2SGUIInputPacket packet, String methodName, ServerPlayer player) {
        try {
            Method method = C2SGUIInputPacket.class.getDeclaredMethod(methodName, ServerPlayer.class);
            method.setAccessible(true);
            method.invoke(packet, player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke packet handler method: " + methodName, e);
        }
    }

    private static void invokeNoArgProtected(Object instance, String methodName) {
        Class<?> current = instance.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(instance);
                return;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to invoke protected method: " + methodName, e);
            }
        }

        throw new AssertionError("Failed to find protected method: " + methodName);
    }

    private static int countPickItemThrottleFires(BasicEntityShip ship, int firstTick) {
        ShipPickItemGoal goal = new ShipPickItemGoal(ship, 6.0F);
        Field nextTickField;
        try {
            nextTickField = ShipPickItemGoal.class.getDeclaredField("nextItemScanTick");
            nextTickField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect ShipPickItemGoal throttle state.", e);
        }

        ship.tickCount = firstTick;
        goal.start();
        int fires = 0;

        for (int tick = firstTick; tick < firstTick + 128; tick += 2) {
            ship.tickCount = tick;
            try {
                int before = nextTickField.getInt(goal);
                goal.tick();
                int after = nextTickField.getInt(goal);
                if (after != before) {
                    if (after != tick + 16) {
                        throw new AssertionError("Item scan throttle scheduled from the wrong tick. tick=" + tick
                                + " next=" + after);
                    }
                    fires++;
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError("Failed to read ShipPickItemGoal throttle state.", e);
            }
        }

        return fires;
    }

    private static void assertDeclaredIntFields(Class<?> type, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = type.getDeclaredField(fieldName);
                if (field.getType() != int.class) {
                    throw new AssertionError(type.getSimpleName() + "." + fieldName
                            + " must hold an entity tick as an int.");
                }
            } catch (NoSuchFieldException e) {
                throw new AssertionError("Missing independent throttle field "
                        + type.getSimpleName() + "." + fieldName, e);
            }
        }
    }

    private static void assertStartInitializesThrottleFields(Goal goal, Entity entity, int now,
                                                              String... fieldNames) {
        entity.tickCount = now;
        goal.start();
        for (String fieldName : fieldNames) {
            int actual = readIntField(goal, fieldName);
            if (actual != now) {
                throw new AssertionError(goal.getClass().getSimpleName() + "." + fieldName
                        + " should initialize to the current entity tick. expected=" + now + " actual=" + actual);
            }
        }
    }

    private static void assertGoalThrottleTiming(Goal goal, Entity entity, String fieldName,
                                                  int interval, int firstTick) {
        entity.tickCount = firstTick;
        goal.start();
        if (readIntField(goal, fieldName) != firstTick) {
            throw new AssertionError("Throttle did not initialize for immediate first execution: " + fieldName);
        }

        goal.tick();
        int firstNext = readIntField(goal, fieldName);
        if (firstNext != firstTick + interval) {
            throw new AssertionError("Throttle did not fire immediately. field=" + fieldName
                    + " expected=" + (firstTick + interval) + " actual=" + firstNext);
        }

        int skippedTick = firstTick + interval + 2;
        entity.tickCount = skippedTick;
        goal.tick();
        int skippedNext = readIntField(goal, fieldName);
        if (skippedNext != skippedTick + interval) {
            throw new AssertionError("Throttle should schedule from now after skipped ticks. field=" + fieldName
                    + " expected=" + (skippedTick + interval) + " actual=" + skippedNext);
        }

        int restartTick = skippedTick + 2;
        entity.tickCount = restartTick;
        goal.start();
        goal.tick();
        int restartNext = readIntField(goal, fieldName);
        if (restartNext != restartTick + interval) {
            throw new AssertionError("Throttle should fire immediately after restart. field=" + fieldName
                    + " expected=" + (restartTick + interval) + " actual=" + restartNext);
        }
    }

    private static int readIntField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect throttle field "
                    + instance.getClass().getSimpleName() + "." + fieldName, e);
        }
    }

    private static void assertCanCreate(ServerLevel level, EntityType<?> type, String id) {
        Entity entity = type.create(level);
        if (entity == null) {
            throw new AssertionError("EntityType#create returned null: " + id);
        }
        entity.discard();
    }

    private static void assertEggIconForShipClass(int shipClass, int expectedIcon) {
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        ShipSpawnEgg.setShipClass(egg, shipClass);
        int actualIcon = ShipSpawnEgg.getEggIcon(egg);
        if (actualIcon != expectedIcon) {
            throw new AssertionError("Ship egg icon mismatch for class=" + shipClass + " expected="
                    + expectedIcon + " actual=" + actualIcon);
        }
    }

    private static void assertModelLayer0(String modelFileName, String expectedTexture) {
        Path modelPath = Path.of("src", "main", "resources", "assets", "shincolle", "models", "item", modelFileName);
        try {
            String content = Files.readString(modelPath);
            String expectedLine = "\"layer0\": \"" + expectedTexture + "\"";
            if (!content.contains(expectedLine)) {
                throw new AssertionError("Spawn egg model texture mismatch in " + modelFileName + " expected="
                        + expectedTexture);
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to validate spawn egg model file: " + modelPath, e);
        }
    }

    private static void assertCanSpawnAndTick(ServerLevel level, EntityType<?> type, String id) {
        Entity entity = type.create(level);
        if (entity == null) {
            throw new AssertionError("EntityType#create returned null: " + id);
        }

        entity.moveTo(0.5D, level.getSharedSpawnPos().getY() + 1D, 0.5D, 0F, 0F);
        if (!level.addFreshEntity(entity)) {
            throw new AssertionError("Failed to add entity to level: " + id);
        }

        try {
            entity.tick();
        } catch (Throwable t) {
            throw new AssertionError("Entity tick failed on dedicated server: " + id, t);
        } finally {
            entity.discard();
        }
    }

    private static void assertItemTooltipSafe(ServerLevel level, Item item, String id, TooltipFlag tooltipFlag) {
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            throw new AssertionError("Failed to create ItemStack: " + id);
        }

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, level, tooltip, tooltipFlag);
    }
}
