package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.CurrentTargetRawObservation;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.EntityTypeKey;
import com.lulan.shincolle.ai.domain.ObservationPosition;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.RelationIdentity;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.utility.TeamHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public final class MinecraftEntityObservationAdapter {
    private MinecraftEntityObservationAdapter() {
    }

    public static RawEntityObservation observe(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity.level().isClientSide()) {
            throw new IllegalArgumentException("AI observations must be captured on the server");
        }
        ResourceLocation dimension = entity.level().dimension().location();
        ResourceLocation entityType = Objects.requireNonNull(
                ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), "entityType");
        return new RawEntityObservation(
                new TargetHandle(entity.getUUID(), new DimensionKey(
                        dimension.getNamespace(), dimension.getPath())),
                new EntityTypeKey(entityType.getNamespace(), entityType.getPath()),
                RelationIdentity.fromLegacyPlayerUid(TeamHelper.getPlayerUID(entity)),
                new ObservationPosition(entity.getX(), entity.getY(), entity.getZ()),
                entity.isAlive());
    }

    public static CurrentTargetRawObservation observeCurrent(Entity target, long observedAtTick) {
        return target == null
                ? CurrentTargetRawObservation.absent(observedAtTick)
                : CurrentTargetRawObservation.observed(observe(target), observedAtTick);
    }
}
