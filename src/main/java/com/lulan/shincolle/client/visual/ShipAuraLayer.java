package com.lulan.shincolle.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** A single depth-tested model pass; GeckoLib reRender skips all per-bone layers and animation updates. */
public final class ShipAuraLayer extends GeoRenderLayer<ShipVisualAnimatable> {
    public ShipAuraLayer(GeoRenderer<ShipVisualAnimatable> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poses, ShipVisualAnimatable entity, BakedGeoModel model, RenderType type,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
        if (!entity.redAura()) return;
        RenderType red = RenderType.entityTranslucent(getTextureResource(entity), false);
        getRenderer().reRender(model, poses, buffers, entity, red, buffers.getBuffer(red), partialTick,
                light, OverlayTexture.NO_OVERLAY, 1, 0.15F, 0.15F, 0.25F);
    }
}
