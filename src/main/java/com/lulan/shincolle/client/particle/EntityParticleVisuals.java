package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.network.ParticlePacketRoute;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Entity-attached visuals keep their arguments out of the position ID space. */
public final class EntityParticleVisuals {
    private EntityParticleVisuals() { }

    public static void show(ParticlePacketRoute route, Entity source, Entity target, double a, double b, double c,
                            boolean boss) {
        if (source == null || !source.level().isClientSide()) return;
        if (route == ParticlePacketRoute.FLUID_FILL || route == ParticlePacketRoute.CRANE_TRANSFER) {
            boolean fluid = route == ParticlePacketRoute.FLUID_FILL;
            ParticleHelper.spawnSparkleParticle(source, fluid ? 0 : 3, fluid ? 0.025F : 0.05F,
                    source.getBbWidth() * (fluid ? 1.5F : 1F), 0F, 0F,
                    (float) a, (float) b, (float) c, 1F, source.getBbHeight() * 0.4F);
        } else if (route == ParticlePacketRoute.EQUIPMENT_SMOKE && source instanceof LivingEntity living) {
            smoke(living, a, b, c);
        } else if (route == ParticlePacketRoute.MOUNT_CANNON_SMOKE && source instanceof LivingEntity living) {
            mountCannonSmoke(living, a, b, c);
        } else if (route == ParticlePacketRoute.YAMATO_CHARGE && source instanceof LivingEntity living) {
            yamatoCharge(living, a, b, c);
        } else if (route == ParticlePacketRoute.YAMATO_BEAM && target != null
                && source instanceof LivingEntity living) {
            yamatoBeam(living, target, a, b, c, boss);
        } else if (target != null && source instanceof LivingEntity living) {
            ClientLevel level = (ClientLevel) source.level();
            if (route == ParticlePacketRoute.SUPPORT_LINE) {
                Minecraft.getInstance().particleEngine.add(new ParticleLaserNoTexture(level, living, target,
                        0D, 0D, 0D, 0.1F, 4));
            } else if (route == ParticlePacketRoute.PURPLE_BEAM) {
                Minecraft.getInstance().particleEngine.add(new ParticleLaserNoTexture(level, living, target,
                        a, b, c, 0F, 6));
            } else if (route == ParticlePacketRoute.DUAL_BEAM) {
                for (float side : new float[]{0.9F, -0.9F}) {
                    Minecraft.getInstance().particleEngine.add(new ParticleLaserNoTexture(level, living, target,
                            side, a, 0D, 0.05F, 0));
                }
            }
        }
    }

    /** Cannon charge: a vibrating cube on the ship and six lightning strips radiating from it. */
    private static void yamatoCharge(LivingEntity source, double a, double b, double c) {
        ClientLevel level = (ClientLevel) source.level();
        Minecraft.getInstance().particleEngine.add(new ParticleCube(level, source, a, b, c, 1.5F, 0));
        for (int i = 0; i < 6; i++) {
            Minecraft.getInstance().particleEngine.add(new ParticleStickyLightning(level, source, (float) a, 40, 3));
        }
    }

    /** Beam shot: the head cube and the beam body between the ship and the beam entity. */
    private static void yamatoBeam(LivingEntity source, Entity beam, double a, double b, double c, boolean boss) {
        ClientLevel level = (ClientLevel) source.level();
        Minecraft.getInstance().particleEngine.add(new ParticleCube(level, source, a, b, c, boss ? 5F : 2.5F, 1));
        Minecraft.getInstance().particleEngine.add(new ParticleLaserNoTexture(level, source, beam, a, b, c,
                boss ? 4F : 2F, 1));
    }

    private static void mountCannonSmoke(LivingEntity source, double width, double height, double forward) {
        ClientLevel level = (ClientLevel) source.level();
        float bodyYaw = source.yBodyRot * (float) (Math.PI / 180D);
        float[] left = CalcHelper.rotateXZByAxis((float) forward, (float) width, bodyYaw, 1F);
        float[] right = CalcHelper.rotateXZByAxis((float) forward, (float) -width, bodyYaw, 1F);
        float[] head = CalcHelper.rotateXZByAxis(1.5F, 0F,
                source.yHeadRot * (float) (Math.PI / 180D), 1F);
        for (int i = 0; i < 24; i++) {
            double r1 = level.random.nextFloat() - 0.5F;
            double r2 = level.random.nextFloat();
            double r3 = level.random.nextFloat();
            double x = source.getX() - 0.5D + 0.05D * i + head[1];
            double z = source.getZ() - 0.5D + 0.05D * i + head[0];
            level.addParticle(ParticleTypes.LARGE_SMOKE, x + left[1], source.getY() + height + 0.6D + r1,
                    z + left[0], head[1] * 0.5D * r2, 0.05D * r2, head[0] * 0.5D * r2);
            level.addParticle(ParticleTypes.LARGE_SMOKE, x + right[1], source.getY() + height + 0.6D + r1,
                    z + right[0], head[1] * 0.5D * r3, 0.05D * r3, head[0] * 0.5D * r3);
            level.addParticle(ParticleTypes.LARGE_SMOKE, x + left[1], source.getY() + height + 0.9D + r1,
                    z + left[0], head[1] * 0.5D * r3, 0.05D * r3, head[0] * 0.5D * r3);
            level.addParticle(ParticleTypes.LARGE_SMOKE, x + right[1], source.getY() + height + 0.9D + r1,
                    z + right[0], head[1] * 0.5D * r2, 0.05D * r2, head[0] * 0.5D * r2);
        }
    }

    private static void smoke(LivingEntity source, double width, double forward, double height) {
        ClientLevel level = (ClientLevel) source.level();
        float yaw = (source.yBodyRot % 360) * (float) (Math.PI / 180D);
        float[] left = CalcHelper.rotateXZByAxis((float) forward, (float) width, yaw, 1F);
        float[] right = CalcHelper.rotateXZByAxis((float) forward, (float) -width, yaw, 1F);
        float[] speed = CalcHelper.rotateXZByAxis(0.25F, 0F, yaw, 1F);
        for (int i = 0; i < 24; i++) {
            double r1 = (level.random.nextFloat() - 0.5F) * 2F;
            double r2 = (level.random.nextFloat() - 0.5F) * 2F;
            double r3 = (level.random.nextFloat() - 0.5F) * 2F;
            double r4 = level.random.nextFloat() * 2F;
            double[][] offsets = {{left[1] + r1, r2, left[0] + r3}, {right[1] + r1, r3, right[0] + r2},
                    {left[1] + r2, r1, left[0] + r3}, {right[1] + r2, r3, right[0] + r1},
                    {left[1] + r3, r1, left[0] + r2}, {right[1] + r3, r2, right[0] + r1}};
            for (double[] offset : offsets) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, source.getX() + offset[0],
                        source.getY() + height + offset[1], source.getZ() + offset[2],
                        speed[1] * r4, 0.05D * r4, speed[0] * r4);
            }
        }
    }
}
