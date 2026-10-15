package com.lulan.shincolle.entity.battleship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamatoAimTest {
    @Test
    void yawFacesTheOffsetUsingMinecraftConvention() {
        assertEquals(0F, YamatoAim.yawToward(0D, 5D), 1E-4F);
        assertEquals(90F, YamatoAim.yawToward(-5D, 0D), 1E-4F);
        assertEquals(-90F, YamatoAim.yawToward(5D, 0D), 1E-4F);
        assertEquals(180F, Math.abs(YamatoAim.yawToward(0D, -5D)), 1E-4F);
    }

    @Test
    void steppedYawTurnsTheShortWayAndStopsAtTheTarget() {
        assertEquals(30F, YamatoAim.steppedYaw(0F, 170F, 30F), 1E-4F);
        assertEquals(190F, YamatoAim.steppedYaw(170F, -170F, 30F), 1E-3F);
        assertEquals(40F, YamatoAim.steppedYaw(35F, 40F, 30F), 1E-4F);
    }
}
