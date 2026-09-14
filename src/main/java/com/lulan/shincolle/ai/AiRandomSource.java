package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.AiRandom;
import com.lulan.shincolle.ai.domain.AiRandomStream;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Stateless server-only binding of decision streams to Minecraft random sources. */
public final class AiRandomSource {

    private AiRandomSource() {
    }

    /** Acquire for the current tick; do not retain across level changes. */
    public static AiRandom forEntity(Entity entity, AiRandomStream stream) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(stream, "stream");
        if (entity.level().isClientSide) {
            throw new IllegalArgumentException("AI random sources require a server level");
        }
        RandomSource source = switch (stream) {
            case TARGET_SELECTION -> entity.level().random;
        };
        return AiRandom.checked(source::nextInt);
    }
}
