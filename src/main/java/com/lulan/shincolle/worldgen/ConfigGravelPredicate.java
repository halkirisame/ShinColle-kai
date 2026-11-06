package com.lulan.shincolle.worldgen;

import com.lulan.shincolle.handler.ConfigHandler;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;

/** Exact replacement blocks for the polymetal gravel disk; unrelated stone variants stay untouched. */
public final class ConfigGravelPredicate implements BlockPredicate {
    public static final Codec<ConfigGravelPredicate> CODEC = Codec.unit(new ConfigGravelPredicate());

    private ConfigGravelPredicate() { }

    @Override
    public boolean test(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        boolean[] enabled = ConfigHandler.polyGravelBaseBlock;
        return (enabled[0] && state.is(Blocks.STONE))
                || (enabled[1] && state.is(Blocks.GRAVEL))
                || (enabled[2] && state.is(Blocks.SAND))
                || (enabled[3] && state.is(Blocks.DIRT));
    }

    @Override
    public BlockPredicateType<?> type() {
        return ModWorldGen.CONFIG_GRAVEL_TARGET.get();
    }
}
