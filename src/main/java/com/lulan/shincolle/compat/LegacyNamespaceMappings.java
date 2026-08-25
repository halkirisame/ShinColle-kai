package com.lulan.shincolle.compat;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * Keeps worlds created before the {@code shincolle} -> {@code shincolle_kai} rename loadable.
 * <p>
 * Without this, Forge stops on its "missing registry entries" prompt. Every stale entry is
 * ignored so the world opens instead; its ShinColle content is simply absent.
 * <p>
 * <b>Nothing here recovers world content, and nothing can.</b> {@code remap} is deliberately
 * not used, for two independent reasons:
 * <ol>
 *   <li>{@code remap} only records an alias on {@link net.minecraftforge.registries.ForgeRegistry},
 *       which keeps its own storage. Minecraft resolves saved items and entities through
 *       {@code BuiltInRegistries} ({@code ItemStack:167}, {@code EntityType:324}), and
 *       {@code MappedRegistry.get} is a plain map lookup that never consults that alias table.</li>
 *   <li>The registry reachable from a {@code Mapping} is the world-side staging registry, not
 *       the live one, so looking a counterpart up in it misses every time anyway.</li>
 * </ol>
 * A real-world load on 2026-08-25 went through all four affected registries — 87 entity types,
 * 66 items, 65 sound events, 17 blocks — and the world opened cleanly with the content gone.
 * See {@code docs/specs/mod_identity_rename_2026-08-25.md} section 3.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyNamespaceMappings {

    /** Namespace used up to and including the v1.20.1.0.8.x releases. */
    private static final String LEGACY_NAMESPACE = "shincolle";

    private LegacyNamespaceMappings() {
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onMissingMappings(MissingMappingsEvent event) {
        // getKey() is wildcard-typed; the element type is irrelevant because nothing is read
        // out of the registry here — every stale mapping is only ever ignored.
        ignoreLegacy(event, (ResourceKey<Registry<Object>>) (ResourceKey<?>) event.getKey());
    }

    private static <T> void ignoreLegacy(MissingMappingsEvent event, ResourceKey<? extends Registry<T>> key) {
        int ignored = 0;

        for (MissingMappingsEvent.Mapping<T> mapping : event.getMappings(key, LEGACY_NAMESPACE)) {
            mapping.ignore();
            ignored++;
        }

        if (ignored > 0) {
            LogHelper.info("Dropped " + ignored + " legacy '" + LEGACY_NAMESPACE + "' entries from "
                    + key.location() + ". Content saved under the old namespace is not recoverable."
                    + " The world itself stays playable.");
        }
    }
}
