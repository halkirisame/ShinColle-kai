package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.TeamHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipyardWaypointPermissionGameTests {
    private ShipyardWaypointPermissionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_shipyard_permissions")
    public static void smallShipyardChecksOwnerAllianceOperatorAndLegacyAccess(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        var block = ModBlocks.SMALL_SHIPYARD.get();
        var state = block.defaultBlockState();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            PermissionPlayer owner = entities.add(new PermissionPlayer(level, false));
            PermissionPlayer ally = entities.add(new PermissionPlayer(level, false));
            PermissionPlayer stranger = entities.add(new PermissionPlayer(level, false));
            PermissionPlayer operator = entities.add(new PermissionPlayer(level, true));
            for (PermissionPlayer player : new PermissionPlayer[] {owner, ally, stranger, operator}) {
                player.moveTo(Vec3.atCenterOf(pos));
                ServerDataManager.updatePlayerID(player);
            }
            int ownerUid = TeamHelper.getPlayerUID(owner);
            int allyUid = TeamHelper.getPlayerUID(ally);
            ServerDataManager.setTeamData(new TeamData(ownerUid, "DockOwner", "dock_owner"));
            ServerDataManager.setTeamData(new TeamData(allyUid, "DockAlly", "dock_ally"));
            try {
                ServerDataManager.teamAddAlly(ownerUid, allyUid);
                helper.assertTrue(ownerUid > 0 && allyUid > 0 && ownerUid != allyUid
                                && TeamHelper.checkIsAlly(owner, ally), "The MOD alliance fixture must be active.");
                helper.assertTrue(ServerDataManager.checkOP(operator) && !ServerDataManager.checkOP(stranger),
                        "Operator permission must be independent of creative mode.");
                level.setBlock(pos, state, 3);
                TileEntitySmallShipyard tile = (TileEntitySmallShipyard) level.getBlockEntity(pos);
                ContainerSmallShipyard menu = new ContainerSmallShipyard(1, stranger.getInventory(), tile);
                helper.assertTrue(menu.stillValid(stranger), "Unowned legacy docks remain public.");
                block.setPlacedBy(level, pos, state, owner, new ItemStack(block));
                CompoundTag saved = tile.saveWithFullMetadata();
                helper.assertTrue(saved.hasUUID("OwnerUUID") && saved.getUUID("OwnerUUID").equals(owner.getUUID()),
                        "Placement must persist the placing owner's UUID.");
                tile.load(saved);
                helper.assertTrue(menu.stillValid(owner) && menu.stillValid(ally) && menu.stillValid(operator),
                        "Owner, MOD ally and operator must retain access after load.");
                helper.assertTrue(saved.getInt("OwnerPlayerUID") == ownerUid,
                        "The persisted alliance identity must survive the owner's absence after restart.");
                checkOfflineAlliance(helper, owner, ally, menu);
                helper.assertTrue(!menu.stillValid(stranger), "An unrelated player must lose an already-created menu.");
                stranger.setGameMode(GameType.CREATIVE);
                helper.assertTrue(!menu.stillValid(stranger), "Creative without server permission must not bypass access.");
                var before = stranger.containerMenu;
                block.use(state, level, pos, stranger, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                helper.assertTrue(stranger.containerMenu == before, "Rejected use must not open a menu.");
                block.setPlacedBy(level, pos, state, stranger, new ItemStack(block));
                helper.assertTrue(menu.stillValid(stranger) && !menu.stillValid(owner),
                        "Re-placement must protect the dock for its new placing owner.");
                saved.remove("OwnerUUID");
                tile.load(saved);
                helper.assertTrue(menu.stillValid(owner) && menu.stillValid(stranger), "Loading a legacy dock must clear ownership.");
            } finally {
                ServerDataManager.removeTeamData(ownerUid);
                ServerDataManager.removeTeamData(allyUid);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void waypointRejectsOtherPlayersWithoutRemovingBlockOrDrops(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        var block = ModBlocks.WAYPOINT.get();
        var state = block.defaultBlockState();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            PermissionPlayer owner = entities.add(new PermissionPlayer(level, false));
            PermissionPlayer other = entities.add(new PermissionPlayer(level, false));
            PermissionPlayer operator = entities.add(new PermissionPlayer(level, true));
            for (PermissionPlayer player : new PermissionPlayer[] {owner, other, operator}) {
                level.setBlock(pos, state, 3);
                ((TileEntityWaypoint) level.getBlockEntity(pos)).setOwnerUUID(owner.getUUID());
                boolean expected = player != other;
                boolean removed = state.onDestroyedByPlayer(level, pos, player, true, level.getFluidState(pos));
                helper.assertTrue(removed == expected, "Only the owner or operator may remove an owned waypoint.");
                helper.assertTrue(expected ? level.getBlockState(pos).isAir() : level.getBlockState(pos).is(block),
                        "Rejected destruction must leave the block and its block entity intact.");
            }
            level.setBlock(pos, state, 3);
            other.setGameMode(GameType.CREATIVE);
            ((TileEntityWaypoint) level.getBlockEntity(pos)).setOwnerUUID(owner.getUUID());
            helper.assertTrue(!state.onDestroyedByPlayer(level, pos, other, false, level.getFluidState(pos)),
                    "Creative alone must not remove another player's waypoint.");
            ((TileEntityWaypoint) level.getBlockEntity(pos)).setOwnerUUID(null);
            helper.assertTrue(state.onDestroyedByPlayer(level, pos, other, false, level.getFluidState(pos)),
                    "Unowned waypoints must remain removable by anyone.");
        }
        helper.succeed();
    }

    private static final class PermissionPlayer extends FakePlayer {
        private final boolean operator;

        private PermissionPlayer(ServerLevel level, boolean operator) {
            super(level, new GameProfile(UUID.randomUUID(), "dock_permission"));
            this.operator = operator;
        }

        @Override
        public boolean hasPermissions(int required) {
            return operator;
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkOfflineAlliance(GameTestHelper helper, FakePlayer owner, FakePlayer ally,
                                            ContainerSmallShipyard menu) {
        try {
            var field = ServerDataManager.class.getDeclaredField("mapPlayerUUID");
            field.setAccessible(true);
            var index = (java.util.Map<UUID, Integer>) field.get(null);
            Integer previous = index.remove(owner.getUUID());
            try {
                helper.assertTrue(ServerDataManager.getKnownPlayerUID(owner.getUUID()) == 0 && menu.stillValid(ally),
                        "An allied player must retain access before the offline owner logs in again.");
            } finally {
                if (previous != null) index.put(owner.getUUID(), previous);
            }
        } catch (ReflectiveOperationException error) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Cannot simulate an offline owner: " + error);
        }
    }
}
