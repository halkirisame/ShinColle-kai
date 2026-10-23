package com.lulan.shincolle.shipdata;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Bounded JSON reload. Bad files are isolated; a failed reload never publishes a partial candidate. */
public final class ShipDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ShipDefinitionValidator validator;
    private final ShipDefinitionRegistry registry;

    public ShipDefinitionLoader() {
        this(new ShipDefinitionValidator(), ShipDefinitionRegistry.server());
    }

    ShipDefinitionLoader(ShipDefinitionValidator validator, ShipDefinitionRegistry registry) {
        super(new Gson(), ShipDefinitionFormat.DIRECTORY);
        this.validator = validator;
        this.registry = registry;
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> prepared = new HashMap<>();
        FileToIdConverter converter = FileToIdConverter.json(ShipDefinitionFormat.DIRECTORY);
        // The resource manager supplies the top pack's file for each ID, as for other datapack definitions.
        converter.listMatchingResources(manager).forEach((file, resource) -> {
            ResourceLocation id = converter.fileToId(file);
            try (var input = resource.open()) {
                ShipDefinitionFormat.requireId(id.toString());
                byte[] bytes = input.readNBytes(ShipDefinitionFormat.MAX_JSON_BYTES + 1);
                prepared.put(id, ShipDefinitionJson.read(bytes));
            } catch (IOException | IllegalArgumentException exception) {
                LOGGER.warn("Rejected ship definition {}: {}", id, exception.getMessage());
            }
        });
        return prepared;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        try {
            registry.reload(() -> parseDefinitions(prepared));
            LOGGER.info("Loaded {} ship definitions (generation {})", registry.all().size(), registry.generation());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed ship definition reload; keeping previous snapshot", exception);
        }
    }

    Map<ShipDefinitionId, ShipDefinition> parseDefinitions(Map<ResourceLocation, JsonElement> prepared) {
        Map<ShipDefinitionId, ShipDefinition> candidate = new HashMap<>();
        prepared.forEach((location, json) -> {
            try {
                ShipDefinitionId id = new ShipDefinitionId(location);
                ShipDefinition definition = validator.validate(id, json,
                        warning -> LOGGER.warn("Ship definition {}: {}", id, warning));
                candidate.put(id, definition);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Rejected ship definition {}: {}", location, exception.getMessage());
            }
        });
        return Map.copyOf(candidate);
    }
}
