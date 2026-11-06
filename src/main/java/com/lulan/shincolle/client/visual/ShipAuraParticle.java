package com.lulan.shincolle.client.visual;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Local, non-colliding translucent motes using an existing block-atlas sprite. */
final class ShipAuraParticle extends TextureSheetParticle {
    private final long expiresAt;

    ShipAuraParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, long clientTick) {
        super(level, x, y, z);
        expiresAt = clientTick + 20;
        setSprite(sprite);
        setColor(1, 0.15F, 0.15F);
        setAlpha(0.25F);
        quadSize = 0.035F;
        lifetime = 20;
        hasPhysics = false;
        xd = 0;
        yd = 0.015;
        zd = 0;
        gravity = 0;
    }

    boolean expire(long clientTick) {
        // ParticleEngine queues additions and may evict particles without ticking or removing them.
        if (clientTick >= expiresAt) remove();
        return !isAlive();
    }

    @Override
    public void tick() {
        super.tick();
        // Vanilla's post-increment comparison otherwise leaves a particle alive for a 21st tick.
        if (age >= lifetime) remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
}
