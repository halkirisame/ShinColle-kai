package com.lulan.shincolle.client.render;

import com.lulan.shincolle.client.model.ModelMidwayHime;
import com.lulan.shincolle.client.model.ModelTakoyaki;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.Reference;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Seated Midway Hime's animated companion, without spawning a world entity. */
@OnlyIn(Dist.CLIENT)
public final class LayerMidwayTakoyaki<T extends BasicEntityShip> extends RenderLayer<T, EntityModel<T>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/entity/entityaircrafttakoyaki.png");

    private final ModelMidwayHime hostModel;
    private final ModelTakoyaki takoyaki;

    public LayerMidwayTakoyaki(RenderLayerParent<T, EntityModel<T>> renderer,
                              EntityRendererProvider.Context context, ModelMidwayHime hostModel) {
        super(renderer);
        this.hostModel = hostModel;
        this.takoyaki = new ModelTakoyaki(context.bakeLayer(ModelTakoyaki.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (!this.hostModel.renderTako) {
            return;
        }
        poseStack.pushPose();
        // Undo the living model basis before applying the companion's local transform.
        poseStack.translate(0D, 1.501D, 0D);
        poseStack.scale(-1F, -1F, 1F);
        poseStack.translate(0D, this.hostModel.takoyakiHeight, -0.18D);
        poseStack.scale(0.65F, 0.65F, 0.65F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-30F));
        poseStack.scale(-1F, -1F, 1F);
        poseStack.translate(0D, -1.501D, 0D);
        this.takoyaki.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.takoyaki.renderToBuffer(poseStack, buffer.getBuffer(this.takoyaki.renderType(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        poseStack.popPose();
    }
}
