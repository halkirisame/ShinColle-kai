package com.lulan.shincolle.compat.curios;

import com.lulan.shincolle.init.ModBlocks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CuriosOreExperienceHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(ModBlocks.POLYMETAL_ORE.get())
                && !ForgeHooks.isCorrectToolForDrops(event.getState(), event.getPlayer())) {
            event.setExpToDrop(0);
        }
    }
}
