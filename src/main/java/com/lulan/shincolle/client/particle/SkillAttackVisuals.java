package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.network.SkillVisualEffect;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Visual-only recreation of the three ships' attack effects. */
public final class SkillAttackVisuals {
    private SkillAttackVisuals() { }

    public static void show(SkillVisualEffect effect, Entity entity, double x, double y, double z,
                            double vx, double vy, double vz) {
        ClientLevel level = (ClientLevel) entity.level();
        float h = entity.getBbHeight();
        switch (effect) {
            case TENRYUU_CHARGE, TATSUTA_CHARGE -> {
                float blue = effect == SkillVisualEffect.TENRYUU_CHARGE ? 0.7F : 0.8F;
                ParticleHelper.spawnSphereLightParticle(entity, 0,
                        h * 1.2F, h * 0.75F, 0.8F, 0.03F, 1F, 1F, blue, 1F, h * 0.4F);
                ParticleHelper.spawnGradientParticle(entity, 1,
                        h * 1.2F, 0.85F, 0.08F, 8F, 1F, 1F, blue, 0.7F);
            }
            case TENRYUU_SWEEP -> ParticleHelper.spawnSweepParticle(entity, 0,
                    h, h * 5.6F, h * 2F, 0.95F, 4F, 1F, 1F, 0.9F, 1F);
            case TATSUTA_SWEEP -> ParticleHelper.spawnSweepParticle(entity, 0,
                    h * 0.1F, h * 5.6F, h * 6F, 0.95F, 4F, 1F, 0.85F, 1F, 1F);
            case SPIN_RING -> {
                ParticleHelper.spawnGradientParticle(entity, 2,
                        h * 1.5F, 0.7F, 0.4F, 0F, 1F, 0.7F, 1F, 0.8F, 40F, 1.6F);
                ParticleHelper.spawnGradientParticle(entity, 2,
                        h * 1.3F, 0.7F, 0.3F, 0F, 1F, 1F, 1F, 1F, 40F, 1.5F);
            }
            case TENRYUU_TRAIL -> lines(level, x, y, z, vx, vy, vz, new float[][]{
                    {2.5F, 8F, 22F, 1F, 1F, 0.4F, 0.8F},
                    {1F, 8F, 20F, 1F, 1F, 0.7F, 0.9F}, {0.8F, 7F, 18F, 1F, 1F, 1F, 1F}});
            case TATSUTA_TRAIL -> lines(level, x, y, z, vx, vy, vz, new float[][]{
                    {0.6F, 7F, 7F, 1F, 0.6F, 1F, 0.3F},
                    {0.3F, 4F, 4F, 1F, 0.8F, 1F, 0.8F}, {0.2F, 3F, 3F, 1F, 1F, 1F, 1F}});
            case GAE_BOLG_TRAIL -> lines(level, x, y, z, vx, vy, vz, new float[][]{
                    {2.4F, 8F, 22F, 1F, 0F, 0F, 0.4F},
                    {0.24F, 8F, 20F, 1F, 0F, 1F, 0.85F}, {0.2F, 7F, 18F, 1F, 1F, 1F, 1F}});
            case PUNCH_INWARD, PUNCH_OUTWARD -> {
                boolean inward = effect == SkillVisualEffect.PUNCH_INWARD;
                for (int i = 0; i < 20; i++) {
                    double angle = 6.28F / 20F * i;
                    double dx = Math.cos(angle) * vx;
                    double dz = Math.sin(angle) * vx;
                    spray(level, inward ? x + dx : x, y + vy, inward ? z + dz : z,
                            inward ? -dx * 0.06D : dx, 0D, inward ? -dz * 0.06D : dz, inward ? 5 : 6);
                }
            }
            case PUNCH_FINISH -> {
                for (int i = 0; i < 3; i++) {
                    double dy = i * 0.4D;
                    ParticleHelper.spawnLaserParticle(level, x, y + dy, z, vx, vy + dy, vz, 4F, 1);
                }
                for (int i = 0; i < 20; i++) {
                    double angle = 6.28F / 20F * i;
                    spray(level, vx, vy + 0.3D, vz, Math.cos(angle) * 0.35D, 0D,
                            Math.sin(angle) * 0.35D, 0);
                }
                Minecraft.getInstance().particleEngine.add(new Particle91Type(level, vx, vy + 3D, vz, 0.6F));
            }
            case NAGATO_SMOKE -> smoke(level, entity, x, y, z, vx, vy, vz);
            case MELEE_HIT -> level.addParticle(ParticleTypes.EXPLOSION, x, y + 2D, z, 0D, 0D, 0D);
            case LIGHT_HIT -> {
                level.addParticle(ParticleTypes.EXPLOSION, x, y + 1.5D, z, 0D, 0D, 0D);
                for (int i = 0; i < 15; i++) {
                    level.addParticle(ParticleTypes.LAVA, x + level.random.nextFloat() * 3F - 1.5F,
                            y + 1D, z + level.random.nextFloat() * 3F - 1.5F, 0D, 0D, 0D);
                }
            }
        }
    }

    private static void lines(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              float[][] styles) {
        for (float[] style : styles) {
            float[] parameters = new float[13];
            System.arraycopy(style, 0, parameters, 0, 7);
            parameters[7] = (float) x;
            parameters[8] = (float) y;
            parameters[9] = (float) z;
            parameters[10] = (float) vx;
            parameters[11] = (float) vy;
            parameters[12] = (float) vz;
            ParticleHelper.spawnLineParticle(level, 0, parameters);
        }
    }

    private static void spray(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int type) {
        Minecraft.getInstance().particleEngine.add(new ParticleSpray(level, x, y, z, vx, vy, vz, type));
    }

    private static void smoke(ClientLevel level, Entity entity, double x, double y, double z,
                               double width, double forward, double height) {
        double yaw = Math.toRadians(entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot());
        for (int i = 0; i < 24; i++) {
            double r1 = level.random.nextFloat() * 0.5F - 0.25F;
            double r2 = level.random.nextFloat() * 0.5F - 0.25F;
            double r3 = level.random.nextFloat() * 0.5F - 0.25F;
            double speed = level.random.nextFloat();
            for (int j = 0; j < 6; j++) {
                double side = j < 3 ? width : -width;
                double ox = side * Math.cos(yaw) - forward * Math.sin(yaw);
                double oz = side * Math.sin(yaw) + forward * Math.cos(yaw);
                double a = j % 3 == 0 ? r1 : j % 3 == 1 ? r2 : r3;
                double b = j % 3 == 0 ? r2 : j % 3 == 1 ? r3 : r1;
                double c = j % 3 == 0 ? r3 : j % 3 == 1 ? r1 : r2;
                level.addParticle(ParticleTypes.LARGE_SMOKE, x + ox + a, y + height + b, z + oz + c,
                        -Math.sin(yaw) * 0.25D * speed, 0.05D * speed, Math.cos(yaw) * 0.25D * speed);
            }
        }
    }
}
