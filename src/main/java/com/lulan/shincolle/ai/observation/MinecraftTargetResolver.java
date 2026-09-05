package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetResolver;
import com.lulan.shincolle.ai.domain.ValidatingTargetResolver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/** Resolves loaded, live server entities from stable combat-target handles. */
public final class MinecraftTargetResolver implements TargetResolver<Entity> {

    private final ValidatingTargetResolver<Entity> resolver;

    public MinecraftTargetResolver(ServerLevel level) {
        ServerLevel checkedLevel = Objects.requireNonNull(level, "level");
        ResourceLocation location = checkedLevel.dimension().location();
        DimensionKey dimension = new DimensionKey(location.getNamespace(), location.getPath());
        this.resolver = new ValidatingTargetResolver<>(
                dimension,
                uuid -> Optional.ofNullable(checkedLevel.getEntity(uuid)),
                Entity::getUUID,
                entity -> !entity.isRemoved() && entity.isAlive());
    }

    @Override
    public Optional<Entity> resolve(TargetHandle handle) {
        return this.resolver.resolve(handle);
    }
}
