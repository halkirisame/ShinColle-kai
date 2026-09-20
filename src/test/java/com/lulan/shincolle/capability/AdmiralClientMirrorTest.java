package com.lulan.shincolle.capability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdmiralClientMirrorTest {

    @Test
    void nullInputsNormalizeToEmptyValues() {
        AdmiralClientMirror mirror = new AdmiralClientMirror();

        mirror.setAllyList(null);
        mirror.setTeamName(null);
        mirror.setEntityItemList(null);

        assertEquals(List.of(), mirror.getAllyList());
        assertEquals("", mirror.getTeamName());
        assertArrayEquals(new float[0], mirror.getEntityItemList());
    }

    @Test
    void settersDefensivelyCopyInputs() {
        AdmiralClientMirror mirror = new AdmiralClientMirror();
        List<Integer> allies = new ArrayList<>(List.of(3));
        float[] items = {1F};

        mirror.setAllyList(allies);
        mirror.setEntityItemList(items);
        allies.add(4);
        items[0] = 9F;

        assertEquals(List.of(3), mirror.getAllyList());
        assertArrayEquals(new float[]{1F}, mirror.getEntityItemList());
    }

    @Test
    void gettersExposeImmutableViewsAndArraySnapshots() {
        AdmiralClientMirror mirror = new AdmiralClientMirror();
        mirror.setAllyList(List.of(3));
        mirror.setEntityItemList(new float[]{1F});

        List<Integer> allies = mirror.getAllyList();
        float[] items = mirror.getEntityItemList();
        assertThrows(UnsupportedOperationException.class, () -> allies.add(4));
        items[0] = 9F;

        assertEquals(List.of(3), mirror.getAllyList());
        assertArrayEquals(new float[]{1F}, mirror.getEntityItemList());
    }

    @Test
    void targetClassNamesRefreshTargetClassHashes() {
        AdmiralClientMirror mirror = new AdmiralClientMirror();

        mirror.setTargetClassNames(List.of("ship", "submarine"));

        assertEquals(List.of("ship".hashCode(), "submarine".hashCode()), mirror.getTargetClassList());
    }
}
