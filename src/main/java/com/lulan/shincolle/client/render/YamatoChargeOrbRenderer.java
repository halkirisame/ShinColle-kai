package com.lulan.shincolle.client.render;

import com.lulan.shincolle.entity.battleship.EntityBattleshipYamato;
import com.lulan.shincolle.entity.battleship.EntityBattleshipYamatoMob;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Draws the pulsing white charge orb shared by friendly and hostile Yamato.
 */
public final class YamatoChargeOrbRenderer {

    private static final int SIDES = 8;
    private static final float ALPHA = 0.3F;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final RenderType ORB_RENDER_TYPE = RenderType.create(
            Reference.MOD_ID + ":yamato_charge_orb",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "shincolle_yamato_charge_orb_transparency",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                        GlStateManager.SourceFactor.ONE,
                                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                            },
                            () -> {
                                RenderSystem.disableBlend();
                                RenderSystem.defaultBlendFunc();
                            }))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                    .createCompositeState(true));

    private YamatoChargeOrbRenderer() {
    }

    public static boolean shouldRender(Entity entity) {
        return entity instanceof EntityBattleshipYamato yamato
                && yamato.getStateEmotion(ID.S.Phase) > 0
                || entity instanceof EntityBattleshipYamatoMob yamatoMob
                && yamatoMob.getStateEmotion(ID.S.Phase) > 0;
    }

    public static void render(Entity entity, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        float baseRadius = 0.3F + 0.2F * entity.getBbWidth();
        float pulse = Mth.sin((entity.tickCount + partialTick) * 0.5F) * 0.15F * baseRadius;
        float jitter = (RANDOM.nextFloat() - 0.5F) * 0.08F * baseRadius;
        float radius = baseRadius + pulse + jitter;

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vertices = bufferSource.getBuffer(ORB_RENDER_TYPE);
        renderDisk(vertices, pose, radius, DiskPlane.XY);
        renderDisk(vertices, pose, radius, DiskPlane.YZ);
        renderDisk(vertices, pose, radius, DiskPlane.XZ);
        poseStack.popPose();
    }

    private static void renderDisk(VertexConsumer vertices, PoseStack.Pose pose, float radius, DiskPlane plane) {
        for (int side = 0; side < SIDES; side++) {
            float angle0 = Mth.TWO_PI * side / SIDES;
            float angle1 = Mth.TWO_PI * (side + 1) / SIDES;
            vertex(vertices, pose, 0.0F, 0.0F, 0.0F);
            vertex(vertices, pose, plane.x(Mth.cos(angle0) * radius, Mth.sin(angle0) * radius),
                    plane.y(Mth.cos(angle0) * radius, Mth.sin(angle0) * radius),
                    plane.z(Mth.cos(angle0) * radius, Mth.sin(angle0) * radius));
            vertex(vertices, pose, plane.x(Mth.cos(angle1) * radius, Mth.sin(angle1) * radius),
                    plane.y(Mth.cos(angle1) * radius, Mth.sin(angle1) * radius),
                    plane.z(Mth.cos(angle1) * radius, Mth.sin(angle1) * radius));
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z) {
        vertices.vertex(pose.pose(), x, y, z).color(1.0F, 1.0F, 1.0F, ALPHA).endVertex();
    }

    private enum DiskPlane {
        XY {
            @Override
            float x(float radialX, float radialY) {
                return radialX;
            }

            @Override
            float y(float radialX, float radialY) {
                return radialY;
            }

            @Override
            float z(float radialX, float radialY) {
                return 0.0F;
            }
        },
        YZ {
            @Override
            float x(float radialX, float radialY) {
                return 0.0F;
            }

            @Override
            float y(float radialX, float radialY) {
                return radialX;
            }

            @Override
            float z(float radialX, float radialY) {
                return radialY;
            }
        },
        XZ {
            @Override
            float x(float radialX, float radialY) {
                return radialX;
            }

            @Override
            float y(float radialX, float radialY) {
                return 0.0F;
            }

            @Override
            float z(float radialX, float radialY) {
                return radialY;
            }
        };

        abstract float x(float radialX, float radialY);

        abstract float y(float radialX, float radialY);

        abstract float z(float radialX, float radialY);
    }
}
