package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Position-based weapon, environment, and status visuals. */
@OnlyIn(Dist.CLIENT)
public final class PositionParticleVisuals {

    private PositionParticleVisuals() {}

    public static void spawn(ClientLevel level, double x, double y, double z,
                             double lookX, double lookY, double lookZ, int type) {
        if (level == null || Minecraft.getInstance().level != level) return;

        switch (type) {
            case 1 -> level.addParticle(ParticleTypes.EXPLOSION, x, y + 2, z, 0, 0, 0);
            case 2 -> {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 0, 0, 0);
                for (int i = 0; i < 24; i++) {
                    double r1 = level.random.nextFloat() * 6F - 3F;
                    double r2 = level.random.nextFloat() * 6F - 3F;
                    level.addParticle(ParticleTypes.LAVA, x + r1, y + 1, z + r2, 0, 0, 0);
                }
            }
            case 3 -> {
                for (int i = 0; i < 7; i++) {
                    double vx = level.random.nextGaussian() * 0.02D;
                    double vy = level.random.nextGaussian() * 0.02D;
                    double vz = level.random.nextGaussian() * 0.02D;
                    level.addParticle(ParticleTypes.HEART,
                            x + level.random.nextFloat() * 2D - 1D,
                            y + 0.5D + level.random.nextFloat() * 2D,
                            z + level.random.nextFloat() * 2F - 1D, vx, vy, vz);
                }
            }
            case 4, 5, 7 -> {
                int count = type == 7 ? 4 : 3;
                for (int i = 0; i < count; i++) {
                    double r1 = level.random.nextFloat() * lookX - lookX / 2D;
                    double r2 = level.random.nextFloat() * lookX - lookX / 2D;
                    double r3 = level.random.nextFloat() * lookX - lookX / 2D;
                    level.addParticle(type == 7 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                            x + r1, y + r2, z + r3, 0, type == 7 ? 0 : lookY, 0);
                    if (type != 4) {
                        level.addParticle(ParticleTypes.FLAME, x + r3, y + r2, z + r1,
                                0, type == 7 ? 0.05D : lookY, 0);
                    }
                }
            }
            case 6, 31 -> {
                int count = type == 6 ? 24 : 22;
                for (int i = 0; i < count; i++) {
                    double r1 = level.random.nextFloat() - 0.5F;
                    double r2 = level.random.nextFloat();
                    double r3 = level.random.nextFloat();
                    double px = x + lookX - 0.5D + 0.05D * i;
                    double pz = z + lookZ - 0.5D + 0.05D * i;
                    level.addParticle(type == 6 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.POOF,
                            px, y + (type == 6 ? 0.6D : 0.7D) + r1, pz,
                            lookX * 0.3D * r2, 0.05D * r2, lookZ * 0.3D * r2);
                    level.addParticle(type == 6 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.POOF,
                            px, y + (type == 6 ? 1D : 0.9D) + r1, pz,
                            lookX * 0.3D * r3, 0.05D * r3, lookZ * 0.3D * r3);
                }
            }
            case 8 -> {
                level.addParticle(ParticleTypes.FLAME, x, y - 0.1D, z, 0, 0, 0);
                level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
                level.addParticle(ParticleTypes.FLAME, x, y + 0.1D, z, 0, 0, 0);
            }
            case 9 -> {
                level.addParticle(ParticleTypes.EXPLOSION, x, y + 1.5D, z, 0, 0, 0);
                for (int i = 0; i < 15; i++) {
                    double r1 = level.random.nextFloat() * 3F - 1.5F;
                    double r2 = level.random.nextFloat() * 3F - 1.5F;
                    level.addParticle(ParticleTypes.LAVA, x + r1, y + 1, z + r2, 0, 0, 0);
                }
            }
            case 10, 11, 12, 13, 34 -> add(new ParticleTexts(level, x, y + lookY, z,
                    1F, type == 34 ? 4 : type - 10));
            case 14 -> add(new ParticleLaser(level, x, y, z, lookX, lookY, lookZ, 1F, 0));
            case 15, 16, 17, 18 -> spray(level, x, y, z, lookX, lookY, lookZ, type - 14);
            case 19 -> {
                float yaw = CalcHelper.getLookDegree(lookX, 0, lookZ, false)[0];
                float[] offset1 = CalcHelper.rotateXZByAxis(0, (float) lookY, yaw, 1F);
                float[] offset2 = CalcHelper.rotateXZByAxis(0, (float) -lookY, yaw, 1F);
                for (int i = 0; i < 15; i++) {
                    double r1 = level.random.nextFloat() - 0.5F;
                    double r2 = level.random.nextFloat();
                    double r3 = level.random.nextFloat();
                    double px = x + lookX - 0.5D + 0.05D * i;
                    double pz = z + lookZ - 0.5D + 0.05D * i;
                    level.addParticle(ParticleTypes.LARGE_SMOKE, px + offset1[1], y + 0.6D + r1,
                            pz + offset1[0], lookX * 0.3D * r2, 0.05D * r2, lookZ * 0.3D * r2);
                    level.addParticle(ParticleTypes.LARGE_SMOKE, px + offset2[1], y + 0.6D + r1,
                            pz + offset2[0], lookX * 0.3D * r3, 0.05D * r3, lookZ * 0.3D * r3);
                    level.addParticle(ParticleTypes.LARGE_SMOKE, px + offset1[1], y + 0.9D + r1,
                            pz + offset1[0], lookX * 0.3D * r3, 0.05D * r3, lookZ * 0.3D * r3);
                    level.addParticle(ParticleTypes.LARGE_SMOKE, px + offset2[1], y + 0.9D + r1,
                            pz + offset2[0], lookX * 0.3D * r2, 0.05D * r2, lookZ * 0.3D * r2);
                }
            }
            case 20 -> {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.SMOKE, x, y + i * 0.1D, z, lookX, lookY, lookZ);
                }
            }
            case 21 -> {
                add(new ParticleLaser(level, x, y, z, lookX, lookY, lookZ, 4F, 1));
                add(new ParticleLaser(level, x, y + 0.4D, z, lookX, lookY + 0.4D, lookZ, 4F, 1));
                add(new ParticleLaser(level, x, y + 0.8D, z, lookX, lookY + 0.8D, lookZ, 4F, 1));
                for (int i = 0; i < 20; i++) {
                    float[] offset = CalcHelper.rotateXZByAxis(1, 0, 6.28F / 20F * i, 1);
                    spray(level, lookX, lookY + 0.3D, lookZ, offset[0] * 0.35D, 0, offset[1] * 0.35D, 0);
                }
                add(new Particle91Type(level, lookX, lookY + 3D, lookZ, 0.6F));
            }
            case 22, 23 -> {
                for (int i = 0; i < 20; i++) {
                    float[] offset = CalcHelper.rotateXZByAxis((float) lookX, 0, 6.28F / 20F * i, 1);
                    if (type == 22) {
                        spray(level, x + offset[0], y + lookY, z + offset[1],
                                -offset[0] * 0.06D, 0, -offset[1] * 0.06D, 5);
                    } else {
                        spray(level, x, y + lookY, z, offset[0], 0, offset[1], 6);
                    }
                }
            }
            case 25 -> ParticleHelper.spawnTeamCircleAt(level, x, y, z, (int) lookY);
            case 28, 49 -> {
                double r1 = level.random.nextFloat() * 0.7D - 0.35D;
                double r2 = level.random.nextFloat() * 0.7D - 0.35D;
                level.addParticle(type == 28 ? ParticleTypes.DRIPPING_WATER : ParticleTypes.DRIPPING_LAVA,
                        x + r1, y, z + r2, lookX, lookY, lookZ);
            }
            case 29 -> spray(level, x, y, z, lookX, lookY, lookZ, 9);
            case 30 -> {
                for (int i = 0; i < 15; i++) {
                    double r1 = level.random.nextFloat() * 2F - 1F;
                    double r2 = level.random.nextFloat() * 2F - 1F;
                    double r3 = level.random.nextFloat() * 2F - 1F;
                    level.addParticle(ParticleTypes.ITEM_SNOWBALL, x + r1, y + 0.8D + r2, z + r3,
                            lookX * 0.2D, 0.5D, lookZ * 0.2D);
                }
            }
            case 36 -> add(new ParticleEmotion(level, null, x, y, z,
                    (float) lookX, (int) lookY, (int) lookZ));
            case 37, 38, 39 -> spray(level, x, y, z, lookX, lookY, lookZ, type - 25);
            case 40 -> add(new ParticleCraning(level, x, y, z, lookX, lookY, lookZ, 0));
            case 43 -> {
                for (int i = 0; i < 3; i++) {
                    ParticleSmoke.spawn(level, x, y + i * 0.1D, z, 0, lookY, 0, (float) lookX);
                }
            }
            case 44 -> {
                line(level, x, y, z, lookX, lookY, lookZ, 2.5F, 8F, 22F, 1F, 1F, 0.4F, 0.8F);
                line(level, x, y, z, lookX, lookY, lookZ, 1F, 8F, 20F, 1F, 1F, 0.7F, 0.9F);
                line(level, x, y, z, lookX, lookY, lookZ, 0.8F, 7F, 18F, 1F, 1F, 1F, 1F);
            }
            case 45 -> {
                line(level, x, y, z, lookX, lookY, lookZ, 2.4F, 8F, 22F, 1F, 0F, 0F, 0.4F);
                line(level, x, y, z, lookX, lookY, lookZ, 0.24F, 8F, 20F, 1F, 0F, 1F, 0.85F);
                line(level, x, y, z, lookX, lookY, lookZ, 0.2F, 7F, 18F, 1F, 1F, 1F, 1F);
            }
            case 46 -> {
                line(level, x, y, z, lookX, lookY, lookZ, 0.6F, 7F, 7F, 1F, 0.6F, 1F, 0.3F);
                line(level, x, y, z, lookX, lookY, lookZ, 0.3F, 4F, 4F, 1F, 0.8F, 1F, 0.8F);
                line(level, x, y, z, lookX, lookY, lookZ, 0.2F, 3F, 3F, 1F, 1F, 1F, 1F);
            }
            case 47 -> {
                int count = (int) ((3 - Minecraft.getInstance().options.particles().get().getId()) * 1.8F);
                for (int i = 0; i < count; i++) spray(level, x, y, z, lookX, lookY, lookZ, 16);
            }
            case 48 -> {
                for (int i = 0; i < 14; i++) {
                    double r1 = (level.random.nextFloat() - 0.5F) * lookY;
                    double r2 = (level.random.nextFloat() - 0.5F) * lookX;
                    double r3 = (level.random.nextFloat() - 0.5F) * lookZ;
                    level.addParticle(ParticleTypes.BUBBLE, x + r2, y + r1, z + r3, 0, 0, 0);
                    level.addParticle(ParticleTypes.FISHING, x + r2, y + r1, z + r3, 0, 0, 0);
                }
            }
            default -> { }
        }
    }

    public static void spray(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, int variant) {
        if (level == null || Minecraft.getInstance().level != level) return;
        add(new ParticleSpray(level, x, y, z, vx, vy, vz, variant));
    }

    private static void line(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, float height, float forward, float back,
                             float red, float green, float blue, float alpha) {
        add(new ParticleLine(level, 0, new float[] {height, forward, back, red, green, blue, alpha,
                (float) x, (float) y, (float) z, (float) vx, (float) vy, (float) vz}));
    }

    private static void add(Particle particle) {
        Minecraft.getInstance().particleEngine.add(particle);
    }
}
