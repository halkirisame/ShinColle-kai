package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.visualdata.ShipHeldItemKind;
import com.lulan.shincolle.visualdata.ShipVisualDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.SwordItem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** Inventory hands attached to explicit locators; never changes the legacy ship layer. */
public final class ShipVisualHeldItemLayer extends GeoRenderLayer<ShipVisualAnimatable> {
    private final ShipVisuals.Loaded visual;
    private final Matrix4f modelBasis = new Matrix4f();
    private final Matrix4f inverseBasis = new Matrix4f();
    private final Vector3f displacement = new Vector3f();
    private float modelScale = 1;

    public ShipVisualHeldItemLayer(GeoRenderer<ShipVisualAnimatable> renderer, ShipVisuals.Loaded visual) {
        super(renderer);
        this.visual = visual;
    }

    public void modelBasis(PoseStack poses, float scale) {
        modelBasis.set(poses.last().pose());
        modelScale = scale;
    }

    @Override
    public void renderForBone(PoseStack poses, ShipVisualAnimatable animatable, GeoBone bone, RenderType type,
                              MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
        boolean left = bone.getName().equals(visual.rig().get("held_item_left"));
        if (!left && !bone.getName().equals(visual.rig().get("held_item"))) return;
        BasicEntityShip ship = animatable.heldItemShip();
        if (ship == null || !ship.canShowHeldItem() || !visible(bone)) return;
        var stack = left ? ship.getOffHandItemShip() : ship.getMainHandItemShip();
        if (stack.isEmpty()) return;
        poses.pushPose();
        try {
            // Configuration offsets and crouching are in entity axes, before the animated arm chain.
            inverseBasis.set(poses.last().pose()).invert().mul(modelBasis);
            displacement.set((float) ConfigHandler.scaleHeldItem[1] * (left ? -1 : 1) / modelScale,
                    ((float) ConfigHandler.scaleHeldItem[2] + (ship.isCrouching() ? 0.2F : 0)) / modelScale,
                    (float) ConfigHandler.scaleHeldItem[3] / modelScale);
            inverseBasis.transformDirection(displacement);
            poses.translate(displacement.x, displacement.y, displacement.z);
            attach(poses, bone, visual.definition().heldItem(), left);
            applyItemScale(poses, stack.getItem().getClass(), ConfigHandler.scaleHeldItem[0]);
            Minecraft.getInstance().getItemRenderer().renderStatic(ship, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poses, buffers, ship.level(), light, OverlayTexture.NO_OVERLAY, ship.getId());
        } finally {
            poses.popPose();
            buffers.getBuffer(type);
        }
    }

    static ShipHeldItemKind kind(Class<? extends Item> item) {
        if (SwordItem.class.isAssignableFrom(item) || DiggerItem.class.isAssignableFrom(item)) return ShipHeldItemKind.TOOL;
        return BlockItem.class.isAssignableFrom(item) ? ShipHeldItemKind.BLOCK : ShipHeldItemKind.OTHER;
    }

    static void applyItemScale(PoseStack poses, Class<? extends Item> item, double configured) {
        ShipHeldItemKind kind = kind(item);
        poses.scale(kind.scaleX(configured), kind.scaleY(configured), kind.scaleZ(configured));
    }

    static boolean visible(GeoBone bone) {
        if (bone.isHidden()) return false;
        for (GeoBone parent = bone.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.isHidden() || parent.isHidingChildren()) return false;
        }
        return true;
    }

    static void attach(PoseStack poses, GeoBone bone, ShipVisualDefinition.HeldItem settings, boolean left) {
        // The callback already includes this bone's rotation and scale; only restore its pivot, not its rotation.
        RenderUtils.translateToPivotPoint(poses, bone);
        var offset = settings.offset();
        poses.translate(offset.x() * (left ? -1 : 1), offset.y(), offset.z());
        var rotate = settings.rotate();
        poses.mulPose(Axis.XP.rotationDegrees(-90F + (float) (rotate.x() % 360)));
        poses.mulPose(Axis.YP.rotationDegrees(180F + (float) (rotate.y() % 360)));
        poses.mulPose(Axis.ZP.rotationDegrees((float) (rotate.z() % 360)));
        poses.translate((left ? -1 : 1) / 16F, 0.125F, -0.625F);
    }
}
