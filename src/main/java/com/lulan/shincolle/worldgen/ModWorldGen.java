package com.lulan.shincolle.worldgen;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Data-driven world generation with live configuration for counts and gravel targets.
 * <p>
 * Features and biome modifiers are defined in JSON files.
 * The actual ore and feature generation is defined by the following data pack
 * files:
 *
 * <h3>Polymetallic Ore (all overworld biomes)</h3>
 * <ul>
 * <li>ConfiguredFeature:
 * {@code data/shincolle/worldgen/configured_feature/polymetal_ore.json}</li>
 * <li>PlacedFeature:
 * {@code data/shincolle/worldgen/placed_feature/polymetal_ore.json}</li>
 * <li>BiomeModifier:
 * {@code data/shincolle/forge/biome_modifier/add_polymetal_ore.json}</li>
 * </ul>
 *
 * <h3>Polymetallic Gravel (ocean biomes only)</h3>
 * <ul>
 * <li>ConfiguredFeature:
 * {@code data/shincolle/worldgen/configured_feature/polymetal_gravel.json}</li>
 * <li>PlacedFeature:
 * {@code data/shincolle/worldgen/placed_feature/polymetal_gravel.json}</li>
 * <li>BiomeModifier:
 * {@code data/shincolle/forge/biome_modifier/add_polymetal_gravel.json}</li>
 * </ul>
 * <p>
 * Custom placement and predicate codecs read common settings at generation time.
 */
public class ModWorldGen {

    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Reference.MOD_ID);
    private static final DeferredRegister<BlockPredicateType<?>> PREDICATES =
            DeferredRegister.create(Registries.BLOCK_PREDICATE_TYPE, Reference.MOD_ID);

    public static final RegistryObject<PlacementModifierType<ConfigCountPlacement>> CONFIG_COUNT =
            PLACEMENTS.register("config_count", () -> () -> ConfigCountPlacement.CODEC);
    public static final RegistryObject<BlockPredicateType<ConfigGravelPredicate>> CONFIG_GRAVEL_TARGET =
            PREDICATES.register("config_gravel_target", () -> () -> ConfigGravelPredicate.CODEC);

    public static void register(IEventBus eventBus) {
        PLACEMENTS.register(eventBus);
        PREDICATES.register(eventBus);
    }

    /**
     * Initialization hook called during mod common setup.
     * Logs confirmation that the data-driven world generation files are expected to
     * be loaded.
     */
    public static void init() {
        ShinColle.LOGGER.info("ShinColle: World generation configured.");
        ShinColle.LOGGER.info(
                "ShinColle:   - Polymetallic Ore: overworld, Y -61 to 50, trapezoid distribution, vein size 7, configurable count");
        ShinColle.LOGGER
                .info("ShinColle:   - Polymetallic Gravel: ocean floors, Y -64 to 55, disk radius 2-3, configurable count and targets");
    }
}
