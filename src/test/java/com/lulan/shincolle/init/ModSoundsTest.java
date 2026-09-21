package com.lulan.shincolle.init;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModSoundsTest {

    private static final Set<Integer> CUSTOM_VOICE_IDS = Set.of(54, 56, 60, 62);

    @Test
    void mapsShipClassesToSpawnEggVoiceIds() {
        assertEquals(5400, ModSounds.CustomSoundKey.forShipClass(0, 52));
        assertEquals(5600, ModSounds.CustomSoundKey.forShipClass(0, 54));
        assertEquals(6000, ModSounds.CustomSoundKey.forShipClass(0, 58));
        assertEquals(6201, ModSounds.CustomSoundKey.forShipClass(1, 60));
    }

    @Test
    void tenryuHasNoCustomVoiceAndFallsBack() {
        int voiceId = ModSounds.CustomSoundKey.voiceId(56);

        assertEquals(58, voiceId);
        assertEquals(5800, ModSounds.CustomSoundKey.forShipClass(0, 56));
        assertFalse(CUSTOM_VOICE_IDS.contains(voiceId));
    }
}
