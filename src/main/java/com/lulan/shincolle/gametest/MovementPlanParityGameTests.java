package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipAttackOnCollideGoal;
import com.lulan.shincolle.ai.ShipFleeGoal;
import com.lulan.shincolle.ai.ShipFollowOwnerGoal;
import com.lulan.shincolle.ai.ShipGuardingGoal;
import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.ShipMovementHost;
import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipWanderGoal;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.path.ShipPathNavigation;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Under NEW the movement goals stop and request paths through one executor; this must not change
 * where or when a ship walks or teleports. Each test runs one fixture under LEGACY and then under
 * NEW at the same place and compares, counted from the tick the watched goal first starts (the goal
 * registered when the ship's AI starts up): every stop and path request made through the ship's
 * navigation, the goal's starts and stops, and every jump in position, which is a teleport.
 * Both authorities ran the same movement code before the executor, so a NEW that still matches
 * LEGACY is a NEW that still moves as before. Each phase also checks that only NEW went through
 * the executor.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementPlanParityGameTests {
    private static final int PHASE = 240;
    private static final int TIMEOUT = 2 * PHASE + 80;
    /** A teleport moves the ship further than this in one tick; walking never does. */
    private static final double JUMP_SQ = 9D;
    /** A ship registers its goals again on its 16th tick; nothing may move it before. */
    private static final int RELEASE_AT = 24;
    /**
     * A following ship under NEW measures its distance every 32 ticks, so it walks on to the owner's
     * last measured place where LEGACY stops as it enters the inner distance; the two are compared
     * up to the second measurement.
     */
    private static final int FOLLOW_COMPARED_TICKS = 32;
    private static final Vec3 SHIP = new Vec3(2.5D, 0D, 2.5D);
    private static final Vec3 NEAR = new Vec3(14.5D, 0D, 2.5D);
    /** The chunks the ship walks through, held entity-ticking for the whole test. */
    private static final Vec3[] ROUTE = {new Vec3(2.5D, 2D, 2.5D), new Vec3(8.5D, 2D, 2.5D),
            new Vec3(14.5D, 2D, 2.5D), new Vec3(22.5D, 2D, 2.5D)};

    private MovementPlanParityGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_movement_plan_follow_walk", timeoutTicks = TIMEOUT)
    public static void followWalksOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, phase -> phase.follow(NEAR, false, 1_000), ShipFollowOwnerGoal.class, FOLLOW_COMPARED_TICKS,
                outcome -> {
            List<String> problems = new ArrayList<>();
            if (outcome.calls.stream().noneMatch(call -> call.contains(":moveTo("))) problems.add("never walked");
            if (!outcome.jumps.isEmpty()) problems.add("teleported while walking");
            return problems;
        });
    }

    // A stuck ship recovers and teleports by the NEW rules; see MovementStuckRecoveryGameTests.

    @GameTest(template = "arena", batch = "isolated_movement_plan_guard_walk", timeoutTicks = TIMEOUT)
    public static void guardWalksOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, phase -> phase.guard(false, 1_000), ShipGuardingGoal.class, outcome -> {
            List<String> problems = new ArrayList<>();
            if (outcome.calls.stream().noneMatch(call -> call.contains(":moveTo("))) problems.add("never walked");
            if (outcome.calls.stream().noneMatch(call -> call.endsWith(":stop"))) problems.add("never arrived");
            return problems;
        });
    }

    @GameTest(template = "arena", batch = "isolated_movement_plan_flee", timeoutTicks = TIMEOUT)
    public static void fleeWalksToOwnerOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, Phase::flee, ShipFleeGoal.class, outcome -> outcome.calls.stream()
                .anyMatch(call -> call.contains(":moveTo(")) ? List.of() : List.of("never walked"));
    }

    @GameTest(template = "arena", batch = "isolated_movement_plan_pick_item", timeoutTicks = TIMEOUT)
    public static void pickItemWalksToItemOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        // NEW gives up an item it has stood at without being able to take it for 80 ticks of failed takes
        // (from tick 102 here); LEGACY keeps trying, so only the first 100 ticks are compared
        verify(helper, Phase::pickItem, ShipPickItemGoal.class, 100, outcome -> {
            List<String> problems = new ArrayList<>();
            if (outcome.calls.stream().noneMatch(call -> call.contains(":moveTo("))) problems.add("never walked");
            if (outcome.calls.stream().noneMatch(call -> call.endsWith(":stop"))) problems.add("never reached");
            return problems;
        });
    }

    @GameTest(template = "arena", batch = "isolated_movement_plan_melee", timeoutTicks = TIMEOUT)
    public static void meleeClosesInOnSameTicksUnderEitherAuthority(GameTestHelper helper) {
        verify(helper, Phase::melee, ShipAttackOnCollideGoal.class, outcome -> {
            List<String> problems = new ArrayList<>();
            if (outcome.calls.stream().noneMatch(call -> call.contains(":moveTo("))) problems.add("never closed in");
            if (outcome.calls.stream().noneMatch(call -> call.endsWith(":stop"))) problems.add("never held");
            return problems;
        });
    }

    /** Wander draws its point from the entity random, which the authorities do not share; NEW only. */
    @GameTest(template = "arena", batch = "isolated_movement_plan_wander", timeoutTicks = 40)
    public static void wanderPathsOnceToItsChosenPointUnderNew(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (ShipAiAuthorityOverride ignored = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.NEW);
                 GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip ship = friendly(helper, entities, SHIP);
                ShipWanderGoal goal = new ShipWanderGoal(ship, 10, 5, 0.8D);
                boolean chosen = false;
                for (int attempt = 0; attempt < 5_000 && !chosen; attempt++) chosen = goal.canUse();
                helper.assertTrue(chosen, "wander never chose a point");
                List<String> log = new ArrayList<>();
                navigation(ship).recordCalls(log);
                goal.start();
                navigation(ship).recordCalls(null);
                String expected = ship.tickCount + ":moveTo(" + fmt(field(goal, "targetX")) + ","
                        + fmt(field(goal, "targetY")) + "," + fmt(field(goal, "targetZ")) + ")x0.8=";
                helper.assertTrue(log.size() == 1 && log.get(0).startsWith(expected),
                        "expected one path to the chosen point " + expected + ", got " + log);
                helper.succeed();
            }
        }, ROUTE[0]);
    }

    // ---------- the two phases ----------

    private static void verify(GameTestHelper helper, Scenario scenario, Class<?> goalType, Check check) {
        verify(helper, scenario, goalType, Integer.MAX_VALUE, check);
    }

    /** Only what happens before tick {@code compareBefore} of the goal is compared between the authorities. */
    private static void verify(GameTestHelper helper, Scenario scenario, Class<?> goalType, int compareBefore,
                               Check check) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            int start = (int) helper.getTick() + 1;
            Outcome legacy = new Outcome();
            Outcome now = new Outcome();
            run(helper, start, ConfigHandler.ShipAiTargetAuthority.LEGACY, scenario, goalType, check, legacy);
            run(helper, start + PHASE + 1, ConfigHandler.ShipAiTargetAuthority.NEW, scenario, goalType, check, now);
            helper.runAtTickTime(start + 2 * PHASE + 2, () -> {
                String report = "\n LEGACY " + legacy + "\n NEW    " + now;
                helper.assertTrue(legacy.problems.isEmpty() && now.problems.isEmpty(), "Fixture problems:" + report);
                helper.assertTrue(before(now.calls, compareBefore).equals(before(legacy.calls, compareBefore)),
                        "Navigation calls differ:" + report);
                helper.assertTrue(before(now.goals, compareBefore).equals(before(legacy.goals, compareBefore)),
                        "Goal starts and stops differ:" + report);
                helper.assertTrue(before(now.jumps, compareBefore).equals(before(legacy.jumps, compareBefore)),
                        "Teleports differ:" + report);
                // the evidence of a passing run, for the change note and review
                LogHelper.info("Movement plan parity (" + goalType.getSimpleName() + "):" + report);
                helper.succeed();
            });
        }, ROUTE);
    }

    private static void run(GameTestHelper helper, int start, ConfigHandler.ShipAiTargetAuthority mode,
                            Scenario scenario, Class<?> goalType, Check check, Outcome result) {
        Phase[] phase = {null};
        helper.runAtTickTime(start, () -> phase[0] = Phase.open(helper, mode, scenario, goalType));
        for (int tick = start + 1; tick < start + PHASE; tick++) {
            helper.runAtTickTime(tick, () -> phase[0].guard(phase[0]::poll));
        }
        helper.runAtTickTime(start + PHASE, () -> phase[0].guard(() -> {
            phase[0].finish(check, result);
            phase[0].close();
        }));
    }

    // ---------- fixtures ----------

    private interface Scenario {
        Mob create(Phase phase);
    }

    private interface Check {
        List<String> problems(Outcome outcome);
    }

    static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities, Vec3 relative) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "failed to create ship");
        // A fresh ship has only a few HP; stray damage kills it and a low ratio makes it flee
        ship.setInvulnerable(true);
        ship.setEntitySit(false);
        ship.setStateMinor(ID.M.CraneState, 0);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PickItem, false);
        ship.setStateFlag(ID.F.PassiveAI, false);
        ship.setStateFlag(ID.F.OnSightChase, false);
        ship.setStateFlag(ID.F.UseMelee, false);
        ship.setStateFlag(ID.F.AtkType_Light, false);
        ship.setStateFlag(ID.F.AtkType_Heavy, false);
        ship.setStateMinor(ID.M.FormatType, 0);
        ship.setStateMinor(ID.M.FormatPos, 0);
        helper.assertTrue(ship.getStateMinor(ID.M.FormatType) == 0 && ship.getStateMinor(ID.M.FormatPos) == 0,
                "fixture must start outside a formation, in slot 0");
        ship.setStateMinor(ID.M.FollowMin, 1);
        ship.setStateMinor(ID.M.FollowMax, 2);
        ship.setStateMinor(ID.M.FleeHP, 0);
        ship.calcShipAttributes(31, false);
        ship.setHealth(ship.getMaxHealth());
        Vec3 at = ground(helper, relative);
        ship.moveTo(at.x, at.y, at.z, 0F, 0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "failed to add ship");
        return ship;
    }

    /** The absolute point standing on the ground at {@code relative}'s column. */
    static Vec3 ground(GameTestHelper helper, Vec3 relative) {
        Vec3 column = helper.absoluteVec(relative);
        int y = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(column.x), (int) Math.floor(column.z));
        return new Vec3(column.x, y, column.z);
    }

    static ShipPathNavigation navigation(Mob mob) {
        if (mob.getNavigation() instanceof ShipPathNavigation navigation) return navigation;
        throw new GameTestAssertException(mob + " does not use the ship navigation");
    }

    static double field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getDouble(owner);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to read " + name + ": " + error);
        }
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class Outcome {
        private List<String> calls = List.of();
        private List<String> goals = List.of();
        private List<String> jumps = List.of();
        private List<String> notes = List.of();
        private final List<String> problems = new ArrayList<>();

        @Override
        public String toString() {
            return "calls=" + this.calls + " goals=" + this.goals + " jumps=" + this.jumps
                    + " problems=" + this.problems + " notes=" + this.notes;
        }
    }

    private static final class Phase {
        private final GameTestHelper helper;
        private final ShipAiAuthorityOverride authority;
        private final GameTestEntities entities;
        private final Class<?> goalType;
        private final int teleportCooldown = ConfigHandler.shipTeleport[0];
        private final List<String> calls = new ArrayList<>();
        private final List<String> goals = new ArrayList<>();
        private final List<String> jumps = new ArrayList<>();
        private final List<BlockPos> barriers = new ArrayList<>();
        /** Why the goal stopped, as NEW decided it; not compared. */
        private final List<String> notes = new ArrayList<>();
        /** Held in place every tick, so a strike's knockback cannot make the phases diverge. */
        private final List<Entity> pinned = new ArrayList<>();
        private final List<Vec3> pins = new ArrayList<>();
        /** Gives the ship its reason to move, once its AI has started up and nothing has moved it yet. */
        private Runnable release = () -> { };
        private boolean released;
        private FakePlayer owner;
        private Mob ship;
        private WrappedGoal instance;
        private boolean running;
        private Vec3 last;
        private boolean closed;

        private Phase(GameTestHelper helper, ShipAiAuthorityOverride authority, GameTestEntities entities,
                      Class<?> goalType) {
            this.helper = helper;
            this.authority = authority;
            this.entities = entities;
            this.goalType = goalType;
        }

        static Phase open(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority mode, Scenario scenario,
                          Class<?> goalType) {
            ShipAiAuthorityOverride authority = ShipAiAuthorityOverride.use(mode);
            Phase phase = new Phase(helper, authority, GameTestEntities.open(helper), goalType);
            phase.guard(() -> {
                phase.ship = scenario.create(phase);
                phase.dropWander();
                navigation(phase.ship).recordCalls(phase.calls);
                phase.last = phase.ship.position();
            });
            return phase;
        }

        // ----- scenarios -----

        Mob follow(Vec3 ownerAt, boolean boxed, int teleportCooldown) {
            BasicEntityShip ship = this.ship(boxed, teleportCooldown);
            this.release = () -> this.owner(ownerAt, ship);
            return ship;
        }

        Mob guard(boolean boxed, int teleportCooldown) {
            BasicEntityShip ship = this.ship(boxed, teleportCooldown);
            BlockPos guardPos = BlockPos.containing(ground(this.helper, NEAR));
            this.release = () -> this.order(ship, guardPos);
            return ship;
        }

        private void order(BasicEntityShip ship, BlockPos guardPos) {
            if (ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW) {
                var id = this.helper.getLevel().dimension().location();
                ship.applyCommandState(new CommandIssuer.Player(UUID.randomUUID()),
                        new CommandStateOp.Apply(new ShipCommand.GuardPosition(
                                new DimensionKey(id.getNamespace(), id.getPath()),
                                new CommandPos(guardPos.getX(), guardPos.getY(), guardPos.getZ()), false)));
                // The order itself paths at once under NEW (outside the goals); only the goal's walk is compared.
                ship.getNavigation().stop();
            } else {
                ship.setGuardedPos(guardPos.getX(), guardPos.getY(), guardPos.getZ(),
                        this.helper.getLevel().dimension(), 1);
                ship.setStateFlag(ID.F.CanFollow, false);
            }
        }

        Mob flee() {
            BasicEntityShip ship = this.ship(false, 1_000);
            ship.setStateMinor(ID.M.FleeHP, 35);
            // wide enough that following does not take over once the flight ends beside the owner
            ship.setStateMinor(ID.M.FollowMax, 4);
            ship.setHealth(1F);
            this.release = () -> this.owner(NEAR, ship);
            return ship;
        }

        Mob pickItem() {
            BasicEntityShip ship = this.ship(false, 1_000);
            ship.setStateFlag(ID.F.PickItem, true);
            // the pick range grows with FollowMax up to half the attack range
            ship.setStateMinor(ID.M.FollowMax, 10);
            this.helper.assertTrue(ship.getCapaShipInventory().getFirstSlotForItem() >= 0,
                    "fixture must have a free inventory slot");
            this.release = this::item;
            return ship;
        }

        private void item() {
            Vec3 at = ground(this.helper, new Vec3(7.5D, 0D, 2.5D));
            ItemEntity item = this.entities.add(new ItemEntity(this.helper.getLevel(), at.x, at.y, at.z,
                    new ItemStack(Items.STICK)));
            // the ship walks up to it again and again, never picking it up
            item.setNeverPickUp();
            item.setDeltaMovement(Vec3.ZERO);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(item), "failed to add item");
        }

        Mob melee() {
            BasicEntityShip ship = this.ship(false, 1_000);
            ship.setPlayerUID(8401);
            ship.setStateFlag(ID.F.UseMelee, true);
            this.release = this::target;
            return ship;
        }

        private void target() {
            BasicEntityShipHostile target = this.entities.add(
                    ModEntities.BB_KIRISHIMA_MOB.get().create(this.helper.getLevel()));
            this.helper.assertTrue(target != null, "failed to create target");
            target.setNoAi(true);
            target.setInvulnerable(true);
            target.setPersistenceRequired();
            Vec3 at = ground(this.helper, new Vec3(10.5D, 0D, 2.5D));
            target.moveTo(at.x, at.y, at.z, 0F, 0F);
            this.helper.assertTrue(this.helper.getLevel().addFreshEntity(target), "failed to add target");
            this.pinned.add(target);
            this.pins.add(target.position());
        }

        private BasicEntityShip ship(boolean boxed, int teleportCooldown) {
            ConfigHandler.shipTeleport[0] = teleportCooldown;
            this.helper.assertTrue(!boxed || ConfigHandler.canTeleport(), "fixture needs teleporting enabled");
            BasicEntityShip ship = friendly(this.helper, this.entities, SHIP);
            if (boxed) {
                // a ring of barriers the ship cannot leave on foot
                BlockPos center = BlockPos.containing(ship.position());
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                        for (int dy = 0; dy < 3; dy++) {
                            BlockPos pos = center.offset(dx, dy, dz);
                            this.helper.getLevel().setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
                            this.barriers.add(pos);
                        }
                    }
                }
            }
            return ship;
        }

        private void owner(Vec3 relative, BasicEntityShip ship) {
            this.owner = FakePlayerFactory.get(this.helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "movement_plan_owner"));
            Vec3 at = ground(this.helper, relative);
            this.owner.moveTo(at.x, at.y, at.z, 0F, 0F);
            this.helper.getLevel().addNewPlayer(this.owner);
            ship.setOwnerUUID(this.owner.getUUID());
        }

        // ----- recording -----

        void guard(Runnable body) {
            try {
                body.run();
            } catch (Throwable error) {
                close();
                throw error;
            }
        }

        /**
         * Wander draws from the entity random, which the phases do not share. The ship registers its
         * goals again after its goals have run for the tick, so removing it here always comes first.
         */
        private void dropWander() {
            selector(this.ship).removeAllGoals(goal -> goal instanceof ShipWanderGoal);
        }

        void poll() {
            this.dropWander();
            if (!this.released && this.ship.tickCount >= RELEASE_AT) {
                this.released = true;
                this.release.run();
            }
            for (int i = 0; i < this.pinned.size(); i++) {
                Vec3 pin = this.pins.get(i);
                this.pinned.get(i).moveTo(pin.x, pin.y, pin.z);
                this.pinned.get(i).setDeltaMovement(Vec3.ZERO);
            }
            WrappedGoal goal = selector(this.ship).getAvailableGoals().stream()
                    .filter(wrapped -> this.goalType.isInstance(wrapped.getGoal()))
                    .findFirst().orElse(null);
            if (goal != this.instance) {
                // The ship registers its goals again once its AI starts up; record the goal that stays.
                this.instance = goal;
                this.running = false;
                this.goals.clear();
            }
            boolean isRunning = goal != null && goal.isRunning();
            if (isRunning != this.running) {
                this.goals.add(this.ship.tickCount + (isRunning ? ":start" : ":stop"));
                if (!isRunning) this.notes.add(this.ship.tickCount + ":" + this.why());
                this.running = isRunning;
            }
            Vec3 position = this.ship.position();
            if (position.distanceToSqr(this.last) > JUMP_SQ) {
                this.jumps.add(this.ship.tickCount + ":jump(" + fmt(position.x) + "," + fmt(position.y) + ","
                        + fmt(position.z) + ")");
            }
            this.last = position;
        }

        /** The facts the movement goals read when the watched goal stopped. */
        private String why() {
            StringBuilder out = new StringBuilder();
            out.append("hp=").append(fmt(this.ship.getHealth() / this.ship.getMaxHealth()));
            if (this.ship instanceof BasicEntityShip basic) {
                out.append(" fleeHP=").append(basic.getStateMinor(ID.M.FleeHP));
            }
            if (this.owner != null) out.append(" ownerSq=").append(fmt(this.ship.distanceToSqr(this.owner)));
            out.append(" running=").append(selector(this.ship).getRunningGoals()
                    .map(wrapped -> wrapped.getGoal().getClass().getSimpleName()).toList());
            if (ShipMovementGate.active()) out.append(" intent=").append(ShipMovementGate.intent(this.ship));
            return out.toString();
        }

        void finish(Check check, Outcome result) {
            int base = this.goals.isEmpty() ? -1
                    : Integer.parseInt(this.goals.get(0).substring(0, this.goals.get(0).indexOf(':')));
            if (base < 0) {
                result.problems.add("the " + this.goalType.getSimpleName() + " never started");
                return;
            }
            result.calls = relative(this.calls, base);
            result.goals = relative(this.goals, base);
            result.jumps = relative(this.jumps, base);
            result.notes = relative(this.notes, base);
            result.problems.addAll(check.problems(result));
            // NEW moves only through the executor, and LEGACY never touches it
            boolean executed = this.ship instanceof ShipMovementHost host
                    && host.shipMovementExecutor().last().isPresent();
            if (executed != ShipMovementGate.active()) {
                result.problems.add(executed ? "LEGACY used the movement executor" : "NEW never used the movement executor");
            }
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            try {
                if (this.ship != null) navigation(this.ship).recordCalls(null);
                this.entities.close();
                if (this.owner != null) {
                    this.helper.getLevel().removePlayerImmediately(this.owner, Entity.RemovalReason.DISCARDED);
                }
                for (BlockPos pos : this.barriers) {
                    this.helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            } finally {
                ConfigHandler.shipTeleport[0] = this.teleportCooldown;
                this.authority.close();
            }
        }
    }

    /** The entries of a goal-relative record made before {@code limit}. */
    private static List<String> before(List<String> entries, int limit) {
        return entries.stream().filter(entry -> Integer.parseInt(entry.substring(0, entry.indexOf(':'))) < limit)
                .toList();
    }

    /** Keeps entries from {@code base} on, rewritten as "tick - base:rest". */
    private static List<String> relative(List<String> entries, int base) {
        return entries.stream().filter(entry -> Integer.parseInt(entry.substring(0, entry.indexOf(':'))) >= base)
                .map(entry -> {
                    int colon = entry.indexOf(':');
                    return (Integer.parseInt(entry.substring(0, colon)) - base) + entry.substring(colon);
                }).toList();
    }

    static GoalSelector selector(Mob mob) {
        try {
            Field field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to inspect goalSelector: " + error);
        }
    }
}
