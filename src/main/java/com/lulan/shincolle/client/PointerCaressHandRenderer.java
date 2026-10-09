package com.lulan.shincolle.client;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.Reference;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/** Replaces the main-hand pointer with the player's arm while caressing. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PointerCaressHandRenderer {

    private PointerCaressHandRenderer() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        int mode = PointerItem.getMode(stack);
        if (event.getHand() != InteractionHand.MAIN_HAND || stack.getItem() != ModItems.POINTER.get()
                || mode < 3 || mode > 5) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);
        if (player.isInvisible() || player.isScoping()) {
            return;
        }

        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1F : -1F;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(side * 0.64000005F, -0.6F, -0.71999997F);
            pose.mulPose(Axis.YP.rotationDegrees(side * 45F));
            pose.translate(side * -1F, 3.6F, 3.5F);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 120F));
            pose.mulPose(Axis.XP.rotationDegrees(200F));
            pose.mulPose(Axis.YP.rotationDegrees(side * -135F));
            pose.translate(side * 5.6F, 0F, 0F);

            if (mc.options.keyUse.isDown()) {
                applyCaressMotion(pose, mode, player.tickCount + event.getPartialTick());
            }

            if (right) {
                renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            } else {
                renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void applyCaressMotion(PoseStack pose, int mode, float ticks) {
        switch (mode) {
            case 3:
                pose.translate(1.3F, 4F, 0F);
                pose.scale(3F, 3F, 3F);
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.cos(ticks * 0.125F) * -20F - 60F));
                break;
            case 4:
                pose.mulPose(Axis.YP.rotationDegrees(70F));
                pose.mulPose(Axis.ZP.rotationDegrees(-20F));
                pose.translate(-2F, 16F, 10F);
                pose.scale(12F, 12F, 12F);
                pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(ticks * 0.1F) * -15F + 20F));
                break;
            case 5:
                pose.translate(13.5F, 12.5F, 2.5F);
                pose.scale(9F, 9F, 9F);
                float radians = (Mth.cos(ticks * 0.2F) * -15F - 20F) * Mth.DEG_TO_RAD;
                float axis = (float) Math.sqrt(0.5D);
                pose.mulPose(new Quaternionf().rotationAxis(radians, axis, axis, 0F));
                break;
        }
    }
}
