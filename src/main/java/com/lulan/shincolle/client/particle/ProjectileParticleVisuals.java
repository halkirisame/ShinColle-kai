package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Visual effects that follow a projectile's existing lifecycle. */
@OnlyIn(Dist.CLIENT)
public final class ProjectileParticleVisuals {
    private ProjectileParticleVisuals() {
    }

    public static void airplaneDeath(BasicEntityAirplane airplane) {
        if (!airplane.level().isClientSide()) {
            return;
        }
        if (airplane.deathTime >= 19) {
            // final burst: lava sparks and explosions around the wreck
            for (int i = 0; i < 12; i++) {
                double ran1 = airplane.getBbWidth() * (airplane.getRandom().nextFloat() - 0.5F);
                double ran2 = airplane.getBbWidth() * (airplane.getRandom().nextFloat() - 0.5F);
                airplane.level().addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
                        airplane.getX() + ran1, airplane.getY() + airplane.getBbHeight() * 0.3D,
                        airplane.getZ() + ran2, 0D, 0D, 0D);
                if ((i & 3) == 0) {
                    airplane.level().addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                            airplane.getX() + ran2, airplane.getY() + airplane.getBbHeight() * 0.5D,
                            airplane.getZ() + ran1, 0D, 0D, 0D);
                }
            }
        }
        if ((airplane.tickCount & 1) != 0) {
            return;
        }
        int count = (int) ((3 - Minecraft.getInstance().options.particles().get().getId()) * 1.8F);
        double range = airplane.getBbWidth() * 0.5D;
        for (int i = 0; i < count; i++) {
            ParticleHelper.spawnAttackParticleAt(airplane.level(),
                    airplane.getX() - range + airplane.getRandom().nextDouble() * range * 2D,
                    airplane.getY() + airplane.getBbHeight() * 0.3D + airplane.getRandom().nextDouble() * 0.3D,
                    airplane.getZ() - range + airplane.getRandom().nextDouble() * range * 2D,
                    1.5D, 0D, 0D, 43);
        }
    }
}
