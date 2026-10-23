package com.lulan.shincolle.client.render;

import com.lulan.shincolle.shipdata.DefinitionBackedShip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.lulan.shincolle.client.visual.ShipVisualAnimatable;
import com.lulan.shincolle.client.visual.ShipVisualGeoModel;
import com.lulan.shincolle.client.visual.ShipVisuals;
import com.lulan.shincolle.client.visual.ShipVisualHeldItemLayer;
import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.visualdata.ShipVisualRenderMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/** Data-defined ships only; legacy renderers and entity classes remain independent of GeckoLib. */
public final class GenericShipRenderer<T extends Mob & DefinitionBackedShip> extends EntityRenderer<T> {
    private static final ResourceLocation MISSING_VISUAL = new ResourceLocation("shincolle_kai", "missing");
    private final EntityRendererProvider.Context context;
    private final Map<T, Instance<T>> instances = new WeakHashMap<>();

    public GenericShipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.context = context;
    }

    private ShipVisuals.Loaded visual(T entity) {
        var presentation = entity.definitionState().presentation(true);
        return ShipVisuals.resolve(presentation == null ? MISSING_VISUAL : presentation.visual());
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return visual(entity).definition().texture();
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        if (entity.definitionState().dormant()) {
            shadowRadius = 0;
            renderNameTag(entity, entity.getDisplayName(), poses, buffers, light);
        } else {
            var visual = visual(entity);
            Instance<T> instance = instances.get(entity);
            if (instance == null || instance.source != visual) {
                instance = new Instance<>(context, visual);
                instances.put(entity, instance);
            }
            int level = entity instanceof IShipEmotion emotion ? emotion.getScaleLevel() : 0;
            shadowRadius = ShipVisualRenderMath.shadow(visual.definition().shadowRadius(), level);
            instance.scale = ShipVisualRenderMath.scale(visual.definition().scale(), level);
            instance.renderer.withScale(instance.scale);
            instance.distant = ShipVisualRenderMath.distant(
                    Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position()),
                    visual.definition().lod().distance());
            instance.animatable.bind(entity, visual);
            try {
                instance.renderer.render(entity, yaw, partialTick, poses, buffers, light);
            } finally {
                instance.animatable.unbind();
                instance.renderer.doPostRenderCleanup();
            }
        }
    }

    static final class Instance<E extends Mob> {
        private final ShipVisuals.Loaded source;
        private final ShipVisualAnimatable animatable = new ShipVisualAnimatable();
        final GeoReplacedEntityRenderer<E, ShipVisualAnimatable> renderer;
        private final ShipVisualHeldItemLayer heldItems;
        private float scale;
        private boolean distant;

        Instance(EntityRendererProvider.Context context, ShipVisuals.Loaded visual) {
            source = visual;
            ShipVisualGeoModel shipModel = new ShipVisualGeoModel(visual);
            renderer = new GeoReplacedEntityRenderer<>(context, shipModel, animatable) {
                @Override
                public void renderRecursively(PoseStack poses, ShipVisualAnimatable entity, GeoBone bone, RenderType type,
                                              MultiBufferSource buffers, VertexConsumer buffer, boolean reRender, float partialTick,
                                               int light, int overlay, float red, float green, float blue, float alpha) {
                    if (shipModel.skipBone(bone, distant)) return;
                    if (bone.getParent() == null) heldItems.modelBasis(poses, scale);
                    boolean rootBone = bone.getName().equals(visual.rig().get("root"));
                    if (rootBone) {
                        poses.pushPose();
                        poses.translate(0, shipModel.postureOffset(entity.hasPosture()), 0);
                    }
                    try {
                        super.renderRecursively(poses, entity, bone, type, buffers, buffer, reRender, partialTick,
                                light, overlay, red, green, blue, alpha);
                    } finally {
                        if (rootBone) poses.popPose();
                    }
                }

                @Override
                public void renderCubesOfBone(PoseStack poses, GeoBone bone, VertexConsumer buffer, int light,
                                              int overlay, float red, float green, float blue, float alpha) {
                    super.renderCubesOfBone(poses, bone, buffer, shipModel.packedLight(bone, light),
                            overlay, red, green, blue, alpha);
                }

                @Override
                protected float getDeathMaxRotation(ShipVisualAnimatable entity) {
                    return 0;
                }
            };
            heldItems = new ShipVisualHeldItemLayer(renderer, visual);
            renderer.addRenderLayer(heldItems);
        }
    }
}
