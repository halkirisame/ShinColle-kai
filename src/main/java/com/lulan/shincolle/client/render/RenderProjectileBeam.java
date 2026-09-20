package com.lulan.shincolle.client.render;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Renders the synchronized Yamato beam as a visible cyan line. */
public class RenderProjectileBeam extends EntityRenderer<EntityProjectileBeam> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID, "textures/entity/placeholder.png");

    public RenderProjectileBeam(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityProjectileBeam beam, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Vec3 direction = beam.getBeamDirection();
        if (direction.lengthSqr() > 0.0001D) {
            Vec3 end = direction.normalize().scale(beam.getBeamLength());
            VertexConsumer vertices = buffer.getBuffer(RenderType.lines());
            PoseStack.Pose pose = poseStack.last();
            vertices.vertex(pose.pose(), 0.0F, 0.0F, 0.0F)
                    .color(80, 230, 255, 255)
                    .normal(pose.normal(), (float) direction.x, (float) direction.y, (float) direction.z)
                    .endVertex();
            vertices.vertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                    .color(190, 250, 255, 255)
                    .normal(pose.normal(), (float) direction.x, (float) direction.y, (float) direction.z)
                    .endVertex();
        }
        super.render(beam, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityProjectileBeam beam) {
        return TEXTURE;
    }
}
