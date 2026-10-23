package com.lulan.shincolle.client.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** In-memory resource manager over the real Kashima files, shared by the visual tests. */
public final class ShipVisualTestResources {
    private ShipVisualTestResources() { }

    public static ResourceLocation id(String path) {
        return new ResourceLocation("shincolle_kai", path);
    }

    public static Map<ResourceLocation, byte[]> files() throws IOException {
        Map<ResourceLocation, byte[]> files = new HashMap<>();
        Path assets = Path.of("src/main/resources/assets/shincolle_kai");
        for (String file : new String[]{"geo/kashima.geo.json", "animations/kashima.animation.json",
                "textures/entity/kashima.png", "ship_visual/placeholder.geo.json", "ship_visual/placeholder.animation.json",
                "shincolle_kai/visuals/kashima.json"}) {
            files.put(id(file), Files.readAllBytes(assets.resolve(file)));
        }
        files.put(new ResourceLocation("minecraft", "textures/block/magenta_concrete.png"), new byte[0]);
        return files;
    }

    @SuppressWarnings("unchecked")
    public static ResourceManager resources(Map<ResourceLocation, byte[]> files) {
        Map<ResourceLocation, Resource> resources = new HashMap<>();
        files.forEach((id, bytes) -> resources.put(id, new Resource(null, () -> new ByteArrayInputStream(bytes))));
        return (ResourceManager) Proxy.newProxyInstance(ShipVisualTestResources.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getResource" -> Optional.ofNullable(resources.get(args[0]));
                    case "getResourceOrThrow" -> {
                        var resource = resources.get(args[0]);
                        if (resource == null) throw new FileNotFoundException(args[0].toString());
                        yield resource;
                    }
                    case "listResources" -> {
                        Map<ResourceLocation, Resource> selected = new HashMap<>();
                        resources.forEach((id, resource) -> {
                            if (id.getPath().startsWith(args[0] + "/") && ((Predicate<ResourceLocation>) args[1]).test(id)) {
                                selected.put(id, resource);
                            }
                        });
                        yield selected;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    public static void reload(ResourceManager resources) {
        var listener = ShipVisuals.LISTENER;
        listener.apply(listener.prepare(resources, InactiveProfiler.INSTANCE), resources, InactiveProfiler.INSTANCE);
    }
}
