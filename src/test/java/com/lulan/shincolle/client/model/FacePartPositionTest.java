package com.lulan.shincolle.client.model;

import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacePartPositionTest {
    @Test
    void neUsesItsOwnFacePlacement() {
        assertFacePositions(ModelHeavyCruiserNe.createBodyLayer().bakeRoot(), -8.5F, -0.7F, 0.7F, -6.8F);
    }

    @Test
    void caHimeUsesItsOwnFacePlacement() {
        assertFacePositions(ModelCAHime.createBodyLayer().bakeRoot(), -8.5F, -0.7F, 0.7F, -6.8F);
    }

    @Test
    void hibikiKeepsDefaultEyesButUsesItsOwnBlushDepth() {
        assertFacePositions(ModelDestroyerHibiki.createBodyLayer().bakeRoot(), -12.2F, -4.2F, -3.0F, -6.8F);
    }

    @Test
    void woKeepsDefaultEyesButUsesItsOwnBlushDepth() {
        assertFacePositions(ModelCarrierWo.createBodyLayer().bakeRoot(), -12.2F, -4.2F, -3.0F, -6.8F);
    }

    @Test
    void hibikiUsesItsOwnMouthAndBlushTexture() {
        assertFaceTexture(ModelDestroyerHibiki.createBodyLayer().bakeRoot(), 128, 128,
                new int[] {22, 52, 100, 58, 114, 56}, new int[] {114, 61});
    }

    @Test
    void woUsesItsOwnMouthAndBlushTexture() {
        assertFaceTexture(ModelCarrierWo.createBodyLayer().bakeRoot(), 256, 128,
                new int[] {69, 91, 69, 96, 83, 91}, new int[] {83, 96});
    }

    @Test
    void akagiKeepsDefaultFaceTexture() {
        assertFaceTexture(ModelCarrierAkagi.createBodyLayer().bakeRoot(), 256, 128,
                new int[] {100, 53, 100, 58, 114, 53}, new int[] {114, 58});
    }

    @Test
    void akagiKeepsDefaultFacePlacement() {
        assertFacePositions(ModelCarrierAkagi.createBodyLayer().bakeRoot(), -12.2F, -4.2F, -3.0F, -6.9F);
    }

    private static void assertFacePositions(ModelPart root, float faceY, float mouthY, float flushY, float flushZ) {
        ModelPart glowHead = root.getChild("GlowBodyMain").getChild("GlowHead");
        for (int index = 0; index < 5; index++) {
            ModelPart face = glowHead.getChild("Face" + index);
            assertEquals(faceY, face.getInitialPose().y, 0.0001F, "Face" + index);
            assertEquals(-6.1F, face.getInitialPose().z, 0.0001F, "Face" + index);
        }
        for (int index = 0; index < 3; index++) {
            ModelPart mouth = glowHead.getChild("Mouth" + index);
            assertEquals(mouthY, mouth.getInitialPose().y, 0.0001F, "Mouth" + index);
            assertEquals(-6.2F, mouth.getInitialPose().z, 0.0001F, "Mouth" + index);
        }
        for (int index = 0; index < 2; index++) {
            ModelPart flush = glowHead.getChild("Flush" + index);
            assertEquals(index == 0 ? -6F : 6F, flush.getInitialPose().x, 0.0001F, "Flush" + index);
            assertEquals(flushY, flush.getInitialPose().y, 0.0001F, "Flush" + index);
            assertEquals(flushZ, flush.getInitialPose().z, 0.0001F, "Flush" + index);
        }
    }

    private static void assertFaceTexture(ModelPart root, int width, int height, int[] mouthUv, int[] flushUv) {
        ModelPart glowHead = root.getChild("GlowBodyMain").getChild("GlowHead");
        for (int index = 0; index < 3; index++) {
            assertTexOffs(glowHead.getChild("Mouth" + index), width, height,
                    mouthUv[index * 2], mouthUv[index * 2 + 1], "Mouth" + index);
        }
        for (int index = 0; index < 2; index++) {
            assertTexOffs(glowHead.getChild("Flush" + index), width, height, flushUv[0], flushUv[1], "Flush" + index);
        }
    }

    /** A box's texture offset is the smallest u and v among its drawn vertices. */
    private static void assertTexOffs(ModelPart part, int width, int height, int u, int v, String name) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE};
        part.render(new PoseStack(), new DefaultedVertexConsumer() {
            @Override
            public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                               float texU, float texV, int overlay, int light, float nx, float ny, float nz) {
                min[0] = Math.min(min[0], texU);
                min[1] = Math.min(min[1], texV);
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer color(int red, int green, int blue, int alpha) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer uv(float texU, float texV) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) {
                return this;
            }

            @Override
            public void endVertex() {
            }
        }, 0, 0);
        assertEquals(u, min[0] * width, 0.01F, name + " u");
        assertEquals(v, min[1] * height, 0.01F, name + " v");
    }
}
