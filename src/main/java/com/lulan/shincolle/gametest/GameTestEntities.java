package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.GoalSelectorHelper;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.reference.ID;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tracks the entities a GameTest creates, fails the test when one leaves the chunks the test itself keeps
 * ticking, and removes them on exit.
 */
public final class GameTestEntities implements AutoCloseable {

    /** Each test's ticket has its own holder, so one test's release leaves another's ticket. */
    private static final TicketType<Integer> FIXTURE_TICKET =
            TicketType.create("shincolle_kai_gametest_fixture", Integer::compare);
    /** Level 31, as a forced chunk: entities tick. */
    private static final int ENTITY_TICKING_DISTANCE = 2;
    /** An entity-ticking chunk needs the chunks within this distance full. */
    private static final int FULL_NEIGHBOURS = 2;
    private static final AtomicInteger NEXT_HOLDER = new AtomicInteger();
    /**
     * A test's structure force-loads the chunks from one before to three after the chunk of its corner, on both
     * horizontal axes. Wherever the corner falls inside its chunk, these relative columns lie in those chunks.
     */
    private static final double AREA_FROM = -16D;
    private static final double AREA_BEFORE = 49D;
    /** The world spawn's START ticket, level 22 at the spawn chunk, keeps entities ticking out to level 31. */
    private static final int SPAWN_TICKING_RADIUS = 9;
    /** Chunks each running test holds through {@link #whenPositionsTicking}. Server thread only. */
    private static final Map<GameTestInfo, Set<ChunkPos>> HELD = new HashMap<>();

    private final GameTestHelper helper;
    private final GameTestInfo test;
    private final List<Entity> entities = new ArrayList<>();
    private final Set<Entity> travellers = new HashSet<>();
    /** Ships whose running dry is under test. */
    private final Set<Entity> fuelless = new HashSet<>();
    /** Ships whose being hurt is under test. */
    private final Set<Entity> damageable = new HashSet<>();
    /** The first hit a tracked ship outside {@link #damageable} took, as {@link #recordHit} explains. */
    private String strayHit;
    /** Ships, hostile ships and mounts whose noAi differed from the stop flag, with their tick count then. */
    private final Map<Entity, Integer> noAiWrites = new HashMap<>();

    private GameTestEntities(GameTestHelper helper, GameTestInfo test) {
        this.helper = helper;
        this.test = test;
    }

    /**
     * Opens a fixture whose entities must stay where this test or the world keeps entities ticking: within
     * {@link #AREA_FROM} and {@link #AREA_BEFORE} of the structure's corner on x and z, in a chunk this test
     * holds through {@link #whenPositionsTicking}, or in the world spawn's chunks. Elsewhere an entity ticks only
     * while another test's chunks happen to cover it, and freezes when the tests before it move. Players are
     * exempt: a player keeps its own chunks loaded. The fixture also fails when a ship, hostile ship or mount drops
     * a noAi the test set, as {@link #noAiDroppedByOwnTick()} explains, when a ship runs out of fuel, as
     * {@link #outOfFuel()} explains, and when a ship is hurt, as {@link #recordHit} explains. The checks run at the
     * end of every server tick and on {@link #close()}.
     */
    public static GameTestEntities open(GameTestHelper helper) {
        Objects.requireNonNull(helper, "helper");
        GameTestInfo test = testInfo(helper, "the fixture area check");
        GameTestEntities entities = new GameTestEntities(helper, test);
        Consumer<TickEvent.ServerTickEvent> watch = event -> {
            if (event.phase == TickEvent.Phase.END && !test.isDone()) {
                entities.problem().ifPresent(problem -> failBetweenTicks(test, problem));
            }
        };
        Consumer<LivingHurtEvent> hits = entities::recordHit;
        MinecraftForge.EVENT_BUS.addListener(watch);
        MinecraftForge.EVENT_BUS.addListener(hits);
        onCompletion(helper, () -> {
            MinecraftForge.EVENT_BUS.unregister(watch);
            MinecraftForge.EVENT_BUS.unregister(hits);
            entities.discard();
        }, "entity cleanup");
        return entities;
    }

    /**
     * {@link GameTestInfo#tick()} tells the listeners only about a test that finishes inside it, so a failure found
     * between the test's ticks tells them here, once, as the tick would have.
     */
    private static void failBetweenTicks(GameTestInfo test, String problem) {
        test.fail(new GameTestAssertException(problem));
        try {
            Field field = GameTestInfo.class.getDeclaredField("listeners");
            field.setAccessible(true);
            for (Object listener : List.copyOf((List<?>) field.get(test))) {
                ((GameTestListener) listener).testFailed(test);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot report a fixture failure to the GameTest listeners", error);
        }
    }

    private Optional<String> outsideArea() {
        BlockPos corner = this.helper.absolutePos(BlockPos.ZERO);
        Set<ChunkPos> held = HELD.getOrDefault(this.test, Set.of());
        ChunkPos spawn = new ChunkPos(this.helper.getLevel().getSharedSpawnPos());
        for (Entity entity : this.entities) {
            if (entity == null || entity.isRemoved() || entity instanceof Player || this.travellers.contains(entity)
                    || this.helper.getLevel().getEntity(entity.getId()) != entity) {
                continue;
            }
            double x = entity.getX() - corner.getX();
            double z = entity.getZ() - corner.getZ();
            if (x >= AREA_FROM && x < AREA_BEFORE && z >= AREA_FROM && z < AREA_BEFORE
                    || held.contains(entity.chunkPosition())
                    || entity.chunkPosition().getChessboardDistance(spawn) <= SPAWN_TICKING_RADIUS) {
                continue;
            }
            return Optional.of(String.format("Fixture %s is at relative (%.2f, %.2f, %.2f), outside the chunks "
                            + "this test or the world spawn keeps ticking, so only another test's chunks can tick it. "
                            + "Pass the positions it passes through to whenPositionsTicking, or allowTravel it if its "
                            + "travel is under test",
                    EntityType.getKey(entity.getType()), x, entity.getY() - corner.getY(), z));
        }
        return Optional.empty();
    }

    /**
     * Ships, hostile ships and mounts set noAi back to {@link BasicEntityShip#stopAI} at the start of their own
     * tick, before any of their goals run. A value that differs at the end of a server tick was written after the
     * entity's tick, by the test, and is dropped by its next tick: it stops none of the goals it was meant to stop.
     */
    private Optional<String> noAiDroppedByOwnTick() {
        for (Entity entity : this.entities) {
            if (!(entity instanceof BasicEntityShip || entity instanceof BasicEntityShipHostile
                    || entity instanceof BasicEntityMount) || entity.isRemoved()
                    || this.helper.getLevel().getEntity(entity.getId()) != entity) {
                continue;
            }
            Integer written = this.noAiWrites.get(entity);
            if (written != null && entity.tickCount != written) {
                return Optional.of(String.format("Fixture %s had noAi %s, unlike the stop flag, at its tick %d, and "
                                + "its own tick set noAi back before running its goals, so the setting stopped none "
                                + "of them. Remove its goals with removeGoalsEachTick or removeAllGoalsEachTick",
                        EntityType.getKey(entity.getType()), !BasicEntityShip.stopAI, written));
            }
            if (((Mob) entity).isNoAi() != BasicEntityShip.stopAI) {
                this.noAiWrites.put(entity, entity.tickCount);
            } else {
                this.noAiWrites.remove(entity);
            }
        }
        return Optional.empty();
    }

    /**
     * A ship spawns with no grudge and runs dry when its AI starts at tick 16. Out of fuel, the action gate stops
     * all of its goals, so it only stands: a test that expects it to act fails for the wrong reason, and one that
     * expects it not to act passes whatever the code under test does. A dying ship reports no fuel whatever its
     * grudge, so it is left out.
     */
    private Optional<String> outOfFuel() {
        for (Entity entity : this.entities) {
            if (!(entity instanceof BasicEntityShip ship) || this.fuelless.contains(ship) || ship.isRemoved()
                    || this.helper.getLevel().getEntity(ship.getId()) != ship || ship.isDeadOrDying()
                    || ship.deathTime > 0 || !ship.getStateFlag(ID.F.NoFuel)) {
                continue;
            }
            return Optional.of(String.format("Fixture %s ran out of fuel at its tick %d with grudge %d, so the action "
                            + "gate stops all of its goals. Give it grudge with setStateMinor(ID.M.NumGrudge, ...), or "
                            + "allowOutOfFuel it if running dry is under test",
                    EntityType.getKey(ship.getType()), ship.tickCount, ship.getStateMinor(ID.M.NumGrudge)));
        }
        return Optional.empty();
    }

    /**
     * A hit stands a sitting ship up, gives it a revenge target and lowers its morale, and a fresh ship has only a
     * few health, so a stray hit kills it. Either way the fixture is no longer what the test set up: a test that
     * expects it to act fails for the wrong reason, and one that expects it not to act passes whatever the code
     * under test does. A kill is always the test's own, so it is left out.
     */
    private void recordHit(LivingHurtEvent event) {
        if (this.strayHit != null || !(event.getEntity() instanceof BasicEntityShip ship)
                || !this.entities.contains(ship) || this.damageable.contains(ship)
                || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        this.strayHit = String.format("Fixture %s took %.1f %s damage from %s at its tick %d with health %.1f of "
                        + "%.1f. A hit stands a sitting ship up, gives it a revenge target and lowers its morale, and "
                        + "a fresh ship has only a few health. Make it setInvulnerable(true), or allowDamage it if its "
                        + "being hurt is under test",
                EntityType.getKey(ship.getType()), event.getAmount(), event.getSource().getMsgId(),
                attacker == null ? "no attacker" : EntityType.getKey(attacker.getType()), ship.tickCount,
                ship.getHealth(), ship.getMaxHealth());
    }

    private Optional<String> problem() {
        return this.outsideArea().or(this::noAiDroppedByOwnTick).or(this::outOfFuel)
                .or(() -> Optional.ofNullable(this.strayHit));
    }

    /**
     * Ships and mounts set noAi back from the global stop flag every tick, so {@code setNoAi(true)} leaves their
     * goals running. This removes the goals {@code which} matches, both goals and target goals, at the start of
     * each of the mob's ticks until the test ends; a ship registers its goals again at tick 16. It listens to the
     * mob's own tick, because a test's scheduled actions cannot be added while the test runs one of them.
     */
    public static void removeGoalsEachTick(GameTestHelper helper, Mob mob, Predicate<Goal> which) {
        GoalSelector goals = selector(mob, "goalSelector");
        GoalSelector targets = selector(mob, "targetSelector");
        Runnable remove = () -> {
            GoalSelectorHelper.stopAndRemove(goals, which);
            GoalSelectorHelper.stopAndRemove(targets, which);
        };
        Consumer<LivingEvent.LivingTickEvent> listener = event -> {
            if (event.getEntity() == mob) remove.run();
        };
        remove.run();
        MinecraftForge.EVENT_BUS.addListener(listener);
        onCompletion(helper, () -> MinecraftForge.EVENT_BUS.unregister(listener), "goal removal listener");
    }

    /** As {@link #removeGoalsEachTick} for every goal: a mob that only stands where the test put it. */
    public static void removeAllGoalsEachTick(GameTestHelper helper, Mob mob) {
        removeGoalsEachTick(helper, mob, goal -> true);
    }

    private static GoalSelector selector(Mob mob, String name) {
        try {
            Field field = Mob.class.getDeclaredField(name);
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Failed to inspect " + name + ": " + error);
        }
    }

    private static GameTestInfo testInfo(GameTestHelper helper, String what) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            return (GameTestInfo) field.get(helper);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot attach " + what + " to its GameTest", error);
        }
    }

    static void onCompletion(GameTestHelper helper, Runnable action, String what) {
        testInfo(helper, what).addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info) {
                action.run();
            }

            @Override
            public void testFailed(GameTestInfo info) {
                action.run();
            }
        });
    }

    GameTestHelper helper() {
        return this.helper;
    }

    public <T extends Entity> T add(T entity) {
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        this.entities.add(entity);
        return entity;
    }

    /**
     * Tracks {@code entity} as {@link #add} does, but leaves it out of the area check: an entity whose travel or
     * chunk loading is the behavior under test, or one the code under test launched.
     */
    public <T extends Entity> T allowTravel(T entity) {
        if (!this.entities.contains(entity)) {
            this.add(entity);
        }
        this.travellers.add(entity);
        return entity;
    }

    /**
     * Tracks {@code ship} as {@link #add} does, but leaves it out of the fuel check: its running dry is under test,
     * or the test needs none of its goals, as for a target or a ship the test drives by hand.
     */
    public <T extends BasicEntityShip> T allowOutOfFuel(T ship) {
        if (!this.entities.contains(ship)) {
            this.add(ship);
        }
        this.fuelless.add(ship);
        return ship;
    }

    /**
     * Tracks {@code ship} as {@link #add} does, but leaves it out of the hit check: its being hurt is under test, or
     * it is a target the test shoots.
     */
    public <T extends BasicEntityShip> T allowDamage(T ship) {
        if (!this.entities.contains(ship)) {
            this.add(ship);
        }
        this.damageable.add(ship);
        return ship;
    }

    /**
     * Start a synchronous fixture only after its entity sections can index new entities. A test's
     * structure force-loads only its own chunks, and where a test lands depends on every test placed
     * before it, so each fixture chunk is held entity-ticking by this test's own ticket until it ends.
     */
    public static void whenPositionsTicking(GameTestHelper helper, Runnable verification, Vec3... relativePositions) {
        ServerChunkCache chunks = helper.getLevel().getChunkSource();
        Integer holder = NEXT_HOLDER.getAndIncrement();
        Set<ChunkPos> held = new LinkedHashSet<>();
        for (Vec3 relative : relativePositions) {
            held.add(new ChunkPos(BlockPos.containing(helper.absoluteVec(relative))));
        }
        GameTestInfo test = testInfo(helper, "the held fixture chunks");
        HELD.computeIfAbsent(test, key -> new HashSet<>()).addAll(held);
        for (ChunkPos chunk : held) {
            chunks.addRegionTicket(FIXTURE_TICKET, chunk, ENTITY_TICKING_DISTANCE, holder);
        }
        // Entities tick in a chunk only once the chunks within 2 of it are full. They generate off
        // the server thread, and the test ticks run far faster than that, so load them here.
        for (ChunkPos chunk : held) {
            for (int dx = -FULL_NEIGHBOURS; dx <= FULL_NEIGHBOURS; dx++) {
                for (int dz = -FULL_NEIGHBOURS; dz <= FULL_NEIGHBOURS; dz++) {
                    helper.getLevel().getChunk(chunk.x + dx, chunk.z + dz);
                }
            }
        }
        onCompletion(helper, () -> {
            HELD.remove(test);
            for (ChunkPos chunk : held) {
                chunks.removeRegionTicket(FIXTURE_TICKET, chunk, ENTITY_TICKING_DISTANCE, holder);
            }
        }, "fixture chunk release");
        helper.startSequence().thenWaitUntil(() -> {
            for (Vec3 relative : relativePositions) {
                Vec3 position = helper.absoluteVec(relative);
                helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(position)),
                        "Waiting for fixture entity section at " + position);
            }
        }).thenExecute(GameTestVerification.namedFailure(verification));
    }

    public static void assertRegistered(GameTestHelper helper, Entity entity) {
        helper.assertTrue(helper.getLevel().getEntity(entity.getUUID()) == entity,
                "Fixture was added but is not indexed by UUID: " + entity.getType()
                        + " at " + entity.position() + " in " + entity.chunkPosition());
    }

    /**
     * Checks where the entities ended, whether one dropped a noAi since the last server tick, whether a ship ran
     * out of fuel and whether a ship was hurt, for a test that finishes before the next one, then removes them.
     * Once the test has failed, as when its failure listener closes the fixture, it only removes them: a failure
     * thrown from a listener crashes the server.
     */
    @Override
    public void close() {
        Optional<String> problem = this.test.hasFailed() ? Optional.empty() : this.problem();
        this.discard();
        problem.ifPresent(message -> {
            throw new GameTestAssertException(message);
        });
    }

    private void discard() {
        for (int i = this.entities.size() - 1; i >= 0; i--) {
            Entity entity = this.entities.get(i);
            try {
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            } catch (Throwable ignored) {
                // Cleanup must not change the GameTest result or skip later entities.
            }
        }
    }
}
