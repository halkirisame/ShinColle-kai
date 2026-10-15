package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipSitGoal;
import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoalSelectorRebuildGameTests {

    private GoalSelectorRebuildGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_goal_rebuild_friendly_revenge", timeoutTicks = 120)
    public static void friendlyRevengeRestartsAfterRebuild(GameTestHelper helper) {
        TestContext context = TestContext.open(helper);
        try {
            BasicEntityShip ship = friendly(helper, context.entities, new Vec3(1.5D, 2D, 1.5D), false);
            ship.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0D);
            Vec3 pinned = ship.position();
            helper.onEachTick(() -> {
                ship.moveTo(pinned.x, pinned.y, pinned.z);
                ship.setDeltaMovement(Vec3.ZERO);
            });
            Zombie first = zombie(helper, context.entities, new Vec3(3.0D, 2D, 1.5D));
            Zombie second = zombie(helper, context.entities, new Vec3(1.5D, 2D, 3.0D));
            WrappedGoal[] removed = {null};

            helper.runAtTickTime(24, () -> runStage(context,
                    () -> ship.setStateFlag(ID.F.PassiveAI, true)));
            helper.runAtTickTime(30, () -> runStage(context, () -> queueRevenge(ship, first)));
            helper.runAtTickTime(50, () -> runStage(context, () -> {
                removed[0] = requireRunning(helper, ship, "targetSelector", ShipTargetAuthorityGoal.class);
                helper.assertTrue(ship.getTarget() == first, "Friendly revenge did not acquire its first attacker");
                // The movement-speed attribute alone does not pin the ship; keep the rebuilt
                // target goal's range wider than the fixture's possible drift.
                ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
                rebuildTargets(ship);
                queueRevenge(ship, second);
            }));
            helper.runAtTickTime(90, () -> finish(context, helper, () -> {
                WrappedGoal rebuilt = requireRunning(
                        helper, ship, "targetSelector", ShipTargetAuthorityGoal.class);
                helper.assertTrue(rebuilt != removed[0], "Removed friendly revenge goal resumed instead of its replacement");
                helper.assertTrue(ship.getTarget() == second,
                        "Rebuilt friendly revenge goal did not acquire the second attacker");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_goal_rebuild_friendly_auto", timeoutTicks = 120)
    public static void friendlyAutoTargetRestartsAfterRebuild(GameTestHelper helper) {
        TestContext context = TestContext.open(helper);
        try {
            BasicEntityShip ship = friendly(helper, context.entities, new Vec3(1.5D, 2D, 1.5D), false);
            ship.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0D);
            Zombie target = zombie(helper, context.entities, new Vec3(3.5D, 2D, 1.5D));
            WrappedGoal[] removed = {null};

            helper.runAtTickTime(24, () -> runStage(context,
                    () -> ship.setStateFlag(ID.F.PassiveAI, false)));
            helper.runAtTickTime(50, () -> runStage(context, () -> {
                removed[0] = requireRunning(helper, ship, "targetSelector", ShipTargetAuthorityGoal.class);
                helper.assertTrue(ship.getTarget() == target, "Friendly automatic targeting did not acquire its fixture");
                // The movement-speed attribute alone does not pin the ship; keep the rebuilt
                // target goal's range wider than the fixture's possible drift.
                ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
                rebuildTargets(ship);
            }));
            helper.runAtTickTime(90, () -> finish(context, helper, () -> {
                WrappedGoal rebuilt = requireRunning(helper, ship, "targetSelector", ShipTargetAuthorityGoal.class);
                helper.assertTrue(rebuilt != removed[0], "Removed automatic target goal resumed instead of its replacement");
                helper.assertTrue(ship.getTarget() == target,
                        "Rebuilt automatic target goal did not reacquire its fixture");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_goal_rebuild_guarding", timeoutTicks = 120)
    public static void guardingRestartsAfterRebuild(GameTestHelper helper) {
        TestContext context = TestContext.open(helper);
        try {
            BasicEntityShip ship = friendly(helper, context.entities, new Vec3(1.5D, 2D, 2.5D), false);
            ship.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.6D);
            BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 2));
            WrappedGoal[] removed = {null};
            Vec3[] rebuildPosition = {null};

            helper.runAtTickTime(24, () -> runStage(context, () -> {
                ship.setStateMinor(ID.M.FormatType, 0);
                ship.setStateMinor(ID.M.FollowMin, 1);
                ship.setStateMinor(ID.M.FollowMax, 2);
                ship.setStateFlag(ID.F.PickItem, false);
                guard(helper, ship, destination);
            }));
            helper.runAtTickTime(44, () -> runStage(context, () -> {
                float max = ship.getStateMinor(ID.M.FollowMax) + ship.getBbWidth() * 0.75F;
                if (ship.getStateFlag(ID.F.PickItem)) max += 5F;
                double dx = destination.getX() + 0.5D - ship.getX();
                double dy = destination.getY() + 0.5D - ship.getY();
                double dz = destination.getZ() + 0.5D - ship.getZ();
                double distanceSq = dx * dx + dy * dy + dz * dz;
                helper.assertTrue(distanceSq > max * max,
                        "Guard rebuild fixture must start outside max follow range: distSq=" + distanceSq
                                + " maxDistSq=" + max * max);
                removed[0] = requireRunning(helper, ship, "goalSelector", ShipGuardingGoal.class);
                rebuildPosition[0] = ship.position();
                rebuildGoals(ship);
            }));
            helper.runAtTickTime(84, () -> finish(context, helper, () -> {
                WrappedGoal rebuilt = requireRunning(helper, ship, "goalSelector", ShipGuardingGoal.class);
                helper.assertTrue(rebuilt != removed[0], "Removed guarding goal resumed instead of its replacement");
                helper.assertTrue(ship.position().distanceToSqr(rebuildPosition[0]) > 0.25D,
                        "Ship did not move after rebuilding its guarding goal");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_goal_rebuild_sitting", timeoutTicks = 120)
    public static void sittingCommandSurvivesRebuild(GameTestHelper helper) {
        Vec3 spawn = new Vec3(1.5D, 2D, 2.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            TestContext context = TestContext.open(helper);
            try {
                BasicEntityShip ship = friendly(helper, context.entities, spawn, false);
                ship.setInvulnerable(true);
                ship.setHealth(ship.getMaxHealth());
                ship.setNoGravity(true);
                // Keep initialization free of wandering before applying the commands under test.
                ship.setEntitySit(true);
                helper.assertTrue(!ship.isNoAi() && ship.isInvulnerable() && ship.isNoGravity()
                                && ship.getHealth() == ship.getMaxHealth(),
                        "Sitting rebuild fixture must retain AI with isolated physics and full health");
                GameTestEntities.assertRegistered(helper, ship);
                BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 2));
                WrappedGoal[] removed = {null};
                Vec3[] stationaryPosition = {null};
                Vec3[] rebuildPosition = {null};
                java.util.function.Function<Vec3, String> diagnostics = baseline ->
                        " baseline=" + baseline + " position=" + ship.position()
                                + " difference=" + ship.position().subtract(baseline)
                                + " velocity=" + ship.getDeltaMovement() + " shipTick=" + ship.tickCount
                                + " onGround=" + ship.onGround() + " noGravity=" + ship.isNoGravity()
                                + " orderedToSit=" + ship.isOrderedToSit()
                                + " navigationDone=" + ship.getNavigation().isDone()
                                + " moveControlUpdating=" + (ship.getMoveControl()
                                instanceof com.lulan.shincolle.ai.path.ShipMoveControl control && control.isUpdating())
                                + " nearby=" + helper.getLevel().getEntities(ship,
                                        ship.getBoundingBox().inflate(0.25D)).stream()
                                .map(entity -> entity.getType().getDescriptionId() + "#" + entity.getId()
                                        + "@" + entity.position() + " motion=" + entity.getDeltaMovement()
                                        + " overlaps=" + entity.getBoundingBox().intersects(ship.getBoundingBox()))
                                .toList();

                helper.startSequence()
                        .thenWaitUntil(() -> helper.assertTrue(ship.tickCount >= 24,
                                "Waiting for ship initialization, actual shipTick=" + ship.tickCount))
                        .thenExecute(() -> runStage(context, () -> {
                            helper.assertTrue(ship.tickCount >= 24, "Ship must finish initial goal registration");
                            ship.setStateMinor(ID.M.FormatType, 0);
                            guard(helper, ship, destination);
                            ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
                        }))
                        .thenIdle(16)
                        .thenExecute(() -> runStage(context, () -> stationaryPosition[0] = ship.position()))
                        .thenIdle(4)
                        .thenExecute(() -> runStage(context, () -> {
                            removed[0] = requireRunning(helper, ship, "goalSelector", ShipSitGoal.class);
                            helper.assertTrue(ship.position().distanceToSqr(stationaryPosition[0]) < 0.0001D
                                            && ship.getDeltaMovement().lengthSqr() < 0.0001D
                                            && ship.getNavigation().isDone(),
                                    "Ship must be stationary before rebuilding" + diagnostics.apply(stationaryPosition[0]));
                            rebuildPosition[0] = ship.position();
                            rebuildGoals(ship);
                        }))
                        .thenIdle(40)
                        .thenExecute(() -> finish(context, helper, () -> {
                            WrappedGoal rebuilt = requireRunning(helper, ship, "goalSelector", ShipSitGoal.class);
                            helper.assertTrue(rebuilt != removed[0],
                                    "Removed sitting goal resumed instead of its replacement"
                                            + diagnostics.apply(rebuildPosition[0]));
                            helper.assertTrue(ship.isOrderedToSit(),
                                    "AI rebuild cleared the ship's sitting command" + diagnostics.apply(rebuildPosition[0]));
                            helper.assertTrue(ship.position().distanceToSqr(rebuildPosition[0]) < 0.01D,
                                    "Ship moved after rebuilding while ordered to sit" + diagnostics.apply(rebuildPosition[0]));
                        }));
            } catch (Throwable error) {
                context.close();
                throw error;
            }
        }, spawn);
    }

    @GameTest(template = "arena", batch = "isolated_goal_rebuild_hostile_revenge", timeoutTicks = 120)
    public static void hostileRevengeRestartsAfterRebuild(GameTestHelper helper) {
        TestContext context = TestContext.open(helper);
        try {
            BasicEntityShipHostile ship = hostile(helper, context.entities, new Vec3(1.5D, 2D, 1.5D), false);
            ship.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0D);
            Zombie first = zombie(helper, context.entities, new Vec3(3.0D, 2D, 1.5D));
            Zombie second = zombie(helper, context.entities, new Vec3(1.5D, 2D, 3.0D));
            WrappedGoal[] removed = {null};

            helper.runAtTickTime(30, () -> runStage(context, () -> queueRevenge(ship, first)));
            helper.runAtTickTime(50, () -> runStage(context, () -> {
                removed[0] = requireRunning(helper, ship, "targetSelector", ShipTargetAuthorityGoal.class);
                helper.assertTrue(ship.getTarget() == first, "Hostile revenge did not acquire its first attacker");
                // Preserve the initial range check while allowing the rebuilt goal to retain
                // the second attacker if the ship drifts away from its spawn position.
                ship.getAttrs().setAttrsBuffed(ID.Attrs.HIT, 32F);
                rebuildHostileTargets(ship);
                rebuildHostileTargets(ship);
                queueRevenge(ship, second);
            }));
            helper.runAtTickTime(90, () -> finish(context, helper, () -> {
                WrappedGoal rebuilt = requireRunning(
                        helper, ship, "targetSelector", ShipTargetAuthorityGoal.class);
                helper.assertTrue(rebuilt != removed[0], "Removed hostile revenge goal resumed instead of its replacement");
                helper.assertTrue(ship.getTarget() == second,
                        "Rebuilt hostile revenge goal did not acquire the second attacker");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    private static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities,
                                             Vec3 position, boolean noAi) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create a friendly ship");
        ship.setNoAi(noAi);
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.calcShipAttributes(31, false);
        ship.setAmmoLight(0);
        ship.setAmmoHeavy(0);
        disableAttacks(ship);
        move(helper, ship, position);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add a friendly ship");
        return ship;
    }

    private static BasicEntityShipHostile hostile(GameTestHelper helper, GameTestEntities entities,
                                                   Vec3 position, boolean noAi) {
        BasicEntityShipHostile ship = entities.add(ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create a hostile ship");
        ship.setNoAi(noAi);
        ship.setInvulnerable(noAi);
        ship.calcShipAttributes(31, false);
        ship.setAmmoLight(0);
        ship.setAmmoHeavy(0);
        disableAttacks(ship);
        move(helper, ship, position);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add a hostile ship");
        return ship;
    }

    private static Zombie zombie(GameTestHelper helper, GameTestEntities entities, Vec3 position) {
        Zombie zombie = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
        helper.assertTrue(zombie != null, "Failed to create a target zombie");
        zombie.setNoAi(true);
        zombie.setInvulnerable(true);
        zombie.setPersistenceRequired();
        move(helper, zombie, position);
        helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Failed to add a target zombie");
        return zombie;
    }

    private static void queueRevenge(com.lulan.shincolle.entity.IShipAttackBase ship, Entity target) {
        ship.setEntityRevengeTarget(target);
        ship.setEntityRevengeTime();
    }

    private static void disableAttacks(com.lulan.shincolle.entity.IShipAttackBase ship) {
        ship.setStateFlag(ID.F.UseAmmoLight, false);
        ship.setStateFlag(ID.F.UseAmmoHeavy, false);
        ship.setStateFlag(ID.F.UseAirLight, false);
        ship.setStateFlag(ID.F.UseAirHeavy, false);
        ship.setStateFlag(ID.F.AtkType_Light, false);
        ship.setStateFlag(ID.F.AtkType_Heavy, false);
        ship.setStateFlag(ID.F.AtkType_AirLight, false);
        ship.setStateFlag(ID.F.AtkType_AirHeavy, false);
    }

    private static void guard(GameTestHelper helper, BasicEntityShip ship, BlockPos destination) {
        var id = helper.getLevel().dimension().location();
        ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                        new DimensionKey(id.getNamespace(), id.getPath()),
                        new CommandPos(destination.getX(), destination.getY(), destination.getZ()), false)));
    }

    /** The ship's goals made again from scratch, as when its AI is rebuilt. */
    private static void rebuildGoals(BasicEntityShip ship) {
        invokeNoArg(ship, BasicEntityShip.class, "clearAITasks");
        invokeNoArg(ship, BasicEntityShip.class, "setAIList");
    }

    private static void rebuildTargets(BasicEntityShip ship) {
        invokeNoArg(ship, BasicEntityShip.class, "clearAITargetTasks");
        ship.setAITargetList();
    }

    private static void rebuildHostileTargets(BasicEntityShipHostile ship) {
        invokeNoArg(ship, BasicEntityShipHostile.class, "clearAITargetTasks");
        ship.setAITargetList();
    }

    private static WrappedGoal requireRunning(GameTestHelper helper, Mob mob, String selectorName,
                                              Class<? extends Goal> goalType) {
        WrappedGoal wrapped = selector(mob, selectorName).getAvailableGoals().stream()
                .filter(candidate -> goalType.isInstance(candidate.getGoal()))
                .findFirst()
                .orElse(null);
        helper.assertTrue(wrapped != null, "Missing " + goalType.getSimpleName());
        helper.assertTrue(wrapped.isRunning(), goalType.getSimpleName() + " was not running");
        return wrapped;
    }

    private static GoalSelector selector(Mob mob, String fieldName) {
        try {
            Field field = Mob.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Failed to inspect " + fieldName, error);
        }
    }

    private static void invokeNoArg(Object instance, Class<?> owner, String methodName) {
        try {
            Method method = owner.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(instance);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Failed to invoke " + owner.getSimpleName() + '.' + methodName, error);
        }
    }

    private static void move(GameTestHelper helper, Entity entity, Vec3 relativePosition) {
        Vec3 absolute = helper.absoluteVec(relativePosition);
        entity.moveTo(absolute.x, absolute.y, absolute.z, 0F, 0F);
    }

    private static void runStage(TestContext context, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    private static void finish(TestContext context, GameTestHelper helper, Runnable assertions) {
        try {
            assertions.run();
            helper.succeed();
        } finally {
            context.close();
        }
    }

    private static final class TestContext implements AutoCloseable {
        private final GameTestEntities entities;
        private boolean closed;

        private TestContext(GameTestEntities entities) {
            this.entities = entities;
        }

        static TestContext open(GameTestHelper helper) {
            return new TestContext(GameTestEntities.open(helper));
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.entities.close();
        }
    }
}
