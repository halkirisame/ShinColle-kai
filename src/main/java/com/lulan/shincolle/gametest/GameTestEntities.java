package com.lulan.shincolle.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tracks entities created by a synchronous GameTest and removes them on exit. */
public final class GameTestEntities implements AutoCloseable {

    private final List<Entity> entities = new ArrayList<>();

    private GameTestEntities() {
    }

    public static GameTestEntities open(GameTestHelper helper) {
        Objects.requireNonNull(helper, "helper");
        return new GameTestEntities();
    }

    public <T extends Entity> T add(T entity) {
        this.entities.add(entity);
        return entity;
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
