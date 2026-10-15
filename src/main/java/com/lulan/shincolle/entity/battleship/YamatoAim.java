package com.lulan.shincolle.entity.battleship;

import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Turns Yamato's body toward its target so the charged beam leaves the muzzle at the front. */
final class YamatoAim {
    /** Degrees per tick while charging. */
    static final float TURN_STEP = 30F;
    /** A shot more than this far off the body's front first snaps the body onto the target. */
    static final float FIRE_TOLERANCE = 20F;

    private YamatoAim() { }

    /** Yaw in degrees that faces the horizontal offset (dx, dz). */
    static float yawToward(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Next body yaw: the current one moved toward {@code wanted} by at most {@code step}. */
    static float steppedYaw(float current, float wanted, float step) {
        return current + Mth.clamp(Mth.wrapDegrees(wanted - current), -step, step);
    }

    static void turnToward(Mob host, Entity target, float step) {
        if (target == null || !target.isAlive()) return;
        float wanted = yawToward(target.getX() - host.getX(), target.getZ() - host.getZ());
        if (Math.abs(Mth.wrapDegrees(wanted - host.yBodyRot)) < 1F) return;
        apply(host, steppedYaw(host.yBodyRot, wanted, step));
    }

    /** Called when firing: never shoot out of the back or side. */
    static void faceIfOff(Mob host, Entity target) {
        float wanted = yawToward(target.getX() - host.getX(), target.getZ() - host.getZ());
        if (Math.abs(Mth.wrapDegrees(wanted - host.yBodyRot)) > FIRE_TOLERANCE) {
            apply(host, wanted);
        }
    }

    private static void apply(Mob host, float yaw) {
        host.setYRot(yaw);
        host.yBodyRot = yaw;
        host.setYHeadRot(yaw);
        ModNetworking.sendToAllTracking(S2CEntitySyncPacket.syncRotation(host), host);
    }
}
