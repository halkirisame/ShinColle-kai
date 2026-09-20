package com.lulan.shincolle.client;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.handler.UpdateNotificationConfig;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;

/** Shows the bounded update notice when the client joins a world. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpdateNotificationHandler {

    private static final String STATE_FILE = Reference.MOD_ID + "-update-notice.json";
    private static final String AVAILABLE_KEY = "chat.shincolle_kai.update.available";
    private static final String LINK_KEY = "chat.shincolle_kai.update.link";
    private static boolean checkedThisProcess;

    private UpdateNotificationHandler() {
    }

    /** Evaluates Forge's asynchronous result once it has left PENDING. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        boolean enabled = UpdateNotificationConfig.CLIENT.enabled.get();
        if (checkedThisProcess || !enabled) {
            return;
        }

        IModInfo modInfo = ModList.get().getModContainerById(Reference.MOD_ID)
                .map(container -> container.getModInfo())
                .orElse(null);
        if (modInfo == null) {
            return;
        }

        VersionChecker.CheckResult result = VersionChecker.getResult(modInfo);
        if (result.status() == VersionChecker.Status.PENDING) {
            return;
        }
        checkedThisProcess = true;

        boolean notificationTarget = UpdateStatusAdapter.isNotificationTarget(result.status());
        if (!notificationTarget || result.target() == null) {
            return;
        }

        Path statePath = FMLPaths.CONFIGDIR.get().resolve(STATE_FILE);
        UpdateNoticeState savedState = UpdateNoticeStateStore.read(statePath).orElse(null);
        UpdateNotificationPolicy.Decision decision = UpdateNotificationPolicy.evaluate(enabled,
                UpdateNotificationConfig.CLIENT.notifyOnLaunches.get(), savedState,
                result.target().toString(), notificationTarget);
        if (!UpdateNoticeStateStore.write(statePath, decision.state())) {
            ShinColle.LOGGER.debug("Could not save update notification state at {}", statePath);
        }
        if (decision.shouldNotify()) {
            event.getPlayer().displayClientMessage(buildMessage(modInfo, result), false);
        }
    }

    private static Component buildMessage(IModInfo modInfo, VersionChecker.CheckResult result) {
        MutableComponent message = Component.translatable(AVAILABLE_KEY,
                modInfo.getVersion().toString(), result.target().toString());
        if (UpdateNotificationLinks.isValidDownloadUrl(result.url())) {
            Component link = Component.translatable(LINK_KEY).withStyle(style -> style
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, result.url())));
            message.append(link);
        }
        return message;
    }
}
