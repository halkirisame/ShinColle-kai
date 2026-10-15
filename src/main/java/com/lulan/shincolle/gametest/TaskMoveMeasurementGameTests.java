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
 * Measurements for the work-task walk (m4, m5): what a ship does over real ticks.
 * They record what was seen; they assert only the fixture and what the recording showed to be fixed.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaskMoveMeasurementGameTests {

    private static final int START = 20;

    private TaskMoveMeasurementGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_task_move_m4_new", timeoutTicks = 200)
    public static void m4GuardStopAndTheFishingWalk(GameTestHelper helper) {
        m4(helper);
    }

    @GameTest(template = "arena", batch = "isolated_task_move_m5_new", timeoutTicks = 200)
    public static void m5MiningWhileSittingOrLeashed(GameTestHelper helper) {
        m5(helper);
    }

    private static void m4(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        Runnable close = entities::close;
        try {
            BlockPos spot = helper.absolutePos(new BlockPos(10, 1, 2));
            BasicEntityShip ship = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(2.5D, 1D, 2.5D));
            ship.setStateMinor(ID.M.Task, 2);
            ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
            List<String> calls = new ArrayList<>();
            List<String> ticks = new ArrayList<>();
            helper.runAtTickTime(START, () -> {
                var id = helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()), new CommandStateOp.Apply(
                        new ShipCommand.GuardPosition(new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(spot.getX(), spot.getY(), spot.getZ()), false)));
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
                    LogHelper.info("Task m4 calls=" + calls);
                    LogHelper.info("Task m4 ticks=" + ticks);
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

    private static void m5(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        Runnable close = entities::close;
        try {
            String[] names = {"sitting", "leashed", "free"};
            BasicEntityShip[] ships = new BasicEntityShip[3];
            List<List<String>> calls = new ArrayList<>();
            Vec3[] starts = new Vec3[3];
            Vec3[] spawnPositions = new Vec3[3];
            Vec3[] startMotion = new Vec3[3];
            int[] startTicks = new int[3];
            int measurementStart = 24;
            for (int i = 0; i < 3; i++) {
                ships[i] = MovementPlanParityGameTests.friendly(helper, entities, new Vec3(3.5D + 4 * i, 1D, 5.5D));
                ships[i].setStateMinor(ID.M.Task, 3);
                ships[i].getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.IRON_PICKAXE));
                ships[i].goalSelector.removeAllGoals(goal -> goal instanceof com.lulan.shincolle.ai.ShipWanderGoal);
                spawnPositions[i] = ships[i].position();
                calls.add(new ArrayList<>());
            }
            ArmorStand holder = entities.add(helper.spawn(EntityType.ARMOR_STAND, new BlockPos(7, 1, 8)));
            helper.runAtTickTime(measurementStart, () -> {
                for (int i = 0; i < 3; i++) {
                    ships[i].goalSelector.removeAllGoals(goal -> goal instanceof com.lulan.shincolle.ai.ShipWanderGoal);
                    ships[i].getNavigation().stop();
                    Vec3 spawn = spawnPositions[i];
                    ships[i].moveTo(spawn.x, spawn.y, spawn.z, 0F, 0F);
                    ships[i].setDeltaMovement(Vec3.ZERO);
                }
                Vec3 holderPosition = ships[1].position().add(0D, 0D, 1.5D);
                holder.moveTo(holderPosition.x, holderPosition.y, holderPosition.z, 0F, 0F);
                ships[0].setEntitySit(true);
                ships[1].setLeashedTo(holder, true);
                helper.assertTrue(ConfigHandler.enableTask[2], "Fixture: mining must be enabled");
                for (int i = 0; i < 3; i++) {
                    String fixture = names[i] + " tick=" + ships[i].tickCount + " position=" + ships[i].position()
                            + " spawn=" + spawnPositions[i] + " motion=" + ships[i].getDeltaMovement()
                            + " holder=" + holder.position() + " holderDistance=" + ships[1].distanceTo(holder);
                    helper.assertTrue(ships[i].tickCount > 16, "Fixture: deferred AI registration must finish: " + fixture);
                    helper.assertTrue(ships[i].goalSelector.getAvailableGoals().stream().noneMatch(
                                    goal -> goal.getGoal() instanceof com.lulan.shincolle.ai.ShipWanderGoal),
                            "Fixture: no wander goal may remain: " + fixture);
                    helper.assertTrue(ships[i].position().equals(spawnPositions[i]),
                            "Fixture: the ship must be at its spawn position: " + fixture);
                    helper.assertTrue(ships[i].getDeltaMovement().x == 0D && ships[i].getDeltaMovement().z == 0D,
                            "Fixture: horizontal motion must be zero: " + fixture);
                    helper.assertTrue(ships[i].getStateMinor(ID.M.Task) == 3,
                            "Fixture: the ship must be mining: " + fixture);
                    helper.assertTrue(ships[i].getCapaShipInventory().getStackInSlot(22).is(Items.IRON_PICKAXE),
                            "Fixture: the ship must hold its pickaxe: " + fixture);
                    helper.assertTrue(ships[i].getStateMinor(ID.M.NumGrudge) > 0 && !ships[i].getStateFlag(ID.F.NoFuel),
                            "Fixture: the ship must have fuel: " + fixture);
                    starts[i] = ships[i].position();
                    startMotion[i] = ships[i].getDeltaMovement();
                    startTicks[i] = ships[i].tickCount;
                    MovementPlanParityGameTests.navigation(ships[i]).recordCalls(calls.get(i));
                }
                helper.assertTrue(ships[0].getIsSitting(), "Fixture: the first ship must start sitting");
                helper.assertTrue(ships[1].getIsLeashed() && ships[1].getLeashHolder() == holder,
                        "Fixture: the second ship must start leashed to its holder");
                helper.assertTrue(holder.isAlive() && ships[1].distanceTo(holder) <= 2F
                                && !ships[1].getBoundingBox().intersects(holder.getBoundingBox()),
                        "Fixture: the holder must be alive, close and non-overlapping: holder=" + holder.position()
                                + " ship=" + ships[1].position() + " distance=" + ships[1].distanceTo(holder));
            });
            helper.runAtTickTime(measurementStart + 80, () -> {
                try {
                    StringBuilder report = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        MovementPlanParityGameTests.navigation(ships[i]).recordCalls(null);
                        report.append(' ').append(names[i]).append("{sitting=").append(ships[i].getIsSitting())
                                .append(" leashed=").append(ships[i].getIsLeashed()).append(" moved=")
                                .append(String.format("%.2f", ships[i].position().distanceTo(starts[i])))
                                .append(" start=").append(starts[i]).append(" end=").append(ships[i].position())
                                .append(" startMotion=").append(startMotion[i])
                                .append(" endMotion=").append(ships[i].getDeltaMovement())
                                .append(" startTick=").append(startTicks[i]).append(" endTick=").append(ships[i].tickCount)
                                .append(" movementGoals=").append(ships[i].goalSelector.getRunningGoals()
                                        .filter(goal -> goal.getFlags().contains(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE))
                                        .map(goal -> goal.getGoal().getClass().getSimpleName()).toList())
                                .append(" calls=").append(calls.get(i)).append('}');
                    }
                    report.append(" holder{position=").append(holder.position()).append(" alive=").append(holder.isAlive())
                            .append(" distance=").append(ships[1].distanceTo(holder)).append('}');
                    LogHelper.info("Task m5:" + report);
                    for (int i = 0; i < 3; i++) {
                        helper.assertTrue(ships[i].isAlive(),
                                "Fixture: " + names[i] + " ship must remain alive: " + report);
                        helper.assertTrue(ships[i].tickCount / 64 > startTicks[i] / 64,
                                "Fixture: " + names[i] + " ship must cross a mining shuffle tick: " + report);
                        helper.assertTrue(ships[i].getStateMinor(ID.M.Task) == 3,
                                "Fixture: " + names[i] + " ship must remain mining: " + report);
                        helper.assertTrue(ships[i].getCapaShipInventory().getStackInSlot(22).is(Items.IRON_PICKAXE),
                                "Fixture: " + names[i] + " ship must retain its pickaxe: " + report);
                        helper.assertTrue(ships[i].getStateMinor(ID.M.NumGrudge) > 0 && !ships[i].getStateFlag(ID.F.NoFuel),
                                "Fixture: " + names[i] + " ship must retain fuel: " + report);
                    }
                    helper.assertTrue(calls.get(2).stream().anyMatch(call -> {
                        if (!call.matches("\\d+:moveTo\\(-?\\d.*")) return false;
                        int tick = Integer.parseInt(call.substring(0, call.indexOf(':')));
                        return (tick & 63) == 0 && tick > startTicks[2] && tick <= ships[2].tickCount;
                    }), "Fixture: the free ship must request a mining shuffle path during measurement: " + report);
                    helper.assertTrue(ships[0].getIsSitting(), "Fixture: the first ship must be sitting: " + report);
                    helper.assertTrue(ships[1].getIsLeashed(), "Fixture: the second ship must be leashed: " + report);
                    helper.assertTrue(starts[0].distanceTo(ships[0].position()) < 1D,
                            "A sitting ship must not walk away: " + report);
                    helper.assertTrue(starts[1].distanceTo(ships[1].position()) < 1D,
                            "A leashed ship must not walk away: " + report);
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
