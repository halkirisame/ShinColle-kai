package com.lulan.shincolle.client.sound;

import com.lulan.shincolle.handler.ShipSoundClientConfig;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShipSoundChannelTest {

    @Test
    void clientSoundGroupsDefaultOffButRememberFullVolume() {
        assertFalse(ShipSoundClientConfig.CLIENT.shipVoiceEnabled.getDefault());
        assertFalse(ShipSoundClientConfig.CLIENT.timekeepingEnabled.getDefault());
        assertFalse(ShipSoundClientConfig.CLIENT.hostileShipVoiceEnabled.getDefault());
        assertEquals(1.0D, ShipSoundClientConfig.CLIENT.shipVoiceVolume.getDefault());
        assertEquals(1.0D, ShipSoundClientConfig.CLIENT.timekeepingVolume.getDefault());
        assertEquals(1.0D, ShipSoundClientConfig.CLIENT.hostileShipVoiceVolume.getDefault());
    }

    @Test
    void classifiesRegularAndCustomFriendlyShipVoices() {
        assertEquals(ShipSoundChannel.VOICE, classify("ship_idle"));
        assertEquals(ShipSoundChannel.VOICE, classify("ship_marry_54"));
        assertEquals(ShipSoundChannel.VOICE, classify("ship_item_56"));
    }

    @Test
    void classifiesOnlyValidTimekeepingHours() {
        assertEquals(ShipSoundChannel.TIMEKEEPING, classify("ship_time0"));
        assertEquals(ShipSoundChannel.TIMEKEEPING, classify("ship_time23"));
        assertNull(classify("ship_time24"));
        assertNull(classify("ship_time"));
    }

    @Test
    void leavesAttackEffectsAndOtherNamespacesUntouched() {
        assertNull(classify("ship_fireheavy"));
        assertNull(classify("ship_hitmetal"));
        assertNull(ShipSoundChannel.classify(new ResourceLocation("example", "ship_idle")));
    }

    @Test
    void classifiesHostileHurtAndDeathVoicesBySource() {
        assertEquals(ShipSoundChannel.HOSTILE_VOICE,
                classify("ship_hurt", ShipSoundChannel.Source.HOSTILE_SHIP));
        assertEquals(ShipSoundChannel.HOSTILE_VOICE,
                classify("ship_death", ShipSoundChannel.Source.HOSTILE_SHIP));
        assertEquals(ShipSoundChannel.VOICE,
                classify("ship_hurt", ShipSoundChannel.Source.FRIENDLY_SHIP));
        assertEquals(ShipSoundChannel.VOICE,
                classify("ship_death", ShipSoundChannel.Source.FRIENDLY_SHIP));
    }

    @Test
    void leavesHostileEffectsAndOtherSourcesUntouched() {
        assertNull(classify("ship_fireheavy", ShipSoundChannel.Source.HOSTILE_SHIP));
        assertNull(classify("ship_hurt", ShipSoundChannel.Source.OTHER));
    }

    private static ShipSoundChannel classify(String path) {
        return ShipSoundChannel.classify(new ResourceLocation(Reference.MOD_ID, path));
    }

    private static ShipSoundChannel classify(String path, ShipSoundChannel.Source source) {
        return ShipSoundChannel.classify(new ResourceLocation(Reference.MOD_ID, path), source);
    }
}
