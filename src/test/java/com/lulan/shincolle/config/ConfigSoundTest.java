package com.lulan.shincolle.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSoundTest {

    @Test
    void retainsEveryDefaultVoiceRate() {
        int[] voiceIds = {54, 56, 60, 62};
        float[][] expected = {
                {0.25F, 0F, 0.25F, 0F, 0.50F, 0F, 0.50F, 0F, 0F},
                {0.50F, 0.50F, 0.50F, 1F, 0F, 0F, 0.50F, 0F, 0F},
                {0.25F, 0.50F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
                {0F, 0.35F, 0F, 0F, 0F, 0F, 0F, 0F, 0F}
        };
        assertEquals(Set.of(54, 56, 60, 62), ConfigSound.SOUND_RATE.keySet());
        for (int voice = 0; voice < voiceIds.length; voice++) {
            assertEquals(9, ConfigSound.SOUND_RATE.get(voiceIds[voice]).length);
            for (int type = 0; type < 9; type++) {
                assertEquals(expected[voice][type], ConfigSound.getSoundRate(voiceIds[voice], type));
            }
        }
    }

    @Test
    void usesVoiceIdsRatherThanShipClasses() {
        assertEquals(0F, ConfigSound.getSoundRate(52, 0));
        assertEquals(0.25F, ConfigSound.getSoundRate(54, 0));
        assertEquals(0.50F, ConfigSound.getSoundRate(56, 0));
        assertEquals(0F, ConfigSound.getSoundRate(58, 0));
    }

    @Test
    void unknownVoicesAndTypesHaveNoCustomRate() {
        assertEquals(0F, ConfigSound.getSoundRate(999, 0));
        assertEquals(0F, ConfigSound.getSoundRate(56, -1));
        assertEquals(0F, ConfigSound.getSoundRate(56, 9));
        assertEquals(0F, ConfigSound.getSoundRate(56, 33));
    }
}
