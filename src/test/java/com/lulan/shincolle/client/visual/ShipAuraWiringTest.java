package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.visualdata.ShipAuraBudget;
import com.lulan.shincolle.visualdata.ShipAuraSelection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipAuraWiringTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        SharedConstants.tryDetectVersion();
        var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        if (bootstrapped.getBoolean(null)) return;
        bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
    }

    @Test
    void dedicatedParticleDiesOnTick20AndDoesNotNeedCollisionWorld() {
        var particle = new ShipAuraParticle(null, 0, 0, 0, null, 0);
        assertSame(ParticleRenderType.TERRAIN_SHEET, particle.getRenderType());
        assertEquals(20, particle.getLifetime());
        for (int i = 0; i < 19; i++) particle.tick();
        assertTrue(particle.isAlive());
        particle.tick();
        assertFalse(particle.isAlive());
    }

    @Test
    void queuedOrEvictedParticlesStillExpireWithin20ClientTicks() {
        var particle = new ShipAuraParticle(null, 0, 0, 0, null, 100);
        assertFalse(particle.expire(119));
        assertTrue(particle.expire(120));
        assertFalse(particle.isAlive());
    }

    @Test
    @SuppressWarnings("unchecked")
    void realRedLayerDispatchesExactlyOneRerenderWithNoDamageOverlayAndDoesNotCallLayers() throws Exception {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var model = new ShipVisualGeoModel(visual);
        var baked = model.getBakedModel(visual.definition().model());
        var entity = new ShipVisualAnimatable();
        List<Object[]> passes = new ArrayList<>();
        GeoRenderer<ShipVisualAnimatable> renderer = (GeoRenderer<ShipVisualAnimatable>) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{GeoRenderer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getTextureLocation")) return model.getTextureResource(entity);
                    if (method.getName().equals("reRender")) { passes.add(args); return null; }
                    throw new AssertionError("Unexpected layer dispatch: " + method.getName());
                });
        MultiBufferSource buffers = type -> null;
        var layer = new ShipAuraLayer(renderer);
        entity.bind(null, visual, visual.definition().texture(), false);
        layer.render(new PoseStack(), entity, baked, null, buffers, null, 0.5F, 77, 123);
        assertTrue(passes.isEmpty());
        entity.bind(null, visual, visual.definition().texture(), true);
        layer.render(new PoseStack(), entity, baked, null, buffers, null, 0.5F, 77, 123);
        assertEquals(1, passes.size());
        Object[] args = passes.get(0);
        assertSame(baked, args[0]);
        assertSame(RenderType.entityTranslucent(visual.definition().texture(), false), args[4]);
        assertEquals(77, args[7]);
        assertEquals(OverlayTexture.NO_OVERLAY, args[8]);
        assertEquals(List.of(1F, 0.15F, 0.15F, 0.25F), List.of(args[9], args[10], args[11], args[12]));
        entity.unbind();
        assertFalse(entity.redAura());
    }

    private static Object state(String name) throws Exception {
        var field = ShipAuraClient.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulReloadAndLogoutDisposeProductionSelectionBudgetAndDedicatedParticlesButFailureDoesNot() throws Exception {
        var files = files();
        reload(resources(files));
        var selection = (ShipAuraSelection) state("SELECTION");
        var budget = (ShipAuraBudget) state("BUDGET");
        var particles = (List<ShipAuraParticle>) state("PARTICLES");
        selection.begin(100, List.of(new ShipAuraSelection.Candidate(1, 0, true, true, false, false, true, true)));
        selection.complete(100);
        assertTrue(budget.emit(10, 1, 0));
        var particle = new ShipAuraParticle(null, 0, 0, 0, null, 0);
        particles.add(particle);
        var bad = files();
        bad.remove(id("ship_visual/placeholder.geo.json"));
        reload(resources(bad));
        assertEquals(List.of(1), selection.completed());
        assertFalse(budget.emit(10, 1, 0));
        assertTrue(particle.isAlive());
        reload(resources(files));
        assertTrue(selection.completed().isEmpty());
        assertTrue(budget.emit(10, 1, 0));
        assertFalse(particle.isAlive());
        assertTrue(particles.isEmpty());
        particles.add(new ShipAuraParticle(null, 0, 0, 0, null, 0));
        selection.begin(101, List.of(new ShipAuraSelection.Candidate(1, 0, true, true, false, false, true, true)));
        selection.complete(101);
        ShipAuraClient.logout(null);
        assertTrue(particles.isEmpty());
        assertTrue(selection.completed().isEmpty());
        assertTrue(budget.emit(10, 1, 0));
        ShipAuraClient.reset();
    }
}
