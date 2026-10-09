package com.lulan.shincolle.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks entities created by a synchronous GameTest and removes them on exit. */
public final class GameTestEntities implements AutoCloseable {

    /** Each test's ticket has its own holder, so one test's release leaves another's ticket. */
    private static final TicketType<Integer> FIXTURE_TICKET =
            TicketType.create("shincolle_kai_gametest_fixture", Integer::compare);
    /** Level 31, as a forced chunk: entities tick. */
    private static final int ENTITY_TICKING_DISTANCE = 2;
    /** An entity-ticking chunk needs the chunks within this distance full. */
    private static final int FULL_NEIGHBOURS = 2;
    private static final AtomicInteger NEXT_HOLDER = new AtomicInteger();

    private final List<Entity> entities = new ArrayList<>();

    private GameTestEntities() {
    }

    public static GameTestEntities open(GameTestHelper helper) {
        Objects.requireNonNull(helper, "helper");
        GameTestEntities entities = new GameTestEntities();
        onCompletion(helper, entities::close, "entity cleanup");
        return entities;
    }

    private static void onCompletion(GameTestHelper helper, Runnable action, String what) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            GameTestInfo test = (GameTestInfo) field.get(helper);
            test.addListener(new GameTestListener() {
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
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot attach " + what + " to GameTest completion", error);
        }
    }

    public <T extends Entity> T add(T entity) {
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        this.entities.add(entity);
        return entity;
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
        }).thenExecute(verification);
    }

    public static void assertRegistered(GameTestHelper helper, Entity entity) {
        helper.assertTrue(helper.getLevel().getEntity(entity.getUUID()) == entity,
                "Fixture was added but is not indexed by UUID: " + entity.getType()
                        + " at " + entity.position() + " in " + entity.chunkPosition());
    }

    @Override
    public void close() {
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
