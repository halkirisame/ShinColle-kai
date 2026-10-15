package com.lulan.shincolle.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Smoke using the vanilla motion, lifetime, and scale behavior. */
@OnlyIn(Dist.CLIENT)
public class ParticleSmoke extends SmokeParticle {

    private static SpriteSet sharedSprites;

    public ParticleSmoke(ClientLevel level, double x, double y, double z,
                         double xSpeed, double ySpeed, double zSpeed, float scale, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, scale, sprites);
    }

    public static void setSharedSprites(SpriteSet sprites) {
        sharedSprites = sprites;
    }

    public static void spawn(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, float scale) {
        if (level == null || Minecraft.getInstance().level != level || sharedSprites == null) return;
        Minecraft.getInstance().particleEngine.add(new ParticleSmoke(level, x, y, z,
                vx, vy, vz, scale, sharedSprites));
    }
}
