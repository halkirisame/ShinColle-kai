package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.ObservationPosition;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.SpatialCandidateProfiler;
import com.lulan.shincolle.ai.domain.SpatialCandidateProvider;
import com.lulan.shincolle.ai.domain.SpatialQuery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Server-side Minecraft implementation of the combat-target spatial query boundary. */
public final class MinecraftSpatialCandidateProvider implements SpatialCandidateProvider {

    private final ServerLevel level;
    private final DimensionKey dimension;
    private final SpatialCandidateProfiler profiler;

    public MinecraftSpatialCandidateProvider(ServerLevel level) {
        this(level, SpatialCandidateProfiler.NONE);
    }

    public MinecraftSpatialCandidateProvider(ServerLevel level, SpatialCandidateProfiler profiler) {
        this.level = Objects.requireNonNull(level, "level");
        ResourceLocation location = level.dimension().location();
        this.dimension = new DimensionKey(location.getNamespace(), location.getPath());
        this.profiler = Objects.requireNonNull(profiler, "profiler");
    }

    @Override
    public List<RawEntityObservation> query(SpatialQuery query) {
        Objects.requireNonNull(query, "query");
        if (!this.dimension.equals(query.source().dimension())) {
            throw new IllegalArgumentException("Spatial query source dimension does not match provider level");
        }
        ObservationPosition center = query.center();
        AABB bounds = new AABB(
                center.x() - query.horizontalRange(),
                center.y() - query.verticalRange(),
                center.z() - query.horizontalRange(),
                center.x() + query.horizontalRange(),
                center.y() + query.verticalRange(),
                center.z() + query.horizontalRange());
        List<Entity> entities = this.level.getEntities((Entity) null, bounds, entity ->
                !query.source().uuid().equals(entity.getUUID())
                        && !entity.isSpectator()
                        && !entity.isRemoved()
                        && entity.isAlive());
        this.profiler.recordQuery(entities.size());
        List<RawEntityObservation> observations = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            observations.add(MinecraftEntityObservationAdapter.observe(entity));
        }
        return List.copyOf(observations);
    }
}
