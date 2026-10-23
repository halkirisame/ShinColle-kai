package com.lulan.shincolle.client.render;

import com.google.gson.JsonParser;
import com.lulan.shincolle.client.visual.PostureTestShips;
import com.lulan.shincolle.client.visual.ShipVisualAnimatable;
import com.lulan.shincolle.client.visual.ShipVisualGeoModel;
import com.lulan.shincolle.client.visual.ShipVisualPostureGeometryTest;
import com.lulan.shincolle.client.visual.ShipVisuals;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.lulan.shincolle.client.visual.ShipVisualTestResources.files;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.id;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.reload;
import static com.lulan.shincolle.client.visual.ShipVisualTestResources.resources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Observes the vertices that the real root-bone callback draws for a bound entity. */
class GenericShipRendererPostureTest {
    private static final Path ANIMATION = Path.of("src/main/resources/assets/shincolle_kai/animations/kashima.animation.json");

    private static final class Recorder implements VertexConsumer {
        float minimumY = Float.POSITIVE_INFINITY;
        int vertices;

        @Override public VertexConsumer vertex(double x, double y, double z) {
            minimumY = Math.min(minimumY, (float) y);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { vertices++; }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    }

    /** Draws the whole model through the renderer's own root callback and returns the lowest drawn point. */
    private static Recorder drawNoFuelPose(Mob boundEntity) throws IOException {
        reload(resources(files()));
        var visual = ShipVisuals.resolve(id("kashima"));
        var context = new EntityRendererProvider.Context(null, null, null, null, null, null, null);
        var instance = new GenericShipRenderer.Instance<Mob>(context, visual);
        var model = (ShipVisualGeoModel) instance.renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(visual.definition().model());
        var animations = JsonParser.parseString(Files.readString(ANIMATION)).getAsJsonObject().getAsJsonObject("animations");
        ShipVisualPostureGeometryTest.apply(baked, animations.getAsJsonObject(
                visual.definition().clip(ShipVisualEvent.NO_FUEL)).getAsJsonObject("bones"), 0);
        var animatable = animatableOf(instance);
        if (boundEntity != null) animatable.bind(boundEntity, visual);
        var recorder = new Recorder();
        var root = baked.getBone(visual.rig().get("root")).orElseThrow();
        instance.renderer.renderRecursively(new PoseStack(), animatable, root, null, null, recorder, false, 0,
                0xF000F0, 0, 1, 1, 1, 1);
        return recorder;
    }

    private static ShipVisualAnimatable animatableOf(GenericShipRenderer.Instance<Mob> instance) {
        try {
            Field field = GenericShipRenderer.Instance.class.getDeclaredField("animatable");
            field.setAccessible(true);
            return (ShipVisualAnimatable) field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void notSittingNoFuelEntityIsLiftedOutOfTheGroundByTheRealRootCallback() throws IOException {
        var lowered = drawNoFuelPose(null);
        var lifted = drawNoFuelPose(PostureTestShips.create(true, false, false, false));
        assertTrue(lowered.vertices > 0 && lifted.vertices == lowered.vertices);
        assertTrue(lowered.minimumY < -0.4F, "the unlifted pose must start below the ground: " + lowered.minimumY);
        assertTrue(lifted.minimumY >= -0.0001F, "a bound not-sitting entity must be lifted: " + lifted.minimumY);
        assertEquals(-lowered.minimumY, lifted.minimumY - lowered.minimumY, 0.0001F);
    }
}
