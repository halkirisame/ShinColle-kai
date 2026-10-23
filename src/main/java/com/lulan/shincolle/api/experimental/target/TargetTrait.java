package com.lulan.shincolle.api.experimental.target;

import com.lulan.shincolle.api.target.CoreTargetTraits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.Objects;

/**
 * A combat-target trait. Experimental: ShinColle reads only its own two traits for now.
 *
 * <p>The element knows its own ID, which must equal the ID it is registered under. The entity types
 * that carry the trait are the members of {@link #tag()}.</p>
 */
public final class TargetTrait {

    private final ResourceLocation id;
    private final TagKey<EntityType<?>> tag;

    public TargetTrait(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
        this.tag = CoreTargetTraits.tag(id);
    }

    public ResourceLocation id() {
        return this.id;
    }

    /** The entity-type tag whose members carry this trait. */
    public TagKey<EntityType<?>> tag() {
        return this.tag;
    }
}
