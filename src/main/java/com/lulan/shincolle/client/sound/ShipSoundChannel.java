package com.lulan.shincolle.client.sound;

import com.lulan.shincolle.handler.ShipSoundClientConfig;
import com.lulan.shincolle.handler.ShipSoundSettings;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Extensible row and playback model for client-local ship sounds. */
public enum ShipSoundChannel {
    VOICE("gui.shincolle_kai.sound.voice", "Ship Voice"),
    TIMEKEEPING("gui.shincolle_kai.sound.timekeeping", "Timekeeping"),
    HOSTILE_VOICE("gui.shincolle_kai.sound.hostile_voice", "Enemy Voice");

    private static final Set<String> VOICE_PATHS = Set.of(
            "ship_idle", "ship_hit", "ship_hurt", "ship_death", "ship_marry",
            "ship_knockback", "ship_item", "ship_feed", "ship_levelup",
            "ship_idle_54", "ship_hurt_54", "ship_marry_54", "ship_item_54",
            "ship_idle_56", "ship_hit_56", "ship_hurt_56", "ship_death_56", "ship_item_56",
            "ship_idle_60", "ship_hit_60", "ship_hit_62");
    private static final Set<String> HOSTILE_VOICE_PATHS = Set.of("ship_hurt", "ship_death");

    private final String translationKey;
    private final String fallback;

    ShipSoundChannel(String translationKey, String fallback) {
        this.translationKey = translationKey;
        this.fallback = fallback;
    }

    public String translationKey() {
        return translationKey;
    }

    public String fallback() {
        return fallback;
    }

    public boolean enabled() {
        return switch (this) {
            case VOICE -> ShipSoundClientConfig.CLIENT.shipVoiceEnabled.get();
            case TIMEKEEPING -> ShipSoundClientConfig.CLIENT.timekeepingEnabled.get();
            case HOSTILE_VOICE -> ShipSoundClientConfig.CLIENT.hostileShipVoiceEnabled.get();
        };
    }

    public void setEnabled(boolean enabled) {
        switch (this) {
            case VOICE -> ShipSoundClientConfig.CLIENT.shipVoiceEnabled.set(enabled);
            case TIMEKEEPING -> ShipSoundClientConfig.CLIENT.timekeepingEnabled.set(enabled);
            case HOSTILE_VOICE -> ShipSoundClientConfig.CLIENT.hostileShipVoiceEnabled.set(enabled);
        }
        ShipSoundClientConfig.save();
    }

    public double volume() {
        return switch (this) {
            case VOICE -> ShipSoundClientConfig.CLIENT.shipVoiceVolume.get();
            case TIMEKEEPING -> ShipSoundClientConfig.CLIENT.timekeepingVolume.get();
            case HOSTILE_VOICE -> ShipSoundClientConfig.CLIENT.hostileShipVoiceVolume.get();
        };
    }

    public int volumePercent() {
        return ShipSoundSettings.toPercent(volume());
    }

    public void setVolumePercent(int percent) {
        double volume = ShipSoundSettings.fromPercent(percent);
        switch (this) {
            case VOICE -> ShipSoundClientConfig.CLIENT.shipVoiceVolume.set(volume);
            case TIMEKEEPING -> ShipSoundClientConfig.CLIENT.timekeepingVolume.set(volume);
            case HOSTILE_VOICE -> ShipSoundClientConfig.CLIENT.hostileShipVoiceVolume.set(volume);
        }
        ShipSoundClientConfig.save();
    }

    public float adjustVolume(float incomingVolume) {
        return ShipSoundSettings.effectiveVolume(enabled(), volume(), incomingVolume);
    }

    public static ShipSoundChannel classify(ResourceLocation soundId) {
        return classify(soundId, Source.FRIENDLY_SHIP);
    }

    static ShipSoundChannel classify(ResourceLocation soundId, Source source) {
        if (soundId == null || source == null || !Reference.MOD_ID.equals(soundId.getNamespace())) {
            return null;
        }
        String path = soundId.getPath();
        if (source == Source.HOSTILE_SHIP) {
            return HOSTILE_VOICE_PATHS.contains(path) ? HOSTILE_VOICE : null;
        }
        if (source != Source.FRIENDLY_SHIP) {
            return null;
        }
        if (isTimekeepingPath(path)) {
            return TIMEKEEPING;
        }
        return VOICE_PATHS.contains(path) ? VOICE : null;
    }

    enum Source {
        FRIENDLY_SHIP,
        HOSTILE_SHIP,
        OTHER
    }

    private static boolean isTimekeepingPath(String path) {
        if (!path.startsWith("ship_time")) {
            return false;
        }
        String hourText = path.substring("ship_time".length());
        try {
            int hour = Integer.parseInt(hourText);
            return hour >= 0 && hour <= 23;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
