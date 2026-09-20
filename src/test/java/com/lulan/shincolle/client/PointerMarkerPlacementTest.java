package com.lulan.shincolle.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointerMarkerPlacementTest {

    @Test
    void markerOriginCentersHorizontallyWithoutRaisingBlockY() {
        Vec3 origin = PointerInputHandler.waypointMarkerOrigin(new BlockPos(10, 64, 20));

        assertEquals(10.5D, origin.x);
        assertEquals(64D, origin.y);
        assertEquals(20.5D, origin.z);
    }

    @Test
    void markerOriginPreservesNegativeCoordinatesAndNegativeY() {
        Vec3 origin = PointerInputHandler.waypointMarkerOrigin(new BlockPos(-10, -32, -4));

        assertEquals(-9.5D, origin.x);
        assertEquals(-32D, origin.y);
        assertEquals(-3.5D, origin.z);
    }
}
