package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.skill.SkillKind;
import com.lulan.shincolle.ai.domain.combat.skill.SkillState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/** One entity's transient skill execution data. Neither saved nor shared between ships. */
public final class ShipSkillAttackState {
    private Optional<SkillState> sequence = Optional.empty();
    Vec3 motion = Vec3.ZERO;
    final Set<UUID> hitEntities = new HashSet<>();
    OptionalInt lastTick = OptionalInt.empty();
    Optional<ResourceKey<Level>> dimension = Optional.empty();
    boolean teleportRequested;

    public Optional<SkillState> sequence() {
        return sequence;
    }

    SkillState sequence(SkillKind kind) {
        return sequence.filter(s -> s.kind() == kind).orElseGet(() -> SkillState.idle(kind));
    }

    void setSequence(SkillState state) {
        sequence = Optional.of(state);
    }

    void clear() {
        sequence = Optional.empty();
        motion = Vec3.ZERO;
        hitEntities.clear();
        lastTick = OptionalInt.empty();
        dimension = Optional.empty();
        teleportRequested = false;
    }
}
