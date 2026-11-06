package com.lulan.shincolle.client.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.lulan.shincolle.visualdata.ShipHeadLook;
import com.lulan.shincolle.visualdata.ShipVisualPresentation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.model.GeoModel;

/** Mutable baked bones belong to this one entity, never to the shared resource snapshot. */
public final class ShipVisualGeoModel extends GeoModel<ShipVisualAnimatable> {
    private final ShipVisuals.Loaded visual;
    private BakedGeoModel baked;

    public ShipVisualGeoModel(ShipVisuals.Loaded visual) {
        this.visual = visual;
    }

    @Override
    public ResourceLocation getModelResource(ShipVisualAnimatable entity) {
        return visual.definition().model();
    }

    @Override
    public ResourceLocation getTextureResource(ShipVisualAnimatable entity) {
        return entity.texture() == null ? visual.definition().texture() : entity.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(ShipVisualAnimatable entity) {
        return visual.definition().animation();
    }

    @Override
    public BakedGeoModel getBakedModel(ResourceLocation resource) {
        if (baked == null) {
            baked = BakedModelFactory.getForNamespace(resource.getNamespace()).constructGeoModel(GeometryTree.fromModel(visual.geometry()));
            getAnimationProcessor().setActiveModel(baked);
        }
        return baked;
    }

    @Override
    public Animation getAnimation(ShipVisualAnimatable entity, String clip) {
        return visual.animations().getAnimation(clip);
    }

    @Override
    public void setCustomAnimations(ShipVisualAnimatable entity, long instanceId, AnimationState<ShipVisualAnimatable> state) {
        applyPresentation(entity.expression(state.getPartialTick()), entity.equipmentVisible());
        var data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        String headRole = visual.rig().get("head");
        if (headRole != null) {
            getBone(headRole).ifPresent(head -> {
                var rest = head.getInitialSnapshot();
                boolean driven = clipRotates(state, head.getName());
                head.setRotX(ShipHeadLook.compose(head.getRotX(), rest.getRotX(),
                        data == null ? 0 : data.headPitch() * Mth.DEG_TO_RAD, driven));
                head.setRotY(ShipHeadLook.compose(head.getRotY(), rest.getRotY(),
                        data == null ? 0 : data.netHeadYaw() * Mth.DEG_TO_RAD, driven));
                head.setRotZ(ShipHeadLook.compose(head.getRotZ(), rest.getRotZ(), entity.headTilt(state.getPartialTick()), driven));
                applyHeadFollow(head.getRotX(), head.getRotZ(), state);
            });
        }
    }

    private void applyHeadFollow(float headX, float headZ, AnimationState<ShipVisualAnimatable> state) {
        var follow = visual.definition().headFollow();
        for (String name : follow.bones()) {
            getBone(name).ifPresent(bone -> {
                var rest = bone.getInitialSnapshot();
                var axes = follow.overrides().get(name);
                float x = axes == null ? follow.coefficient() : axes.x();
                float z = axes == null ? follow.coefficient() : axes.z();
                boolean driven = clipRotates(state, name);
                bone.setRotX(ShipHeadLook.compose(bone.getRotX(), rest.getRotX(), -x * headX, driven));
                bone.setRotZ(ShipHeadLook.compose(bone.getRotZ(), rest.getRotZ(), -z * headZ, driven));
            });
        }
    }

    /** Whether the playing clip animates the bone, so the engine rewrites it on every frame. */
    private static boolean clipRotates(AnimationState<ShipVisualAnimatable> state, String bone) {
        var queued = state.getController().getCurrentAnimation();
        if (queued == null) return false;
        for (var animation : queued.animation().boneAnimations()) {
            if (animation.boneName().equals(bone)) return true;
        }
        return false;
    }

    void applyPresentation(ShipVisualPresentation.Expression expression, boolean equipmentVisible) {
        visual.expressions().forEach((slot, bones) -> {
            for (int index = 0; index < bones.size(); index++) {
                var visibility = ShipVisualPresentation.visibility(slot, index, expression,
                        visual.definition().expressions().containsKey(slot), bones.size());
                getBone(bones.get(index)).ifPresent(bone -> {
                    bone.setHidden(!visibility.visible());
                    bone.setRotY(visibility.flipped() ? 3.14159F : 0);
                });
            }
        });
        String equipment = visual.rig().get("equipment");
        if (equipment != null) getBone(equipment).ifPresent(bone -> {
            bone.setHidden(!equipmentVisible);
            bone.setChildrenHidden(!equipmentVisible);
        });
    }

    /** Applied only to the drawing matrix, never fed back into animation snapshots or entity position. */
    public float postureOffset(boolean active) {
        if (!active || baked == null || !visual.rig().containsKey("root")) return 0;
        return ShipVisualPresentation.postureLift(true, ShipVisualPose.minimumSupportY(baked, visual.rig()),
                visual.definition().scale()) / visual.definition().scale();
    }

    public int packedLight(GeoBone bone, int light) {
        return visual.glowBones().contains(bone.getName()) ? 0xF000F0 : light;
    }

    public boolean skipBone(GeoBone bone, boolean distant) {
        return distant && visual.lodBones().contains(bone.getName());
    }
}
