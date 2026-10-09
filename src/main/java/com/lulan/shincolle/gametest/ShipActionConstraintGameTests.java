package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipActionConstraintGameTests {
    private ShipActionConstraintGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_action_constraint_goals", timeoutTicks = 200)
    public static void newFuelOutKeepsGoalsAndStopsThem(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 2D, 2.5D));
            Object[] registered = {null};
            WrappedGoal[] guarding = {null};
            Vec3[] stopped = {null};
            helper.runAtTickTime(20, () -> context.stage(() -> {
                helper.assertTrue(!goals(ship, "goalSelector").isEmpty() && !goals(ship, "targetSelector").isEmpty(),
                        "Fixture must register its goals");
                registered[0] = List.of(goals(ship, "goalSelector"), goals(ship, "targetSelector"));
                BlockPos destination = helper.absolutePos(new BlockPos(10, 2, 2));
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(dimension(helper),
                                new CommandPos(destination.getX(), destination.getY(), destination.getZ()), false)));
            }));
            helper.runAtTickTime(40, () -> context.stage(() -> {
                guarding[0] = running(ship, ShipGuardingGoal.class);
                helper.assertTrue(guarding[0] != null, "Guarding must run before fuel runs out");
                ship.setStateMinor(ID.M.NumGrudge, 0);
                ship.decrGrudgeNum(0);
            }));
            helper.runAtTickTime(50, () -> context.stage(() -> {
                helper.assertTrue(ship.getStateFlag(ID.F.NoFuel), "Fixture must run out of fuel");
                helper.assertTrue(registered[0].equals(List.of(goals(ship, "goalSelector"), goals(ship, "targetSelector"))),
                        "Running out of fuel must keep the registered goals");
                helper.assertTrue(runningCount(ship, "goalSelector") == 0 && runningCount(ship, "targetSelector") == 0,
                        "No goal may run while out of fuel: " + runningNames(ship));
                stopped[0] = ship.position();
            }));
            helper.runAtTickTime(80, () -> context.stage(() -> {
                helper.assertTrue(horizontal(ship.position(), stopped[0]) < 0.05D, "Ship moved while out of fuel");
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.decrGrudgeNum(0);
            }));
            helper.runAtTickTime(120, () -> context.finish(() -> {
                helper.assertTrue(!ship.getStateFlag(ID.F.NoFuel), "Fixture must be refuelled");
                helper.assertTrue(registered[0].equals(List.of(goals(ship, "goalSelector"), goals(ship, "targetSelector"))),
                        "Refuelling must not re-register goals");
                helper.assertTrue(guarding[0].isRunning(), "The same guarding goal must resume after refuelling");
                helper.assertTrue(horizontal(ship.position(), stopped[0]) > 0.25D, "Ship did not move after refuelling");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_action_constraint_effects", timeoutTicks = 100)
    public static void newFuelOutEffectsHappenOnce(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 2D, 2.5D));
            Cow cow = context.entities.add(EntityType.COW.create(helper.getLevel()));
            cow.setNoAi(true);
            cow.moveTo(helper.absoluteVec(new Vec3(6.5D, 2D, 2.5D)));
            helper.getLevel().addFreshEntity(cow);
            helper.runAtTickTime(20, () -> context.stage(() -> {
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.Attack(ShipCommandStateAdapter.handle(cow))));
                ship.setMorale(3000);
                ship.setStateMinor(ID.M.NumGrudge, 0);
                ship.decrGrudgeNum(0);
            }));
            helper.runAtTickTime(30, () -> context.stage(() -> {
                helper.assertTrue(ship.getMorale() == 0, "Running out of fuel must drop morale to zero");
                helper.assertTrue(ship.getCommandState().manualAttack().isEmpty(),
                        "Running out of fuel must clear the manual attack");
                ship.setMorale(3000);
                ship.decrGrudgeNum(0);
            }));
            helper.runAtTickTime(40, () -> context.finish(() ->
                    helper.assertTrue(ship.getMorale() == 3000, "Staying out of fuel must not repeat the effects")));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_action_constraint_mount", timeoutTicks = 60)
    public static void newMountGoalWaitsWhileHostIsOutOfFuel(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 2D, 2.5D));
            BasicEntityMount mount = context.entities.add(ModEntities.MOUNT_BAH.get().create(helper.getLevel()));
            mount.moveTo(helper.absoluteVec(new Vec3(9.5D, 2D, 2.5D)));
            helper.getLevel().addFreshEntity(mount);
            mount.setHost(ship);
            helper.runAtTickTime(20, () -> context.finish(() -> {
                WrappedGoal follow = goals(mount, "goalSelector").get(0);
                helper.assertTrue(follow.getGoal().canUse(), "Fixture mount must want to follow its host");
                ship.setStateMinor(ID.M.NumGrudge, 0);
                ship.decrGrudgeNum(0);
                helper.assertTrue(!follow.getGoal().canUse(), "Mount must wait while its host is out of fuel");
            }));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    @GameTest(template = "arena", batch = "isolated_action_constraint_legacy", timeoutTicks = 60)
    public static void legacyFuelOutStillRemovesGoals(GameTestHelper helper) {
        Context context = Context.open(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
        try {
            BasicEntityShip ship = ship(helper, context, new Vec3(1.5D, 2D, 2.5D));
            helper.runAtTickTime(20, () -> context.stage(() -> {
                helper.assertTrue(!goals(ship, "goalSelector").isEmpty(), "Fixture must register its goals");
                ship.setStateMinor(ID.M.NumGrudge, 0);
                ship.decrGrudgeNum(0);
            }));
            helper.runAtTickTime(30, () -> context.finish(() ->
                    helper.assertTrue(goals(ship, "goalSelector").isEmpty() && goals(ship, "targetSelector").isEmpty(),
                            "LEGACY must still remove the goals when fuel runs out")));
        } catch (Throwable error) {
            context.close();
            throw error;
        }
    }

    private static BasicEntityShip ship(GameTestHelper helper, Context context, Vec3 position) {
        BasicEntityShip ship = context.entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create a friendly ship");
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PickItem, false);
        ship.setStateMinor(ID.M.FormatType, 0);
        ship.setStateMinor(ID.M.FollowMin, 1);
        ship.setStateMinor(ID.M.FollowMax, 2);
        ship.calcShipAttributes(31, false);
        ship.setStateFlag(ID.F.UseAmmoLight, false);
        ship.setStateFlag(ID.F.UseAmmoHeavy, false);
        ship.setStateFlag(ID.F.AtkType_Light, false);
        ship.setStateFlag(ID.F.AtkType_Heavy, false);
        ship.moveTo(helper.absoluteVec(position));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add a friendly ship");
        return ship;
    }

    private static DimensionKey dimension(GameTestHelper helper) {
        var id = helper.getLevel().dimension().location();
        return new DimensionKey(id.getNamespace(), id.getPath());
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<WrappedGoal> goals(Mob mob, String selectorName) {
        return List.copyOf(selector(mob, selectorName).getAvailableGoals());
    }

    private static long runningCount(Mob mob, String selectorName) {
        return selector(mob, selectorName).getAvailableGoals().stream().filter(WrappedGoal::isRunning).count();
    }

    private static List<String> runningNames(Mob mob) {
        return java.util.stream.Stream.of("goalSelector", "targetSelector")
                .flatMap(name -> selector(mob, name).getAvailableGoals().stream())
                .filter(WrappedGoal::isRunning)
                .map(wrapped -> wrapped.getGoal().getClass().getName())
                .toList();
    }

    private static WrappedGoal running(Mob mob, Class<?> goalType) {
        return selector(mob, "goalSelector").getAvailableGoals().stream()
                .filter(wrapped -> goalType.isInstance(wrapped.getGoal()) && wrapped.isRunning())
                .findFirst().orElse(null);
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

    private static final class Context implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameTestEntities entities;
        private final ShipAiAuthorityOverride authority;
        private boolean closed;

        private Context(GameTestHelper helper, GameTestEntities entities, ShipAiAuthorityOverride authority) {
            this.helper = helper;
            this.entities = entities;
            this.authority = authority;
        }

        static Context open(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode) {
            ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
            try {
                return new Context(helper, GameTestEntities.open(helper), authority);
            } catch (RuntimeException | Error failure) {
                authority.close();
                throw failure;
            }
        }

        void stage(Runnable action) {
            try {
                action.run();
            } catch (Throwable error) {
                close();
                throw error;
            }
        }

        void finish(Runnable assertions) {
            try {
                assertions.run();
                this.helper.succeed();
            } finally {
                close();
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                this.entities.close();
            } finally {
                this.authority.close();
            }
        }
    }
}
