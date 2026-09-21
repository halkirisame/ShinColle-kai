package com.lulan.shincolle.client.sound;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Applies this client's ship sound preferences immediately before playback. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ShipSoundPlaybackHandler {

    private ShipSoundPlaybackHandler() {
    }

    @SubscribeEvent
    public static void onPlayAtEntity(PlayLevelSoundEvent.AtEntity event) {
        if (!event.getEntity().level().isClientSide()) {
            return;
        }
        ShipSoundChannel.Source source;
        if (event.getEntity() instanceof BasicEntityShip) {
            source = ShipSoundChannel.Source.FRIENDLY_SHIP;
        } else if (event.getEntity() instanceof BasicEntityShipHostile) {
            source = ShipSoundChannel.Source.HOSTILE_SHIP;
        } else {
            return;
        }
        Holder<SoundEvent> holder = event.getSound();
        if (holder == null) {
            return;
        }
        ResourceLocation soundId = ForgeRegistries.SOUND_EVENTS.getKey(holder.value());
        ShipSoundChannel channel = ShipSoundChannel.classify(soundId, source);
        if (channel == null) {
            return;
        }
        float adjustedVolume = channel.adjustVolume(event.getNewVolume());
        if (adjustedVolume <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setNewVolume(adjustedVolume);
        }
    }
}
