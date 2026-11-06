package com.lulan.shincolle.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class BlockPolymetalOre extends BasicBlock {

    public BlockPolymetalOre() {
        super(Properties.of().mapColor(MapColor.STONE).strength(3.0F, 3.0F).requiresCorrectToolForDrops());
    }

    @Override
    public int getExpDrop(BlockState state, LevelReader level, RandomSource randomSource, BlockPos pos,
                          int fortuneLevel, int silkTouchLevel) {
        return silkTouchLevel > 0 ? 0 : (randomSource.nextInt(4) + 1) * (fortuneLevel + 1);
    }
}
