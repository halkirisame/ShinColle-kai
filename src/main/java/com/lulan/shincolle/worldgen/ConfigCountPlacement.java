package com.lulan.shincolle.worldgen;

import com.lulan.shincolle.handler.ConfigHandler;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/** Reads cluster counts when a chunk is generated, rather than when its datapack is loaded. */
public final class ConfigCountPlacement extends RepeatingPlacement {
    public static final Codec<ConfigCountPlacement> CODEC = Codec.BOOL.fieldOf("gravel")
            .xmap(ConfigCountPlacement::new, placement -> placement.gravel).codec();

    private final boolean gravel;

    private ConfigCountPlacement(boolean gravel) {
        this.gravel = gravel;
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        return gravel ? ConfigHandler.polymetalGravelClusters() : ConfigHandler.polymetalOreClusters();
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModWorldGen.CONFIG_COUNT.get();
    }
}
