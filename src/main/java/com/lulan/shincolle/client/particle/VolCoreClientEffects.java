package com.lulan.shincolle.client.particle;

import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VolCoreClientEffects {

    private VolCoreClientEffects() {
    }

    public static void spawnParticles(Level level, BlockPos pos) {
        int particleSetting = Minecraft.getInstance().options.particles().get().getId();
        int count = (3 - particleSetting) * 25;

        for (int i = 0; i < count; i++) {
            ParticleHelper.spawnAttackParticleAt(level,
                    pos.getX() + 0.5D + level.random.nextDouble() * 13.0D - 6.5D,
                    pos.getY() + 1.5D + level.random.nextDouble() * 13.0D - 4.5D,
                    pos.getZ() + 0.5D + level.random.nextDouble() * 13.0D - 6.5D,
                    0.0D, 0.05D, 0.0D, 37);
        }
    }
}
