package com.lulan.shincolle.client;

import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.EmotionHelper;
import net.minecraft.world.entity.Entity;

/** Shared position for Yamato's orb and steady charge lightning. */
public final class YamatoChargeVisuals {
    private YamatoChargeVisuals() { }

    public static double heightOffset(Entity entity) {
        IShipEmotion ship = (IShipEmotion) entity;
        return heightOffset(entity.getBbHeight(), ship.getScaleLevel(), ship.getIsSitting() || ship.getIsRiding(),
                EmotionHelper.checkModelState(0, ship.getStateEmotion(ID.S.State)),
                ship.getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED);
    }

    /** Horizontal {x, z} offset of the muzzle: twice the hull width along the body yaw (radians). */
    public static double[] forwardOffset(Entity entity, float bodyYawRad) {
        return forwardOffset(entity.getBbWidth(), bodyYawRad);
    }

    static double[] forwardOffset(double width, double bodyYawRad) {
        double reach = width * 2D;
        return new double[] {-reach * Math.sin(bodyYawRad), reach * Math.cos(bodyYawRad)};
    }

    static double heightOffset(double height, int scaleLevel, boolean lowered, boolean cannon, boolean bored) {
        if (!lowered) return height * 0.6D;
        double scale = switch (scaleLevel) {
            case 1 -> 1D;
            case 2 -> 1.5D;
            case 3 -> 2D;
            default -> 0.5D;
        };
        double poseY = cannon ? 0.4D : bored ? 0.5D : 0.54D;
        return height * 0.6D - poseY * (1D + scale);
    }
}
