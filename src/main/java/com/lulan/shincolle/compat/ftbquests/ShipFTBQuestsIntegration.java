package com.lulan.shincolle.compat.ftbquests;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayer;

/** Optional quest accounting, loaded only after the caller checks for FTB Quests. */
public final class ShipFTBQuestsIntegration {
    private ShipFTBQuestsIntegration() { }

    public static void creditKill(ServerPlayer owner, LivingEntity victim) {
        if (owner instanceof FakePlayer) return;
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return;
        TeamData data = file.getOrCreateTeamData(owner);
        if (data == null || data.isLocked()) return;
        for (KillTask task : file.collect(KillTask.class)) {
            if (data.getProgress(task) < task.getMaxProgress() && data.canStartTasks(task.getQuest())) {
                task.kill(data, victim);
            }
        }
    }
}
