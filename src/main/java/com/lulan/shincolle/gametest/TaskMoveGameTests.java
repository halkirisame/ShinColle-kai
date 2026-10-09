package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementHost;
import java.util.UUID;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityType;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.TaskHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A working ship's walks (cooking and crafting back to the chest, fishing to the spot, mining's shuffle). */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaskMoveGameTests {

    private static final Pattern CALL = Pattern.compile("^moveTo\\((-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\)");

    private enum Work { COOKING, CRAFTING, FISHING, MINING }

    private record Run(List<String> calls, MovementReason reason, double x, double y, double z) { }

    private TaskMoveGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void cookingReturnsToTheWaypointTheSameUnderEitherAuthority(GameTestHelper helper) {
        sameCalls(helper, Work.COOKING, MovementReason.TASK_RETURN);
    }

    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void craftingReturnsToTheWaypointTheSameUnderEitherAuthority(GameTestHelper helper) {
        sameCalls(helper, Work.CRAFTING, MovementReason.TASK_RETURN);
    }

    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void fishingWalksToItsSpotTheSameUnderEitherAuthority(GameTestHelper helper) {
        sameCalls(helper, Work.FISHING, MovementReason.TASK_FISHING_SPOT);
    }

    /** Mining draws its point from the ship's own random numbers, so the two are compared by their range. */
    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void miningShufflesOnceWithinItsRangeUnderEitherAuthority(GameTestHelper helper) {
        for (ConfigHandler.ShipAiTargetAuthority mode : ConfigHandler.ShipAiTargetAuthority.values()) {
            Run run = run(helper, mode, Work.MINING, 64);
            helper.assertTrue(run.calls().size() == 1, mode + ": one path request at the 64-tick mark: " + run.calls());
            double[] at = point(run.calls().get(0));
            helper.assertTrue(at[0] >= run.x() - 4D && at[0] <= run.x() + 4D && at[1] >= run.y() - 2D
                            && at[1] <= run.y() + 2D && at[2] >= run.z() - 4D && at[2] <= run.z() + 4D,
                    mode + ": the point must lie within 4 / 2 / 4 of the ship: " + run.calls() + " from " + run.x()
                            + "," + run.y() + "," + run.z());
            if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                helper.assertTrue(run.reason() == MovementReason.TASK_MINING_SHUFFLE,
                        "NEW must leave TASK_MINING_SHUFFLE in the executor: " + run.reason());
            }
        }
        helper.succeed();
    }

    /** Off the 64-tick mark a mining ship does not walk. */
    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void miningDoesNotWalkOffTheMark(GameTestHelper helper) {
        for (ConfigHandler.ShipAiTargetAuthority mode : ConfigHandler.ShipAiTargetAuthority.values()) {
            helper.assertTrue(run(helper, mode, Work.MINING, 65).calls().isEmpty(), mode + ": no walk off the mark");
        }
        helper.succeed();
    }

    // ---------- when a working ship may walk (P7) ----------

    /** A ship that sits, is leashed, works the crane or has no grudge asks for no path; LEGACY still does. */
    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void aWorkingShipThatCannotWalkAsksForNoPath(GameTestHelper helper) {
        ArmorStand holder = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(7, 1, 8));
        List<Consumer<BasicEntityShip>> holds = List.of(
                ship -> ship.setEntitySit(true),
                ship -> ship.setLeashedTo(holder, true),
                ship -> {
                    ship.setStateTimer(ID.T.CrandDelay, 0);
                    ship.setStateMinor(ID.M.CraneState, 1);
                },
                ship -> ship.setStateMinor(ID.M.NumGrudge, 0));
        String[] names = {"sitting", "leashed", "crane", "no grudge"};
        for (int i = 0; i < holds.size(); i++) {
            Consumer<BasicEntityShip> hold = holds.get(i);
            for (Work work : Work.values()) {
                Run now = run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, work, 64, hold);
                helper.assertTrue(now.calls().isEmpty(), "NEW, " + names[i] + ", " + work + ": no path: " + now.calls());
                Run legacy = run(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, work, 64, hold);
                helper.assertTrue(legacy.calls().size() == 1, "LEGACY, " + names[i] + ", " + work
                        + " is unchanged and asks once: " + legacy.calls());
            }
        }
        helper.succeed();
    }

    /** A mining shuffle stays within the region the ship's intent allows; the free ship is the control. */
    @GameTest(template = "arena", batch = "isolated_task_move")
    public static void aMiningShuffleStaysInsideTheRegionOfTheIntent(GameTestHelper helper) {
        BlockPos far = helper.absolutePos(new BlockPos(14, 2, 14));
        Consumer<BasicEntityShip> guardFar = ship -> {
            var id = helper.getLevel().dimension().location();
            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(
                    new ShipCommand.GuardPosition(new DimensionKey(id.getNamespace(), id.getPath()),
                            new CommandPos(far.getX(), far.getY(), far.getZ()), false)));
        };
        Run tight = run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, Work.MINING, 64, ship -> {
            ship.setStateMinor(ID.M.FollowMax, 1);
            guardFar.accept(ship);
        });
        helper.assertTrue(tight.calls().isEmpty(), "A guard far away leaves no point to shuffle to: " + tight.calls());
        Run wide = run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, Work.MINING, 64, ship -> {
            ship.setStateMinor(ID.M.FollowMax, 60);
            guardFar.accept(ship);
        });
        helper.assertTrue(wide.calls().size() == 1, "A wide enough region lets it shuffle: " + wide.calls());
        helper.succeed();
    }

    private static void sameCalls(GameTestHelper helper, Work work, MovementReason expected) {
        Run legacy = run(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, work, 0);
        Run now = run(helper, ConfigHandler.ShipAiTargetAuthority.NEW, work, 0);
        helper.assertTrue(legacy.calls().size() == 1, "Fixture: LEGACY must request one path: " + legacy.calls());
        helper.assertTrue(now.calls().equals(legacy.calls()),
                "The path requests differ:\n LEGACY " + legacy.calls() + "\n NEW    " + now.calls());
        helper.assertTrue(now.reason() == expected, "NEW must leave " + expected + " in the executor: " + now.reason());
        helper.succeed();
    }

    private static Run run(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, Work work, int tick) {
        return run(helper, mode, work, tick, ship -> { });
    }

    private static Run run(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, Work work, int tick,
                           Consumer<BasicEntityShip> setup) {
        ServerLevel level = helper.getLevel();
        BlockPos waypointPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos chestPos = helper.absolutePos(new BlockPos(3, 2, 2));
        try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(mode);
             GameTestEntities entities = GameTestEntities.open(helper)) {
            try {
                if (work != Work.MINING) {
                    level.setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
                    level.setBlock(chestPos, work == Work.COOKING ? Blocks.FURNACE.defaultBlockState()
                            : Blocks.CHEST.defaultBlockState(), 3);
                    if (!(level.getBlockEntity(waypointPos) instanceof TileEntityWaypoint waypoint)) {
                        throw new AssertionError("Fixture: no waypoint");
                    }
                    waypoint.setPairedChest(chestPos);
                }
                BlockPos shipAt = helper.absolutePos(work == Work.MINING ? new BlockPos(5, 2, 5)
                        : new BlockPos(9, 2, 2));
                BasicEntityShip ship = ship(helper, entities, shipAt);
                ship.setGuardedPos(waypointPos.getX(), waypointPos.getY(), waypointPos.getZ(), level.dimension(), 1);
                ship.setStateFlag(ID.F.CanFollow, false);
                ship.getCapaShipInventory().setStackInSlot(22, switch (work) {
                    case COOKING -> new ItemStack(Items.RAW_IRON);
                    case CRAFTING -> new ItemStack(ModItems.RECIPE_PAPER.get());
                    case FISHING -> new ItemStack(Items.FISHING_ROD);
                    case MINING -> new ItemStack(Items.IRON_PICKAXE);
                });
                ship.setDeltaMovement(0D, 0D, 0D);
                setup.accept(ship);
                if (work == Work.MINING) {
                    ship.tickCount = tick;
                }
                List<String> calls = new ArrayList<>();
                MovementPlanParityGameTests.navigation(ship).recordCalls(calls);
                switch (work) {
                    case COOKING -> TaskHelper.onUpdateCooking(ship);
                    case CRAFTING -> TaskHelper.onUpdateCrafting(ship);
                    case FISHING -> TaskHelper.onUpdateFishing(ship);
                    case MINING -> TaskHelper.onUpdateMining(ship);
                }
                MovementPlanParityGameTests.navigation(ship).recordCalls(null);
                return new Run(calls.stream().map(call -> call.substring(call.indexOf(':') + 1)).toList(),
                        reason(ship), ship.getX(), ship.getY(), ship.getZ());
            } finally {
                level.removeBlock(waypointPos, false);
                level.removeBlock(chestPos, false);
            }
        }
    }

    private static MovementReason reason(BasicEntityShip ship) {
        var last = ((ShipMovementHost) ship).shipMovementExecutor().last();
        return last.isPresent() ? last.get().step().reason() : null;
    }

    private static double[] point(String call) {
        Matcher match = CALL.matcher(call);
        if (!match.find()) {
            throw new IllegalStateException("Not a path request: " + call);
        }
        return new double[] {Double.parseDouble(match.group(1)), Double.parseDouble(match.group(2)),
                Double.parseDouble(match.group(3))};
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, BlockPos at) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "failed to create ship");
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateMinor(ID.M.FormatType, 0);
        ship.moveTo(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add ship");
        return ship;
    }
}
