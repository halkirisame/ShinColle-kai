package com.lulan.shincolle.client.visual.yukikaze;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.ForgeRenderTypes;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Set;

/**
 * The root callback runs after native animations and before any child geometry.
 * Eyes, mouths and skin use Forge's unlit shader instead of directional shading.
 * Skin keeps the lightmap and damage overlay; eyes and mouths stay full-bright.
 * Each surface is submitted once in its existing bone transform.
 */
public final class YukikazeExpressionLayer extends GeoRenderLayer<GeoAnimatable> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int NO_OVERLAY = 0x000A0000;
    private static final Set<String> SKIN_BONES = Set.of("SkinHead",
            "SkinHandRight", "SkinHandLeft", "SkinThighRight", "SkinThighLeft",
            "SkinCalfRight", "SkinCalfLeft");

    private final GeoReplacedEntityRenderer<?, ?> replacedRenderer;
    private int unlitFace = -1;
    private int unlitMouth = -1;

    @SuppressWarnings("unchecked")
    public YukikazeExpressionLayer(GeoReplacedEntityRenderer<?, ?> renderer) {
        super((GeoRenderer<GeoAnimatable>) (GeoRenderer<?>) renderer);
        replacedRenderer = renderer;
    }

    private int selected(String prefix, int count) {
        for (int i = 0; i < count; i++) {
            var bone = getGeoModel().getBone(prefix + i);
            if (bone.isPresent() && !bone.get().isHidden()) return i;
        }
        if (prefix.equals("Face") && unlitFace >= 0) return unlitFace;
        if (prefix.equals("Mouth") && unlitMouth >= 0) return unlitMouth;
        return 0;
    }

    private void show(String name, boolean visible) {
        getGeoModel().getBone(name).ifPresent(b -> b.setHidden(!visible));
    }

    @Override
    public void renderForBone(PoseStack poses, GeoAnimatable animatable, GeoBone bone, RenderType type,
                              MultiBufferSource buffers, VertexConsumer vertices, float partialTick, int light, int overlay) {
        if (!bone.getName().equals("root")) {
            boolean eye = unlitFace >= 0 && bone.getName().equals("Face" + unlitFace);
            boolean mouth = unlitMouth >= 0 && bone.getName().equals("Mouth" + unlitMouth);
            if (eye || mouth || SKIN_BONES.contains(bone.getName())) {
                // Full-bright lightmap alone still leaves minecraft_mix_light
                // in the normal shader. Forge's unlit material removes that
                // directional multiplier, while preserving normal depth tests.
                var unlit = ForgeRenderTypes.getUnlitTranslucent(getTextureResource(animatable), false);
                bone.setHidden(false);
                try {
                    var surface = buffers.getBuffer(unlit);
                    renderer.renderCubesOfBone(poses, bone, surface, (eye || mouth) ? FULL_BRIGHT : light,
                            (eye || mouth) ? NO_OVERLAY : overlay, 1, 1, 1, 1);
                } finally {
                    bone.setHidden(true);
                    // A non-fixed buffer source may reuse its builder. Restore
                    // the main material before native rendering visits children.
                    buffers.getBuffer(type);
                }
            }
            return;
        }
        long id = renderer.getInstanceId(animatable);
        var controller = animatable.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers().get("presentation");
        var playing = controller == null ? null : controller.getCurrentAnimation();
        String clip = playing == null ? "" : playing.animation().name();
        boolean flushed = getGeoModel().getBone("Flush0").map(b -> !b.isHidden()).orElse(false);
        var state = ExpressionPolicy.select(selected("Face", 5), selected("Mouth", 3), flushed,
                clip, animatable.getTick(replacedRenderer.getCurrentEntity()), id);
        unlitFace = state.face() >= 0 && state.face() < 4 ? state.face() : -1;
        unlitMouth = state.mouth() >= 0 && state.mouth() < 3 ? state.mouth() : -1;
        // Face4 is the native hungry black band. Keep its original rendering.
        // Eye faces are hidden in the main pass and drawn by the callback above.
        for (int i = 0; i < 5; i++) show("Face" + i, i == 4 && i == state.face());
        for (int i = 0; i < 3; i++) show("Mouth" + i, false);
        for (int i = 0; i < 2; i++) show("Flush" + i, state.flush());
        for (String name : SKIN_BONES) show(name, false);
        if (state.exhausted()) {
            getGeoModel().getBone("Head").ifPresent(head -> {
                head.setRotX(0);
                head.setRotY(0);
                head.setRotZ(0);
            });
        }
    }
}
