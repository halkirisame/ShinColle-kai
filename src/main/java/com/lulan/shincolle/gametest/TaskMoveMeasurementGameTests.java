package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipMovementHost;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Measurements for the work-task walk (m4, m5): what a ship does over real ticks, under each authority.
 * They record what was seen; they assert only the fixture and what the recording showed to be fixed.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaskMoveMeasurementGameTests {

    private static final int START = 20;

    private TaskMoveMeasurementGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_task_move_m4_legacy", timeoutTicks = 200)
    public static void m4LegacyGuardStopAndTheFishingWalk(GameTestHelper helper) {
        m4(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "arena", batch = "isolated_task_move_m4_new", timeoutTicks = 200)
    public static void m4NewGuardStopAndTheFishingWalk(GameTestHelper helper) {
        m4(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    @GameTest(template = "arena", batch = "isolated_task_move_m5_legacy", timeoutTicks = 200)
    public static void m5LegacyMiningWhileSittingOrLeashed(GameTestHelper helper) {
        m5(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "arena", batch = "isolated_task_move_m5_new", timeoutTicks = 200)
    public static void m5NewMiningWhileSittingOrLeashed(GameTestHelper helper) {
        m5(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    private static void m4(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
        GameTestEntities entities = GameTestEntities.open(helper);
        Runnable close = () -> {
            try {
                entities.close();
            } finally {
                authority.close();
            }
        };
        try {
            BlockPos spot = helper.absolutePos(new BlockPos(10, 1, 2));
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(2.5D, 1D, 2.5D));
            ship.setStateMinor(ID.M.Task, 2);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            List<String> calls = new ArrayList<>();
            List<String> ticks = new ArrayList<>();
            helper.runAtTickTime(START, () -> {
                if (mode == ConfigHandler.ShipAiTargetAuthority.NEW) {
                    var id = helper.getLevel().dimension().location();
                    ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(
                            new ShipCommand.GuardPosition(new DimensionKey(id.getNamespace(), id.getPath()),
                                    new CommandPos(spot.getX(), spot.getY(), spot.getZ()), false)));
                } else {
                    ship.setGuardedPos(spot.getX(), spot.getY(), spot.getZ(), helper.getLevel().dimension(), 1);
                    ship.setStateFlag(ID.F.CanFollow, false);
                }
                MovementPlanParityGameTests.navigation(ship).recordCalls(calls);
            });
            for (int i = 1; i <= 100; i++) {
                int at = START + i;
                int tick = i;
                helper.runAtTickTime(at, () -> {
                    var last = ((ShipMovementHost) ship).shipMovementExecutor().last();
                    ticks.add(tick + ":" + (ship.getNavigation().isInProgress() ? "path" : "idle") + " d2="
                            + String.format("%.1f", ship.distanceToSqr(Vec3.atCenterOf(spot))) + " last="
                            + last.map(request -> request.tick() + "/" + request.step().reason()).orElse("-"));
                });
            }
            helper.runAtTickTime(START + 102, () -> {
                try {
                    MovementPlanParityGameTests.navigation(ship).recordCalls(null);
                    LogHelper.info("Task m4 " + mode + " calls=" + calls);
                    LogHelper.info("Task m4 " + mode + " ticks=" + ticks);
                    helper.assertTrue(ship.getStateMinor(ID.M.Task) == 2, "Fixture: the ship must be fishing");
                    helper.assertTrue(ship.distanceToSqr(Vec3.atCenterOf(spot)) <= 10D,
                            "The fishing ship must reach its spot: " + ticks);
                    helper.assertTrue(ticks.stream().noneMatch(line -> line.contains("GOAL_STOPPED")),
                            "No goal stop may come between the task's request and the arrival: " + ticks);
                    helper.succeed();
                } finally {
                    close.run();
                }
            });
        } catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
    }

    private static void m5(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
        ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
        GameTestEntities entities = GameTestEntities.open(helper);
        Runnable close = () -> {
            try {
                entities.close();
            } finally {
                authority.close();
            }
        };
        try {
            String[] names = {"sitting", "leashed", "free"};
            BasicEntityShip[] ships = new BasicEntityShip[3];
            List<List<String>> calls = new ArrayList<>();
            Vec3[] starts = new Vec3[3];
            for (int i = 0; i < 3; i++) {
                ships[i] = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(3.5D + 4 * i, 1D, 5.5D));
                ships[i].setStateMinor(ID.M.Task, 3);
                ships[i].getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.IRON_PICKAXE));
                calls.add(new ArrayList<>());
            }
            ArmorStand holder = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(7, 1, 8));
            helper.runAtTickTime(START, () -> {
                ships[0].setEntitySit(true);
                ships[1].setLeashedTo(holder, true);
                for (int i = 0; i < 3; i++) {
                    starts[i] = ships[i].position();
                    MovementPlanParityGameTests.navigation(ships[i]).recordCalls(calls.get(i));
                }
            });
            helper.runAtTickTime(START + 80, () -> {
                try {
                    StringBuilder report = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        MovementPlanParityGameTests.navigation(ships[i]).recordCalls(null);
                        report.append(' ').append(names[i]).append("{sitting=").append(ships[i].getIsSitting())
                                .append(" leashed=").append(ships[i].getIsLeashed()).append(" moved=")
                                .append(String.format("%.2f", ships[i].position().distanceTo(starts[i])))
                                .append(" calls=").append(calls.get(i)).append('}');
                    }
                    LogHelper.info("Task m5 " + mode + ":" + report);
                    helper.assertTrue(ships[0].getIsSitting(), "Fixture: the first ship must be sitting");
                    helper.assertTrue(ships[1].getIsLeashed(), "Fixture: the second ship must be leashed");
                    helper.assertTrue(starts[0].distanceTo(ships[0].position()) < 1D
                                    && starts[1].distanceTo(ships[1].position()) < 1D,
                            "A sitting or leashed ship must not walk away: " + report);
                    helper.succeed();
                } finally {
                    close.run();
                }
            });
        } catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
    }
}
