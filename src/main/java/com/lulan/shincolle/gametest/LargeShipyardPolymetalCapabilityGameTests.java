package com.lulan.shincolle.gametest;

import com.lulan.shincolle.block.BasicBlockMulti;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.BasicTileMulti;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.lulan.shincolle.tileentity.TileMultiPolymetal;
import com.lulan.shincolle.utility.MulitBlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LargeShipyardPolymetalCapabilityGameTests {

    private static final BlockPos CORE = new BlockPos(4, 4, 4);

    private LargeShipyardPolymetalCapabilityGameTests() {
    }

    @GameTest(template = "arena")
    public static void hopperInsertsFuelThroughPolymetalBase(GameTestHelper helper) {
        withShipyard(helper, () -> {
            ServerLevel level = helper.getLevel();
            TileMultiGrudgeHeavy core = core(helper);
            BlockPos hopperPos = base(helper).getBlockPos().west();
            BlockState state = Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, Direction.EAST);
            level.setBlock(hopperPos, state, 3);
            HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(hopperPos);
            hopper.setItem(0, new ItemStack(Items.COAL));
            helper.assertTrue(hopper.getItem(0).is(Items.COAL) && core.getPowerRemained() == 0,
                    "Hopper fuel fixture must start with coal and an empty core");

            HopperBlockEntity.pushItemsTick(level, hopperPos, state, hopper);
            helper.assertTrue(hopper.isEmpty(), "Hopper must deliver fuel into the polymetal base");
            helper.assertTrue(core.getInventory().getStackInSlot(TileMultiGrudgeHeavy.SLOT_INPUT_START)
                    .is(Items.COAL), "Hopper fuel must reach the core input inventory");
            TileMultiGrudgeHeavy.serverTick(level, core.getBlockPos(), core.getBlockState(), core);
            helper.assertTrue(core.getPowerRemained() > 0, "Delivered coal must become core fuel power");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void polymetalFluidCapabilityFillsCoreTankOnEverySide(GameTestHelper helper) {
        withShipyard(helper, () -> {
            TileMultiGrudgeHeavy core = core(helper);
            TileMultiPolymetal base = base(helper);
            for (Direction side : sides()) {
                var fluid = base.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
                var coreFluid = core.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
                var item = base.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                var coreItem = core.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                helper.assertTrue(fluid.isPresent(), "Formed base must expose fluid capability on " + side);
                helper.assertTrue(item.isPresent(), "Formed base must expose item capability on " + side);
                helper.assertTrue(fluid != coreFluid && item != coreItem,
                        "The base must own its capability optionals");
                helper.assertTrue(fluid.orElse(null) == coreFluid.orElse(null)
                        && item.orElse(null) == coreItem.orElse(null),
                        "The base must delegate the same sided handlers without changing their rules");
            }
            IFluidHandler fluid = base.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST)
                    .orElseThrow(IllegalStateException::new);
            IFluidHandler coreFluid = core.getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .orElseThrow(IllegalStateException::new);
            helper.assertTrue(coreFluid.getFluidInTank(0).isEmpty(), "Core tank fixture must be empty");
            helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 750), IFluidHandler.FluidAction.EXECUTE) == 0,
                    "The base must preserve the core's non-fuel rejection");
            helper.assertTrue(fluid.fill(new FluidStack(Fluids.LAVA, 750), IFluidHandler.FluidAction.SIMULATE) == 750
                    && coreFluid.getFluidInTank(0).isEmpty(), "Simulated fill must not change the core tank");
            helper.assertTrue(fluid.fill(new FluidStack(Fluids.LAVA, 750), IFluidHandler.FluidAction.EXECUTE) == 750
                    && coreFluid.getFluidInTank(0).getAmount() == 750,
                    "Lava supplied through the base must enter the core tank");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void unformedPolymetalDoesNotExposeAutomation(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(CORE.below(2).west());
            BlockPos corePos = helper.absolutePos(CORE);
            level.setBlock(pos, ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
            TileMultiPolymetal base = (TileMultiPolymetal) level.getBlockEntity(pos);
            assertNoAutomation(helper, base);
            base.setCorePos(corePos);
            assertNoAutomation(helper, base);
            level.setBlock(corePos, Blocks.CHEST.defaultBlockState(), 3);
            assertNoAutomation(helper, base);
            level.setBlock(corePos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
            assertNoAutomation(helper, base);
            helper.succeed();
        }, Vec3.atCenterOf(CORE), Vec3.atCenterOf(CORE.below(2).west()));
    }

    @GameTest(template = "arena")
    public static void breakingStructureInvalidatesRetainedBaseCapabilities(GameTestHelper helper) {
        withShipyard(helper, () -> {
            TileMultiPolymetal base = base(helper);
            List<LazyOptional<?>> retained = new ArrayList<>();
            for (Direction side : sides()) {
                retained.add(base.getCapability(ForgeCapabilities.ITEM_HANDLER, side));
                retained.add(base.getCapability(ForgeCapabilities.FLUID_HANDLER, side));
            }
            helper.assertTrue(retained.stream().allMatch(LazyOptional::isPresent),
                    "Completed structure must expose all capabilities before destruction");
            boolean[] invalidated = {false};
            retained.get(0).addListener(ignored -> {
                invalidated[0] = true;
                assertNoAutomation(helper, base);
            });
            helper.getLevel().destroyBlock(helper.absolutePos(CORE.below().offset(1, 0, 1)), false);
            helper.assertTrue(!base.hasCorePos() && !core(helper).hasCorePos(),
                    "Breaking one component must reset the surviving base and core");
            helper.assertTrue(invalidated[0] && retained.stream().noneMatch(LazyOptional::isPresent),
                    "Structure destruction must invalidate retained optionals and notify listeners");
            assertNoAutomation(helper, base);
            placeShipyard(helper);
            helper.assertTrue(base(helper).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Reforming the structure must expose a fresh capability");
            helper.assertTrue(retained.stream().noneMatch(LazyOptional::isPresent),
                    "Reformation must not revive old optionals");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void changingCorePositionInvalidatesBaseCapabilities(GameTestHelper helper) {
        withShipyard(helper, () -> {
            TileMultiPolymetal base = base(helper);
            var oldItem = base.getCapability(ForgeCapabilities.ITEM_HANDLER);
            var oldFluid = base.getCapability(ForgeCapabilities.FLUID_HANDLER);
            helper.assertTrue(oldItem.isPresent() && oldFluid.isPresent(), "Old core must be accessible");
            BlockPos lamp = installNeighborLamp(helper);
            NeighborProbe probe = observeBaseNeighbors(helper, true);
            BlockPos nextPos = helper.absolutePos(CORE.east(3));
            helper.getLevel().setBlock(nextPos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
            TileMultiGrudgeHeavy next = (TileMultiGrudgeHeavy) helper.getLevel().getBlockEntity(nextPos);
            next.setCorePos(nextPos);
            base.setCorePos(nextPos);
            BasicBlockMulti.updateBlockState(1, helper.getLevel(), base.getBlockPos());
            helper.assertTrue(!oldItem.isPresent() && !oldFluid.isPresent(),
                    "Changing core position must invalidate both old capabilities");
            helper.assertTrue(base.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null)
                    == next.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null),
                    "Fresh lookup must use the new core");
            helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT)
                    && probe.updates == 1 && probe.consistent,
                    "Changing the core must notify the neighbor after the block state is ready");
            base.setCorePos(nextPos);
            BasicBlockMulti.updateBlockState(1, helper.getLevel(), base.getBlockPos());
            helper.assertTrue(probe.updates == 1, "Reapplying the same core must not notify twice");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void loadedPolymetalResolvesSavedCorePosition(GameTestHelper helper) {
        withShipyard(helper, () -> {
            TileMultiPolymetal base = base(helper);
            TileMultiPolymetal loaded = new TileMultiPolymetal(base.getBlockPos(), base.getBlockState());
            loaded.load(base.saveWithoutMetadata());
            helper.assertTrue(loaded.getLevel() == null
                    && !loaded.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    && !loaded.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(),
                    "A newly loaded base must remain safe before its level is assigned");
            loaded.setLevel(helper.getLevel());
            helper.assertTrue(loaded.hasCorePos() && loaded.getCorePos().equals(core(helper).getBlockPos()),
                    "NBT fixture must restore the saved core position");
            helper.assertTrue(loaded.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    && loaded.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(),
                    "A loaded base must resolve both capabilities without setCorePos");
            loaded.invalidateCaps();
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void existingBaseReloadRoutesAutomationToNewCoreAndNotifiesNeighbors(GameTestHelper helper) {
        withShipyard(helper, () -> {
            ServerLevel level = helper.getLevel();
            TileMultiPolymetal base = base(helper);
            TileMultiGrudgeHeavy original = core(helper);
            CompoundTag originalTag = base.saveWithoutMetadata();
            BlockPos nextPos = helper.absolutePos(CORE.east(3));
            level.setBlock(nextPos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
            TileMultiGrudgeHeavy next = (TileMultiGrudgeHeavy) level.getBlockEntity(nextPos);
            next.setCorePos(nextPos);
            var originalFluid = original.getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .orElseThrow(IllegalStateException::new);
            var nextFluid = next.getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .orElseThrow(IllegalStateException::new);
            helper.assertTrue(original.hasCorePos() && next.hasCorePos()
                    && original.getInventory().getStackInSlot(TileMultiGrudgeHeavy.SLOT_INPUT_START).isEmpty()
                    && next.getInventory().getStackInSlot(TileMultiGrudgeHeavy.SLOT_INPUT_START).isEmpty()
                    && originalFluid.getFluidInTank(0).isEmpty() && nextFluid.getFluidInTank(0).isEmpty(),
                    "Reload fixture must contain two valid cores with empty inputs and tanks");
            List<LazyOptional<?>> retained = new ArrayList<>();
            for (Direction side : sides()) {
                retained.add(base.getCapability(ForgeCapabilities.ITEM_HANDLER, side));
                retained.add(base.getCapability(ForgeCapabilities.FLUID_HANDLER, side));
            }
            helper.assertTrue(retained.stream().allMatch(LazyOptional::isPresent),
                    "The existing base must cache the original core's handlers before reload");
            BlockPos lamp = installNeighborLamp(helper);
            NeighborProbe probe = observeBaseNeighbors(helper, true);
            boolean[] notifiedAfterReload = {false};
            Consumer<BlockEvent.NeighborNotifyEvent> listener = event -> {
                if (event.getLevel() == level && event.getPos().equals(base.getBlockPos())) {
                    notifiedAfterReload[0] |= retained.stream().noneMatch(LazyOptional::isPresent)
                            && base.getCorePos().equals(nextPos)
                            && base.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null)
                                    == next.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null)
                            && base.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null) == nextFluid;
                }
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            GameTestEntities.onCompletion(helper, () -> MinecraftForge.EVENT_BUS.unregister(listener),
                    "base reload neighbor probe cleanup");
            CompoundTag reloaded = originalTag.copy();
            reloaded.putInt("CoreX", nextPos.getX());
            reloaded.putInt("CoreY", nextPos.getY());
            reloaded.putInt("CoreZ", nextPos.getZ());
            base.load(reloaded);
            helper.assertTrue(level.getBlockEntity(base.getBlockPos()) == base && base.getCorePos().equals(nextPos),
                    "Reload must update the same existing block entity, not create a replacement");
            var items = base.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            var fluids = base.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
            boolean inserted = items.insertItem(TileMultiGrudgeHeavy.SLOT_INPUT_START,
                    new ItemStack(Items.COAL), false).isEmpty();
            int filled = fluids.fill(new FluidStack(Fluids.LAVA, 750), IFluidHandler.FluidAction.EXECUTE);
            int originalItems = original.getInventory().getStackInSlot(TileMultiGrudgeHeavy.SLOT_INPUT_START).getCount();
            int nextItems = next.getInventory().getStackInSlot(TileMultiGrudgeHeavy.SLOT_INPUT_START).getCount();
            helper.assertTrue(inserted && filled == 750 && nextItems == 1 && originalItems == 0
                    && nextFluid.getFluidInTank(0).getAmount() == 750 && originalFluid.getFluidInTank(0).isEmpty(),
                    "NBT reload must route both handlers to B, not A: A items=" + originalItems
                            + ", lava=" + originalFluid.getFluidInTank(0).getAmount()
                            + "; B items=" + nextItems + ", lava=" + nextFluid.getFluidInTank(0).getAmount());
            helper.assertTrue(retained.stream().noneMatch(LazyOptional::isPresent),
                    "Reload must invalidate all retained item and fluid optionals");
            helper.assertTrue(probe.updates == 1 && probe.consistent && notifiedAfterReload[0]
                    && level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                    "Reload must notify once, after the new core and capability invalidation are observable");
            var retainedNext = base.getCapability(ForgeCapabilities.ITEM_HANDLER);
            var retainedNextFluid = base.getCapability(ForgeCapabilities.FLUID_HANDLER);
            base.load(reloaded);
            helper.assertTrue(retainedNext.isPresent() && retainedNextFluid.isPresent() && probe.updates == 1,
                    "Reloading the same core must preserve capabilities without duplicate notification");
            installNeighborLamp(helper);
            base.beginStructureUpdate();
            base.load(originalTag);
            helper.assertTrue(!retainedNext.isPresent() && !retainedNextFluid.isPresent() && probe.updates == 1,
                    "A batched reload must invalidate handlers immediately but defer neighbor notification");
            base.endStructureUpdate();
            helper.assertTrue(probe.updates == 2 && probe.consistent
                    && level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)
                    && base.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null)
                            == original.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null),
                    "Completing the batch must notify once with the restored core ready");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void baseCapabilityLifecycleInvalidatesAndRevivesFreshOptionals(GameTestHelper helper) {
        withShipyard(helper, () -> {
            TileMultiPolymetal base = base(helper);
            var item = base.getCapability(ForgeCapabilities.ITEM_HANDLER);
            var fluid = base.getCapability(ForgeCapabilities.FLUID_HANDLER);
            helper.assertTrue(item.isPresent() && fluid.isPresent(), "Live base must expose capabilities");
            base.invalidateCaps();
            helper.assertTrue(!item.isPresent() && !fluid.isPresent(),
                    "Block entity invalidation must invalidate its delegated optionals");
            assertNoAutomation(helper, base);
            base.reviveCaps();
            helper.assertTrue(base.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    && base.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(),
                    "Reviving a formed base must provide fresh optionals");
            helper.assertTrue(!item.isPresent() && !fluid.isPresent(), "Revival must not reuse invalidated optionals");
            var revivedItem = base.getCapability(ForgeCapabilities.ITEM_HANDLER);
            var revivedFluid = base.getCapability(ForgeCapabilities.FLUID_HANDLER);
            core(helper).invalidateCaps();
            helper.assertTrue(!revivedItem.isPresent() && !revivedFluid.isPresent(),
                    "Invalidating the source core must also invalidate the base optionals");
            assertNoAutomation(helper, base);
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void formationNotifiesNeighborAfterEntireStructureIsReady(GameTestHelper helper) {
        withShipyard(helper, false, () -> {
            BlockPos lamp = installNeighborLamp(helper);
            NeighborProbe probe = observeBaseNeighbors(helper, true);
            formShipyard(helper);
            helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                    "Formation must deliver neighborChanged to the pre-existing lamp");
            helper.assertTrue(probe.updates == 1 && probe.consistent,
                    "Formation must notify once, after every member and the core are ready");
            BlockPos core = helper.absolutePos(CORE);
            MulitBlockHelper.setupStructure(helper.getLevel(), core.getX(), core.getY(), core.getZ(), 1);
            helper.assertTrue(probe.updates == 1, "Reapplying unchanged membership must not notify again");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void componentRemovalAndReformationNotifyExistingNeighbor(GameTestHelper helper) {
        withShipyard(helper, () -> {
            BlockPos lamp = installNeighborLamp(helper);
            NeighborProbe probe = observeBaseNeighbors(helper, false);
            helper.getLevel().destroyBlock(helper.absolutePos(CORE.offset(1, -1, 1)), false);
            helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                    "Component removal must deliver neighborChanged beside the surviving base");
            helper.assertTrue(probe.updates == 1, "Removal must notify once, actual updates=" + probe.updates);
            helper.assertTrue(probe.consistent, "Removal must reset all members before notifying");
            BlockPos core = helper.absolutePos(CORE);
            MulitBlockHelper.resetStructure(helper.getLevel(), core.getX(), core.getY(), core.getZ());
            helper.assertTrue(probe.updates == 1, "Resetting already detached members must not notify again");
            installNeighborLamp(helper);
            probe.expectedFormed = true;
            placeShipyard(helper);
            helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                    "Reformation must notify the same neighbor without replacing it");
            helper.assertTrue(probe.updates == 2 && probe.consistent,
                    "Reformation must deliver one new notification after the entire structure is ready");
            helper.succeed();
        });
    }

    private static BlockPos installNeighborLamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lamp = helper.absolutePos(CORE.offset(-2, -2, 0));
        level.setBlock(lamp.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
        helper.assertTrue(level.hasNeighborSignal(lamp)
                && !level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                "Neighbor probe must start powered but unlit, awaiting neighborChanged");
        return lamp;
    }

    private static NeighborProbe observeBaseNeighbors(GameTestHelper helper, boolean expectedFormed) {
        NeighborProbe probe = new NeighborProbe();
        probe.expectedFormed = expectedFormed;
        BlockPos base = base(helper).getBlockPos();
        Consumer<BlockEvent.NeighborNotifyEvent> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getPos().equals(base)) {
                probe.updates++;
                probe.consistent &= structureMatches(helper, probe.expectedFormed)
                        && base(helper).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent() == probe.expectedFormed
                        && base(helper).getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent() == probe.expectedFormed;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        GameTestEntities.onCompletion(helper, () -> MinecraftForge.EVENT_BUS.unregister(listener),
                "shipyard neighbor probe cleanup");
        return probe;
    }

    private static boolean structureMatches(GameTestHelper helper, boolean formed) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(CORE);
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, -2, -1), core.offset(1, 0, 1))) {
            if (level.getBlockEntity(pos) instanceof BasicTileMulti tile) {
                if (tile.hasCorePos() != formed) {
                    return false;
                }
            } else if (level.getBlockEntity(pos) instanceof TileMultiGrudgeHeavy tile) {
                if (tile.hasCorePos() != formed) {
                    return false;
                }
            } else {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BasicBlockMulti.MBS)) {
                if (state.getValue(BasicBlockMulti.MBS) != (formed ? 1 : 0)) {
                    return false;
                }
            } else if (formed) {
                return false;
            }
        }
        return true;
    }

    private static final class NeighborProbe {
        private int updates;
        private boolean expectedFormed;
        private boolean consistent = true;
    }

    private static void withShipyard(GameTestHelper helper, Runnable verification) {
        withShipyard(helper, true, verification);
    }

    private static void withShipyard(GameTestHelper helper, boolean formed, Runnable verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            placeShipyardBlocks(helper);
            if (formed) {
                formShipyard(helper);
            }
            verification.run();
        }, Vec3.atCenterOf(CORE), Vec3.atCenterOf(CORE.offset(-3, -2, -1)),
                Vec3.atCenterOf(CORE.offset(3, 0, 1)));
    }

    private static void placeShipyard(GameTestHelper helper) {
        placeShipyardBlocks(helper);
        formShipyard(helper);
    }

    private static void placeShipyardBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(CORE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(core.offset(dx, -2, dz), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
                if (dx != 0 && dz != 0) {
                    level.setBlock(core.offset(dx, -1, dz), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(core, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
    }

    private static void formShipyard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(CORE);
        helper.assertTrue(MulitBlockHelper.checkMultiBlockForm(level, core.getX(), core.getY(), core.getZ()) == 1,
                "Fixture must match an unformed large shipyard");
        MulitBlockHelper.setupStructure(level, core.getX(), core.getY(), core.getZ(), 1);
        helper.assertTrue(core(helper).hasCorePos() && base(helper).hasCorePos(),
                "Fixture formation must assign core positions");
    }

    private static TileMultiGrudgeHeavy core(GameTestHelper helper) {
        return (TileMultiGrudgeHeavy) helper.getLevel().getBlockEntity(helper.absolutePos(CORE));
    }

    private static TileMultiPolymetal base(GameTestHelper helper) {
        return (TileMultiPolymetal) helper.getLevel().getBlockEntity(helper.absolutePos(CORE.below(2).west()));
    }

    private static Direction[] sides() {
        return new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST};
    }

    private static void assertNoAutomation(GameTestHelper helper, TileMultiPolymetal base) {
        for (Direction side : sides()) {
            helper.assertTrue(!base.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent()
                    && !base.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent(),
                    "Base outside a live structure must not expose automation on " + side);
        }
    }
}
