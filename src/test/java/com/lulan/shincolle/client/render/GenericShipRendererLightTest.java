package com.lulan.shincolle.client.render;

import com.lulan.shincolle.client.visual.ShipVisualGeoModel;
import com.lulan.shincolle.client.visual.ShipVisuals;
import com.lulan.shincolle.client.visual.ShipVisualTestResources;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Observes the light that the real bone-drawing callback hands to the vertex consumer. */
class GenericShipRendererLightTest {
    private static final int FULL = 0xF000F0;

    private static final class Recorder implements VertexConsumer {
        final Set<Integer> lights = new HashSet<>();
        int vertices;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            lights.add(u | (v << 16));
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            vertices++;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) { }

        @Override
        public void unsetDefaultColor() { }
    }

    private static Recorder draw(GenericShipRenderer.Instance<Mob> instance, ShipVisualGeoModel model,
                                 ShipVisuals.Loaded visual, String boneName, int light) {
        var bone = model.getBakedModel(visual.definition().model()).getBone(boneName).orElseThrow();
        var recorder = new Recorder();
        instance.renderer.renderCubesOfBone(new PoseStack(), bone, recorder, light, 0, 1, 1, 1, 1);
        return recorder;
    }

    @Test
    void realBoneCallbackSendsFullBrightnessOnlyToGlowBonesAndKeepsAmbientLightElsewhere() throws IOException {
        ShipVisualTestResources.reload(ShipVisualTestResources.resources(ShipVisualTestResources.files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var context = new EntityRendererProvider.Context(null, null, null, null, null, null, null);
        var instance = new GenericShipRenderer.Instance<Mob>(context, visual);
        var model = new ShipVisualGeoModel(visual);
        var baked = model.getBakedModel(visual.definition().model());
        assertTrue(visual.glowBones().contains("Face0"));
        baked.getBone("Face0").orElseThrow().setHidden(false);
        baked.getBone("Face1").orElseThrow().setHidden(true);

        var face = draw(instance, model, visual, "Face0", 7);
        assertTrue(face.vertices > 0);
        assertEquals(Set.of(FULL), face.lights);

        for (String name : new String[]{"BodyMain", "Head", "Hair"}) {
            assertFalse(visual.glowBones().contains(name));
            var ordinary = draw(instance, model, visual, name, 7);
            assertEquals(Set.of(7), ordinary.lights, name);
        }

        // A hidden expression part is not drawn at all, so it cannot receive any light.
        assertTrue(baked.getBone("Face1").orElseThrow().isHidden());
        assertEquals(0, draw(instance, model, visual, "Face1", 7).vertices);
    }
}
