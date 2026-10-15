package com.lulan.shincolle.utility;

import com.lulan.shincolle.client.particle.*;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Particle spawning utility class for ShinColle.
 * Provides helper methods for spawning attack, emotion, and effect particles.
 * <p>
 * The original mod (1.10.2) used numeric particle type IDs:
 * Types 1-49: Position-based particles (explosions, flames, etc.)
 * Entity-attached packet routes are decoded separately before dispatch.
 * <p>
 * Sprite-based particles (Spray, Smoke) are spawned via level.addParticle()
 * through the registered provider system.
 * Custom-rendered particles (Laser, Lightning, etc.) are spawned directly via
 * Minecraft.getInstance().particleEngine.add() since they need complex
 * constructor parameters (Entity references, type IDs, float arrays) that
 * can't pass through the standard ParticleProvider interface.
 */
public class ParticleHelper {

    private static final float TEAM_CIRCLE_ENTITY_SCALE = 0.35F;

    /**
     * Spawn attack particles at a position. Type IDs match the original mod's
     * particle system.
     * This method handles only the position-based effect table.
     *
     * @param level the world/level instance
     * @param x     X position
     * @param y     Y position
     * @param z     Z position
     * @param lookX look vector X (used for directional particles)
     * @param lookY look vector Y
     * @param lookZ look vector Z
     * @param type  particle type ID from the original mod's system
     */
    public static void spawnAttackParticleAt(Level level, double x, double y, double z,
                                             double lookX, double lookY, double lookZ, int type) {
        if (level != null && level.isClientSide() && level instanceof ClientLevel clientLevel) {
            PositionParticleVisuals.spawn(clientLevel, x, y, z, lookX, lookY, lookZ, type);
        }
    }

    /**
     * Spawn attack particles with default look direction (upward).
     */
    public static void spawnAttackParticleAt(Level level, double x, double y, double z, int type) {
        spawnAttackParticleAt(level, x, y, z, 0.0, 1.0, 0.0, type);
    }

    /**
     * Spawn emotion particles on an entity using custom ParticleEmotion.
     * Emotion types correspond to the original mod's emotion system:
     * 0 = sweat/drop, 1 = heart, 2 = panic, etc.
     *
     * @param entity      the entity to spawn particles on
     * @param emotionType the emotion type ID
     */
    public static void spawnEmotionParticle(Entity entity, int emotionType) {
        if (entity != null && entity.level().isClientSide()) {
            spawnEmotionParticleClient(entity, emotionType);
        }
    }

    /**
     * Spawn a spray/splash particle effect, typically used for ship movement on
     * water. Uses custom ParticleSpray via level.addParticle().
     *
     * @param level the world/level instance
     * @param x     X position
     * @param y     Y position (water surface)
     * @param z     Z position
     * @param count number of particles to spawn
     */
    public static void spawnSprayParticle(Level level, double x, double y, double z, int count) {
        if (level != null && level.isClientSide()) {
            spawnSprayParticleClient(level, x, y, z, count);
        }
    }

    /**
     * Spawn a "MISS" text particle above an entity using custom ParticleTexts.
     *
     * @param entity the entity that was missed
     */
    public static void spawnMissParticle(Entity entity) {
        if (entity != null && entity.level().isClientSide()) {
            spawnTextParticleClient((ClientLevel) entity.level(),
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.5, entity.getZ(),
                    1.0f, 0);
        }
    }

    /**
     * Spawn team circle indicator particle below an entity using custom
     * ParticleTeam.
     *
     * @param entity the entity to show the team circle for
     * @param indicatorType the visual subtype (determines color and marker behavior)
     */
    public static void spawnTeamCircle(Entity entity, int indicatorType) {
        if (entity != null && entity.level().isClientSide()) {
            spawnTeamCircleClient(entity, indicatorType);
        }
    }

    /**
     * Spawn team circle indicator particle at a world position.
     * Used for pointer block/waypoint target visualization.
     */
    public static void spawnTeamCircleAt(Level level, double x, double y, double z, int indicatorType) {
        if (level != null && level.isClientSide()) {
            spawnTeamCircleAtClient((ClientLevel) level, x, y, z, indicatorType);
        }
    }

    /** Spawn the legacy green marker that identifies an entity as a movement target. */
    public static void spawnMovingTargetMarker(Entity entity) {
        spawnTeamCircle(entity, 4);
    }

    /** Spawn the legacy red marker that identifies an entity as an attack target. */
    public static void spawnAttackTargetMarker(Entity entity) {
        spawnTeamCircle(entity, 5);
    }

    /** Spawn the legacy green marker at a commanded movement destination. */
    public static void spawnMovingTargetMarkerAt(Level level, double x, double y, double z) {
        spawnTeamCircleAt(level, x, y, z, 4);
    }

    /** Spawn the legacy white guard marker used for a block destination. */
    public static void spawnWaypointMarkerAt(Level level, double x, double y, double z) {
        spawnTeamCircleAt(level, x, y, z, 6);
    }

    /** Spawn the legacy short-lived guard line from a ship to a block destination. */
    public static void spawnGuardLineTo(LivingEntity host, double x, double y, double z) {
        if (host != null && host.level().isClientSide()) {
            spawnGuardLineToClient(host, x, y, z);
        }
    }

    /** Spawn the legacy short-lived guard line from a ship to the entity it guards. */
    public static void spawnGuardLineToEntity(LivingEntity host, Entity target) {
        if (host != null && target != null && host.level().isClientSide()) {
            spawnTargetLineClient(host, target, 2);
        }
    }

    /** Spawn the legacy long-lived position line from a player to one of the player's ships. */
    public static void spawnPositionLine(LivingEntity host, Entity target) {
        if (host != null && target != null && host.level().isClientSide()) {
            spawnTargetLineClient(host, target, 5);
        }
    }

    /** Show the local debug caress height without changing the target's hit state. */
    public static void spawnCaressPosition(Entity target, float height) {
        if (target != null && target.isAlive() && target.level().isClientSide()) {
            spawnCaressPositionClient(target, height);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnCaressPositionClient(Entity target, float height) {
        Minecraft.getInstance().particleEngine.add(new ParticleDebugPlane(target, 0, height));
    }

    public static void spawnTeamCircleAtPlayer(net.minecraft.server.level.ServerPlayer player, double x, double y, double z,
                                               int indicatorType) {
        if (player == null) return;
        io.netty.buffer.ByteBuf rawBuf = io.netty.buffer.Unpooled.buffer(48);
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(rawBuf);
        try {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeDouble(0.3); // scale (lookX)
            buf.writeDouble(indicatorType); // team indicator type (lookY)
            buf.writeDouble(0.0); // unused (lookZ)
            byte[] payload = new byte[48];
            buf.getBytes(0, payload);
            com.lulan.shincolle.network.ModNetworking.sendToPlayer(
                    new com.lulan.shincolle.network.S2CSpawnParticlePacket((byte) 25, -1, payload),
                    player
            );
        } finally {
            buf.release();
        }
    }

    /** Spawn the red marker for an order that could not be accepted. */
    public static void spawnOrderRejectedParticle(Level level, double x, double y, double z,
                                                   double vx, double vy, double vz) {
        if (level != null && level.isClientSide() && level instanceof ClientLevel clientLevel) {
            PositionParticleVisuals.spray(clientLevel, x, y, z, vx, vy, vz, 4);
        }
    }

    /** Spawn blue fire at the equipment's eye position. */
    public static void spawnEquipmentSparkParticle(Entity entity, double height, double eyeX, double eyeZ) {
        spawnSparkleParticle(entity, 1, (float) height, (float) eyeX, (float) eyeZ, 0F, 1F, 1F, 1F);
    }

    // ========== Custom particle spawn methods ==========

    /**
     * Spawn a laser beam particle from source to target position.
     *
     * @param level the world/level (must be client-side)
     * @param x     source X
     * @param y     source Y
     * @param z     source Z
     * @param tarX  target X
     * @param tarY  target Y
     * @param tarZ  target Z
     * @param scale beam scale
     * @param type  beam visual type
     */
    public static void spawnLaserParticle(Level level, double x, double y, double z,
                                          double tarX, double tarY, double tarZ, float scale, int type) {
        if (level != null && level.isClientSide()) {
            spawnLaserParticleClient((ClientLevel) level, x, y, z, tarX, tarY, tarZ, scale, type);
        }
    }

    /**
     * Spawn a lightning particle attached to an entity.
     *
     * @param entity the entity to electrify
     * @param scale  effect scale
     * @param type   lightning visual type
     */
    public static void spawnLightningParticle(Entity entity, float scale, int type) {
        if (entity != null && entity.level().isClientSide()) {
            spawnLightningParticleClient(entity, scale, type);
        }
    }

    /**
     * Spawn a chi/energy particle attached to an entity.
     *
     * @param entity the host entity
     * @param scale  effect scale
     * @param type   chi visual type
     */
    public static void spawnChiParticle(Entity entity, float scale, int type) {
        if (entity != null && entity.level().isClientSide()) {
            spawnChiParticleClient(entity, scale, type);
        }
    }

    /**
     * Spawn a cube particle effect on a living entity.
     *
     * @param entity the living entity
     * @param scale  effect scale
     * @param type   cube visual type
     */
    public static void spawnCubeParticle(LivingEntity entity, float scale, int type) {
        if (entity != null && entity.level().isClientSide()) {
            spawnCubeParticleClient(entity, scale, type);
        }
    }

    /**
     * Spawn a gradient ring particle on an entity.
     *
     * @param entity the entity
     * @param type   gradient visual type
     * @param parms  additional parameters
     */
    public static void spawnGradientParticle(Entity entity, int type, float... parms) {
        if (entity != null && entity.level().isClientSide()) {
            spawnGradientParticleClient(entity, type, parms);
        }
    }

    /**
     * Spawn a sphere light particle on an entity.
     *
     * @param entity the entity
     * @param type   light visual type
     * @param parms  additional parameters
     */
    public static void spawnSphereLightParticle(Entity entity, int type, float... parms) {
        if (entity != null && entity.level().isClientSide()) {
            spawnSphereLightParticleClient(entity, type, parms);
        }
    }

    /**
     * Spawn sticky lightning particles attached to an entity.
     * Ported for legacy projectile beam effects.
     */
    public static void spawnStickyLightningParticle(Entity entity, float scale, int life, int type) {
        if (entity != null && entity.level().isClientSide()) {
            spawnStickyLightningParticleClient(entity, scale, life, type);
        }
    }

    /**
     * Spawn a sparkle particle on an entity.
     *
     * @param entity the entity
     * @param type   sparkle visual type
     * @param parms  additional parameters
     */
    public static void spawnSparkleParticle(Entity entity, int type, float... parms) {
        if (entity != null && entity.level().isClientSide()) {
            spawnSparkleParticleClient(entity, type, parms);
        }
    }

    /**
     * Spawn a line/trail particle.
     *
     * @param level the world/level
     * @param type  line visual type
     * @param parms line parameters (positions, etc.)
     */
    public static void spawnLineParticle(Level level, int type, float[] parms) {
        if (level != null && level.isClientSide()) {
            spawnLineParticleClient((ClientLevel) level, type, parms);
        }
    }

    /**
     * Spawn a sweep arc particle on an entity.
     *
     * @param entity the entity
     * @param type   sweep visual type
     * @param parms  sweep parameters
     */
    public static void spawnSweepParticle(Entity entity, int type, float... parms) {
        if (entity != null && entity.level().isClientSide()) {
            spawnSweepParticleClient(entity, type, parms);
        }
    }

    /**
     * Spawn a crane construction particle.
     *
     * @param level     the world/level
     * @param x         X position
     * @param y         Y position
     * @param z         Z position
     * @param lengthMax maximum length
     * @param par1      parameter 1
     * @param scale     effect scale
     * @param type      visual type
     */
    public static void spawnCraningParticle(Level level, double x, double y, double z,
                                            double lengthMax, double par1, double scale, int type) {
        if (level != null && level.isClientSide()) {
            spawnCraningParticleClient((ClientLevel) level, x, y, z, lengthMax, par1, scale, type);
        }
    }

    // ========== Client-side implementation methods ==========
    // These methods use @OnlyIn(Dist.CLIENT) and directly instantiate
    // custom particle classes, adding them to the particle engine.

    @OnlyIn(Dist.CLIENT)
    private static void spawnTextParticleClient(ClientLevel level, double x, double y, double z,
                                                float scale, int type) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleTexts(level, x, y, z, scale, type));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnTargetLineClient(LivingEntity host, Entity target, int type) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleLaserNoTexture((ClientLevel) host.level(), host, target, 0D, 0D, 0D, 0.1F, type));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnGuardLineToClient(LivingEntity host, double x, double y, double z) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleLaserNoTexture((ClientLevel) host.level(), host, x, y, z, 0.1F, 3));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnEmotionParticleClient(Entity entity, int emotionType) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleEmotion(level, entity,
                        entity.getX(), entity.getY() + entity.getBbHeight(),
                        entity.getZ(), entity.getBbHeight(), 0, emotionType));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnSprayParticleClient(Level level, double x, double y, double z, int count) {
        ClientLevel clientLevel = (ClientLevel) level;
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;
            double speedY = level.random.nextDouble() * 0.2 + 0.1;
            Minecraft.getInstance().particleEngine.add(
                    new ParticleSpray(clientLevel, x + offsetX, y, z + offsetZ, 0, speedY, 0, 0));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnSprayParticleVariantClient(ClientLevel level, double x, double y, double z,
                                                        double motionX, double motionY, double motionZ, int sprayType) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleSpray(level, x, y, z, motionX, motionY, motionZ, sprayType));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnTeamCircleClient(Entity entity, int indicatorType) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleTeam(level, entity, TEAM_CIRCLE_ENTITY_SCALE, indicatorType));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnTeamCircleAtClient(ClientLevel level, double x, double y, double z, int indicatorType) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleTeam(level, TEAM_CIRCLE_ENTITY_SCALE, indicatorType, x, y, z));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnLaserParticleClient(ClientLevel level, double x, double y, double z,
                                                 double tarX, double tarY, double tarZ, float scale, int type) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleLaser(level, x, y, z, tarX, tarY, tarZ, scale, type));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnLightningParticleClient(Entity entity, float scale, int type) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleLightning(level, entity, scale, type));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnChiParticleClient(Entity entity, float scale, int type) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleChi(level, entity, scale, type));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnCubeParticleClient(LivingEntity entity, float scale, int type) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleCube(level, entity,
                        entity.getX(), entity.getY(), entity.getZ(), scale, type));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnGradientParticleClient(Entity entity, int type, float... parms) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleGradient(level, entity, type, parms));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnSphereLightParticleClient(Entity entity, int type, float... parms) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleSphereLight(entity, type, parms));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnStickyLightningParticleClient(Entity entity, float scale, int life, int type) {
        ClientLevel level = (ClientLevel) entity.level();
        // 2026/04/07：GitHub Copilotによって確認済み
        // Keep legacy visual density: railgun beam emitted 4 sticky-lightning strips
        // per tick.
        for (int i = 0; i < 4; i++) {
            Minecraft.getInstance().particleEngine.add(
                    new ParticleStickyLightning(level, entity, scale, life, type));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnSparkleParticleClient(Entity entity, int type, float... parms) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleSparkle(entity, type, parms));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnLineParticleClient(ClientLevel level, int type, float[] parms) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleLine(level, type, parms));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnSweepParticleClient(Entity entity, int type, float... parms) {
        ClientLevel level = (ClientLevel) entity.level();
        Minecraft.getInstance().particleEngine.add(
                new ParticleSweep(level, entity, type, parms));
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnCraningParticleClient(ClientLevel level, double x, double y, double z,
                                                   double lengthMax, double par1, double scale, int type) {
        Minecraft.getInstance().particleEngine.add(
                new ParticleCraning(level, x, y, z, lengthMax, par1, scale, type));
    }

    /**
     * Spawn attack text particle (miss/crit/dhit/thit indicator).
     * Called from server side (CombatHelper). Sends a particle packet to nearby
     * clients.
     * type: 0=miss, 1=critical, 2=double hit, 3=triple hit
     */
    public static void spawnAttackTextParticle(Entity entity, int type) {
        if (entity == null || entity.level().isClientSide())
            return;
        if (type < 0 || type > 4) return;
        int particleType = type == 4 ? 34 : 10 + type;
        S2CSpawnParticlePacket packet = S2CSpawnParticlePacket.position(particleType, entity.getId(),
                entity.getX(), entity.getY(), entity.getZ(), 0, entity.getBbHeight() * 1.3D, 0);
        ModNetworking.sendToAllTracking(packet, entity);
    }
}
