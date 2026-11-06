package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.ITileWaypoint;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.TileEntityHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CranePairingGameTests {

    private static final int OWNER_UID = 12549;

    private CranePairingGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void cranePairsThroughServerWrenchHandlerAndClearsMissingChest(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        pairChest(helper, fixture.owner(), fixture);
        helper.assertTrue(fixture.crane().isPaired() && fixture.chest().equals(fixture.crane().getChestPos()),
                "The server wrench handler must pair an owned crane with its chest.");
        helper.getLevel().setBlock(fixture.chest(), Blocks.AIR.defaultBlockState(), 3);
        fixture.crane().setActive(true);
        for (int tick = 0; tick < 80; tick++) {
            TileEntityCrane.serverTick(helper.getLevel(), fixture.crane().getBlockPos(),
                    fixture.crane().getBlockState(), fixture.crane());
        }
        helper.assertTrue(!fixture.crane().isPaired(), "Removing the chest must invalidate the pairing.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void cranePairingRetainsOwnerAndWrenchChecks(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer stranger = player(helper, "crane_stranger");
        stranger.moveTo(fixture.owner().getX(), fixture.owner().getY(), fixture.owner().getZ());
        pairChest(helper, stranger, fixture);
        helper.assertTrue(!fixture.crane().isPaired(), "A forged numeric UID must not bypass crane ownership.");
        fixture.owner().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        pairChest(helper, fixture.owner(), fixture);
        helper.assertTrue(!fixture.crane().isPaired(), "Pairing must still require the target wrench.");
        fixture.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TARGET_WRENCH.get()));
        pairChest(helper, fixture.owner(), fixture);
        helper.assertTrue(fixture.crane().isPaired(), "The same fixture must accept the authenticated owner.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneParticipatesOnBothSidesOfWaypointRoutes(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ITileWaypoint crane = waypoint(helper, fixture.crane());
        BlockPos waypointPos = fixture.crane().getBlockPos().offset(0, 0, 2);
        helper.getLevel().setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        TileEntityWaypoint waypoint = (TileEntityWaypoint) helper.getLevel().getBlockEntity(waypointPos);
        helper.assertTrue(waypoint != null, "The waypoint fixture must exist.");
        waypoint.setOwnerUUID(fixture.owner().getUUID());
        waypoint.setPlayerUID(OWNER_UID);
        TileEntityHelper.pairingWaypoints(fixture.owner(), OWNER_UID, helper.getLevel(),
                fixture.crane().getBlockPos(), waypointPos);
        helper.assertTrue(crane.hasNextWaypoint() && waypointPos.equals(crane.getNextWaypoint())
                        && waypoint.hasLastWaypoint() && fixture.crane().getBlockPos().equals(waypoint.getLastWaypoint()),
                "A crane must work as the source of a waypoint link.");
        TileEntityHelper.pairingWaypoints(fixture.owner(), OWNER_UID, helper.getLevel(),
                waypointPos, fixture.crane().getBlockPos());
        helper.assertTrue(waypoint.hasNextWaypoint() && fixture.crane().getBlockPos().equals(waypoint.getNextWaypoint())
                        && !crane.hasLastWaypoint(),
                "A crane must work as the destination while preserving the two-node cycle rule.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneRoutePresenceAndOriginSurviveNbt(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ITileWaypoint crane = waypoint(helper, fixture.crane());
        helper.assertTrue(!crane.hasNextWaypoint() && !crane.hasLastWaypoint() && !crane.hasPairedChest(),
                "A new crane must start without route or chest links.");
        crane.setNextWaypoint(BlockPos.ZERO);
        crane.setLastWaypoint(new BlockPos(-3, -20, 7));
        crane.setPairedChest(BlockPos.ZERO);
        CompoundTag saved = fixture.crane().saveWithFullMetadata();
        TileEntityCrane restored = new TileEntityCrane(fixture.crane().getBlockPos(), fixture.crane().getBlockState());
        restored.load(saved);
        ITileWaypoint route = waypoint(helper, restored);
        helper.assertTrue(route.hasNextWaypoint() && BlockPos.ZERO.equals(route.getNextWaypoint())
                        && route.hasLastWaypoint() && crane.getLastWaypoint().equals(route.getLastWaypoint())
                        && route.hasPairedChest() && BlockPos.ZERO.equals(route.getPairedChest()),
                "Explicit origin links and negative-height links must survive saving.");
        helper.assertTrue(fixture.owner().getUUID().equals(restored.getOwnerUUID()) && restored.getPlayerUID() == OWNER_UID,
                "Route persistence must preserve the crane owner.");
        helper.succeed();
    }

    private static ITileWaypoint waypoint(GameTestHelper helper, Object tile) {
        helper.assertTrue(tile instanceof ITileWaypoint, "Cranes must participate in the waypoint interface.");
        return (ITileWaypoint) tile;
    }

    private static void pairChest(GameTestHelper helper, ServerPlayer player, Fixture fixture) {
        TileEntityHelper.pairingWaypointAndChest(player, OWNER_UID, helper.getLevel(),
                fixture.crane().getBlockPos(), fixture.chest());
    }

    private static Fixture fixture(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos chest = pos.offset(2, 0, 0);
        helper.getLevel().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        TileEntityCrane crane = (TileEntityCrane) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(crane != null, "The crane fixture must exist.");
        ServerPlayer owner = player(helper, "crane_owner");
        owner.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> capa.setPlayerUID(OWNER_UID));
        helper.assertTrue(crane.claimOrVerifyOwner(owner) && crane.getPlayerUID() == OWNER_UID
                        && owner.getUUID().equals(crane.getOwnerUUID()),
                "The crane fixture must have the authenticated owner and numeric UID.");
        owner.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return new Fixture(crane, owner, chest);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TARGET_WRENCH.get()));
        return player;
    }

    private record Fixture(TileEntityCrane crane, ServerPlayer owner, BlockPos chest) {
    }
}
