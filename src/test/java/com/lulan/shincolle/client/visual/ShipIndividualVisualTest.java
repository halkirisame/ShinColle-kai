package com.lulan.shincolle.client.visual;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.packs.resources.ResourceManager;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipIndividualVisualTest {
    @Test
    void textureExistenceIsReadOncePerIdUntilCacheReset() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        var delegate = resources(files());
        ResourceLocation texture = id("textures/entity/kashima.png");
        ResourceManager counted = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResource") && args[0].equals(texture)) reads.incrementAndGet();
                    return method.invoke(delegate, args);
                });
        reload(counted);
        int loaded = reads.get();
        for (int i = 0; i < 120; i++) ShipVisuals.resolve(id("kashima"), texture.toString());
        assertEquals(loaded + 1, reads.get());
        ShipVisuals.clearTextureCache();
        ShipVisuals.resolve(id("kashima"), texture.toString());
        assertEquals(loaded + 2, reads.get());
    }

    @Test
    void texturesArePerInstanceAndDoNotChangeSharedVisualOrBakedGeometry() throws IOException {
        var files = files();
        var firstTexture = id("textures/entity/first.png");
        var secondTexture = id("textures/entity/second.png");
        files.put(firstTexture, new byte[0]);
        files.put(secondTexture, new byte[0]);
        reload(resources(files));
        var base = ShipVisuals.resolve(id("kashima"));
        var first = ShipVisuals.resolve(id("kashima"), firstTexture.toString());
        var second = ShipVisuals.resolve(id("kashima"), secondTexture.toString());
        assertSame(base, first.visual());
        assertSame(base, second.visual());
        var a = new ShipVisualAnimatable();
        var b = new ShipVisualAnimatable();
        a.bind(null, first.visual(), first.texture(), true);
        b.bind(null, second.visual(), second.texture(), false);
        var modelA = new ShipVisualGeoModel(base);
        var modelB = new ShipVisualGeoModel(base);
        assertEquals(firstTexture, modelA.getTextureResource(a));
        assertEquals(secondTexture, modelB.getTextureResource(b));
        assertEquals(id("textures/entity/kashima.png"), base.definition().texture());
        assertNotSame(modelA.getBakedModel(base.definition().model()), modelB.getBakedModel(base.definition().model()));
        a.unbind();
        a.bind(null, first.visual(), first.texture(), false);
        assertEquals(firstTexture, modelA.getTextureResource(a), "recruitment removes red, not texture");
        assertEquals(base.definition().texture(), ShipVisuals.resolve(id("kashima"), "").texture());
    }

    @Test
    void missingIndividualTextureUsesWholePlaceholderAndSuccessfulReloadRechecksCachedMiss() throws IOException {
        var files = files();
        var missing = id("textures/entity/restored.png");
        reload(resources(files));
        var old = ShipVisuals.snapshot();
        assertSame(old.placeholder(), ShipVisuals.resolve(id("kashima"), missing.toString()).visual());
        assertTrue(ShipVisuals.warned("texture:" + missing));
        files.put(missing, new byte[0]);
        reload(resources(files));
        assertNotSame(old, ShipVisuals.snapshot());
        var restored = ShipVisuals.resolve(id("kashima"), missing.toString());
        assertSame(ShipVisuals.resolve(id("kashima")), restored.visual());
        assertEquals(missing, restored.texture());
        var absentVisual = ShipVisuals.resolve(id("absent"), missing.toString());
        assertSame(ShipVisuals.snapshot().placeholder(), absentVisual.visual());
        assertEquals(ShipVisuals.snapshot().placeholder().definition().texture(), absentVisual.texture());
    }

    @Test
    void failedReloadKeepsOldResourceManagerAndResolutionCacheIncludingUncachedTextures() throws IOException {
        var files = files();
        ResourceLocation texture = id("textures/entity/retained.png");
        ResourceLocation uncached = id("textures/entity/uncached.png");
        files.put(texture, new byte[0]);
        files.put(uncached, new byte[0]);
        reload(resources(files));
        var old = ShipVisuals.snapshot();
        assertEquals(texture, ShipVisuals.resolve(id("kashima"), texture.toString()).texture());
        files.remove(texture);
        files.remove(uncached);
        files.remove(id("ship_visual/placeholder.geo.json"));
        reload(resources(files));
        assertSame(old, ShipVisuals.snapshot());
        assertEquals(texture, ShipVisuals.resolve(id("kashima"), texture.toString()).texture());
        assertEquals(uncached, ShipVisuals.resolve(id("kashima"), uncached.toString()).texture());
        ShipVisuals.clearTextureCache();
        assertEquals(texture, ShipVisuals.resolve(id("kashima"), texture.toString()).texture());
        ShipAuraClient.reset();
    }
}
