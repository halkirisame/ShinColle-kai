package com.lulan.shincolle.api.target;

import com.lulan.shincolle.api.ShinColleApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.Objects;

/**
 * Stable identifiers of the target traits ShinColle ships, and the rule that turns a trait ID into
 * the entity-type tag that assigns the trait.
 *
 * <p>A trait {@code <ns>:<path>} is assigned to an entity type by adding the type to the tag
 * {@code <ns>:shincolle_kai/target_trait/<path>}, that is the data file
 * {@code data/<ns>/tags/entity_types/shincolle_kai/target_trait/<path>.json}. Tags change with a
 * data pack reload, so an assignment can change while the game runs.</p>
 */
public final class CoreTargetTraits {

    /** Entities of this trait are valid targets for anti-air fire. */
    public static final ResourceLocation ANTI_AIR_ELIGIBLE =
            ResourceLocation.fromNamespaceAndPath(ShinColleApi.MOD_ID, "anti_air_eligible");
    /** Entities of this trait are valid targets for anti-submarine fire. */
    public static final ResourceLocation ANTI_SUBMARINE_ELIGIBLE =
            ResourceLocation.fromNamespaceAndPath(ShinColleApi.MOD_ID, "anti_submarine_eligible");

    private CoreTargetTraits() {
    }

    /** Returns the entity-type tag that assigns the trait with the given ID. */
    public static TagKey<EntityType<?>> tag(ResourceLocation traitId) {
        Objects.requireNonNull(traitId, "traitId");
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(
                traitId.getNamespace(), "shincolle_kai/target_trait/" + traitId.getPath()));
    }
}
