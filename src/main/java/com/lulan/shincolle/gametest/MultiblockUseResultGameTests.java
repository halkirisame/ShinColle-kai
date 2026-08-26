package com.lulan.shincolle.gametest;

import com.lulan.shincolle.block.BasicBlockMulti;
import com.lulan.shincolle.client.gui.inventory.ContainerLargeShipyard;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.BasicTileMulti;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.lulan.shincolle.utility.MulitBlockHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MultiblockUseResultGameTests {

    private MultiblockUseResultGameTests() {
    }

    @GameTest(template = "arena")
    public static void unformedMultiblockUsePassesToHeldItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, ModBlocks.POLYMETAL.get().defaultBlockState(), 3);

        InteractionResult result = useBlock(level, pos, createPlayer(level, 1));
        if (result != InteractionResult.PASS) {
            throw new AssertionError("Unformed multiblock use should pass to held item, got: " + result);
        }

        level.setBlock(pos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        result = useBlock(level, pos, createPlayer(level, 2));
        if (result != InteractionResult.PASS) {
            throw new AssertionError("Invalid shipyard core use should pass to held item, got: " + result);
        }

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void sneakingUseDoesNotFormStructure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(4, 4, 2));
        List<BlockPos> structure = placeLargeShipyard(level, core);
        FakePlayer player = createPlayer(level, 3);
        player.setShiftKeyDown(true);

        InteractionResult result = useBlock(level, core, player);
        if (result != InteractionResult.PASS) {
            throw new AssertionError("Sneaking multiblock use should pass to held item, got: " + result);
        }
        assertStructureUnformed(level, structure);

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void formedMultiblockUseConsumesAction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(4, 4, 2));
        placeLargeShipyard(level, core);
        MulitBlockHelper.setupStructure(level, core.getX(), core.getY(), core.getZ(), 1);

        FakePlayer player = createPlayer(level, 4);
        InteractionResult result = useBlock(level, core, player);
        if (!result.consumesAction()) {
            throw new AssertionError("Formed multiblock use should consume the held-item action, got: " + result);
        }
        assertLargeShipyardMenuOpened(player);

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void formedMultiblockComponentUseConsumesAction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(4, 4, 2));
        List<BlockPos> structure = placeLargeShipyard(level, core);
        MulitBlockHelper.setupStructure(level, core.getX(), core.getY(), core.getZ(), 1);
        FakePlayer player = createPlayer(level, 5);

        InteractionResult result = useBlock(level, structure.get(0), player);
        if (!result.consumesAction()) {
            throw new AssertionError("Formed component use should consume the held-item action, got: " + result);
        }
        assertLargeShipyardMenuOpened(player);

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void multiblockUseFormsStructureAndMaintainsStateInvariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(4, 4, 2));
        List<BlockPos> structure = placeLargeShipyard(level, core);

        InteractionResult result = useBlock(level, core, createPlayer(level, 6));
        if (!result.consumesAction()) {
            throw new AssertionError("Successful multiblock formation should consume the action, got: " + result);
        }

        for (BlockPos pos : structure) {
            BlockState state = level.getBlockState(pos);
            if (state.getValue(BasicBlockMulti.MBS) != 1) {
                throw new AssertionError("Formed block did not receive MBS=1 at " + pos);
            }
            BlockPos actualCore;
            if (level.getBlockEntity(pos) instanceof BasicTileMulti tile && tile.hasCorePos()) {
                actualCore = tile.getCorePos();
            } else if (level.getBlockEntity(pos) instanceof TileMultiGrudgeHeavy tile && tile.hasCorePos()) {
                actualCore = tile.getCorePos();
            } else {
                throw new AssertionError("Formed block did not receive a core position at " + pos);
            }
            if (!actualCore.equals(core)) {
                throw new AssertionError("Formed block received the wrong core position at " + pos
                        + ": " + actualCore);
            }
        }

        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void staleStructureCleanupDefersReformation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(4, 4, 2));
        List<BlockPos> structure = placeLargeShipyard(level, core);
        MulitBlockHelper.setupStructure(level, core.getX(), core.getY(), core.getZ(), 1);
        BlockPos missingCore = core.offset(20, 0, 0);

        for (BlockPos pos : structure) {
            setCorePos(level, pos, missingCore);
        }

        FakePlayer player = createPlayer(level, 7);
        InteractionResult coreCleanup = useBlock(level, core, player);
        if (coreCleanup != InteractionResult.PASS) {
            throw new AssertionError("Stale core cleanup should pass without forming, got: " + coreCleanup);
        }

        InteractionResult componentCleanup = useBlock(level, core, player);
        if (componentCleanup != InteractionResult.PASS) {
            throw new AssertionError("Stale component cleanup should pass without forming, got: "
                    + componentCleanup);
        }
        assertStructureUnformed(level, structure);

        InteractionResult reformation = useBlock(level, core, player);
        if (!reformation.consumesAction()) {
            throw new AssertionError("Clean structure should form on the following click, got: " + reformation);
        }

        helper.succeed();
    }

    private static InteractionResult useBlock(ServerLevel level, BlockPos pos, FakePlayer player) {
        BlockState state = level.getBlockState(pos);
        BasicBlockMulti block = (BasicBlockMulti) state.getBlock();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    private static FakePlayer createPlayer(ServerLevel level, int id) {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-00000000010" + id);
        return FakePlayerFactory.get(level, new GameProfile(uuid, "shincolle_multiblock_use_" + id));
    }

    private static void assertLargeShipyardMenuOpened(FakePlayer player) {
        if (!(player.containerMenu instanceof ContainerLargeShipyard)) {
            throw new AssertionError("Multiblock use consumed the action without opening the shipyard menu");
        }
    }

    private static void assertStructureUnformed(ServerLevel level, List<BlockPos> structure) {
        for (BlockPos pos : structure) {
            if (level.getBlockState(pos).getValue(BasicBlockMulti.MBS) != 0) {
                throw new AssertionError("Unformed block retained MBS state at " + pos);
            }
            if (level.getBlockEntity(pos) instanceof BasicTileMulti tile && tile.hasCorePos()) {
                throw new AssertionError("Unformed component retained core position at " + pos);
            }
            if (level.getBlockEntity(pos) instanceof TileMultiGrudgeHeavy tile && tile.hasCorePos()) {
                throw new AssertionError("Unformed core retained core position at " + pos);
            }
        }
    }

    private static void setCorePos(ServerLevel level, BlockPos pos, BlockPos core) {
        if (level.getBlockEntity(pos) instanceof BasicTileMulti tile) {
            tile.setCorePos(core);
        } else if (level.getBlockEntity(pos) instanceof TileMultiGrudgeHeavy tile) {
            tile.setCorePos(core);
        } else {
            throw new AssertionError("Multiblock tile was not created at " + pos);
        }
    }

    private static List<BlockPos> placeLargeShipyard(ServerLevel level, BlockPos core) {
        List<BlockPos> structure = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = core.offset(dx, -2, dz);
                level.setBlock(pos, ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
                structure.add(pos);
            }
        }

        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                BlockPos pos = core.offset(dx, -1, dz);
                level.setBlock(pos, ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
                structure.add(pos);
            }
        }

        level.setBlock(core, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
        structure.add(core);
        return structure;
    }
}
