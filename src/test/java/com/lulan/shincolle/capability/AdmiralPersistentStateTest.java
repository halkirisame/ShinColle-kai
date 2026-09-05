package com.lulan.shincolle.capability;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AdmiralPersistentStateTest {

    @Test
    void createsLegacyDefaults() {
        AdmiralPersistentState state = new AdmiralPersistentState(37);

        assertFalse(state.hasRing());
        assertFalse(state.isRingActive());
        assertFalse(state.isRingFlying());
        assertEquals(0, state.getMarriageNum());
        assertEquals(37, state.getBossCooldown());
        assertEquals(0, state.getTeamCooldown());
        assertEquals(-1, state.getPlayerUID());
        assertEquals(0, state.getSelectTeam());
        assertEquals(0, state.getColledShipNum());
        assertEquals(0, state.getColledEquipNum());
        for (int team = 0; team < AdmiralPersistentState.TEAM_NUM; team++) {
            assertEquals(0, state.getFormatID(team));
            assertEquals("Team " + (team + 1), state.getUnitName(team));
            for (int slot = 0; slot < AdmiralPersistentState.SLOT_NUM; slot++) {
                assertEquals(-1, state.getTeamMember(team, slot));
                assertFalse(state.isShipSelected(team, slot));
            }
        }
    }

    @Test
    void instancesDoNotShareTeamRows() {
        AdmiralPersistentState first = new AdmiralPersistentState(1);
        AdmiralPersistentState second = new AdmiralPersistentState(1);

        first.setTeamMember(0, 0, 7);
        first.setShipSelected(0, 0, true);
        first.setFormatID(0, 3);
        first.setUnitName(0, "First");

        assertEquals(-1, second.getTeamMember(0, 0));
        assertFalse(second.isShipSelected(0, 0));
        assertEquals(0, second.getFormatID(0));
        assertEquals("Team 1", second.getUnitName(0));
    }

    @Test
    void outOfRangeTeamsReturnLegacySentinels() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        assertEquals(-1, state.getTeamMember(-1, 0));
        assertEquals(-1, state.getTeamMember(9, 0));
        assertEquals(0, state.getFormatID(9));
        assertEquals("", state.getUnitName(9));
        assertFalse(state.isShipSelected(9, 0));
    }

    @Test
    void outOfRangeSlotsReturnLegacySentinels() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        assertEquals(-1, state.getTeamMember(0, -1));
        assertEquals(-1, state.getTeamMember(0, 6));
    }

    @Test
    void outOfRangeSettersAreNoOps() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        state.setTeamMember(9, 0, 7);
        state.setShipSelected(9, 0, true);
        state.setFormatID(9, 4);
        state.setUnitName(9, "ignored");

        for (int team = 0; team < AdmiralPersistentState.TEAM_NUM; team++) {
            for (int slot = 0; slot < AdmiralPersistentState.SLOT_NUM; slot++) {
                assertEquals(-1, state.getTeamMember(team, slot));
                assertFalse(state.isShipSelected(team, slot));
            }
        }
    }

    @Test
    void selectedTeamIsClamped() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        state.setSelectTeam(-5);
        assertEquals(0, state.getSelectTeam());
        state.setSelectTeam(100);
        assertEquals(8, state.getSelectTeam());
        state.setSelectTeam(8);
        assertEquals(8, state.getSelectTeam());
        state.setSelectTeam(0);
        assertEquals(0, state.getSelectTeam());
    }

    @Test
    void copyFromCoversEveryDeclaredStateField() throws IllegalAccessException {
        AdmiralPersistentState source = new AdmiralPersistentState(1);
        AdmiralPersistentState target = new AdmiralPersistentState(2);
        Field[] fields = Arrays.stream(AdmiralPersistentState.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toArray(Field[]::new);
        assertEquals(14, fields.length);

        for (Field field : fields) {
            field.setAccessible(true);
            makeNonDefault(field, source);
        }

        target.copyFrom(source);

        for (Field field : fields) {
            assertCopied(field, source, target);
        }
    }

    @Test
    void persistenceSnapshotsDoNotShareOwnedRows() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);
        state.setTeamMember(0, 0, 7);
        state.setShipSelected(0, 0, true);

        int[] members = state.copyTeamMembers(0);
        byte[] selected = state.copyShipSelection(0);
        members[0] = 99;
        selected[0] = 0;

        assertEquals(7, state.getTeamMember(0, 0));
        assertTrue(state.isShipSelected(0, 0));
    }

    @Test
    void shortTeamPayloadKeepsRemainingDefaults() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        state.loadTeamMembers(0, new int[]{7, 8});

        assertEquals(7, state.getTeamMember(0, 0));
        assertEquals(8, state.getTeamMember(0, 1));
        for (int slot = 2; slot < AdmiralPersistentState.SLOT_NUM; slot++) {
            assertEquals(-1, state.getTeamMember(0, slot));
        }
    }

    @Test
    void missingSelectionMigratesOnlyFirstOccupiedSlot() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        state.migrateShipSelection(0, new int[]{-1, 5, 9, -1, 3, -1});

        for (int slot = 0; slot < AdmiralPersistentState.SLOT_NUM; slot++) {
            assertEquals(slot == 1, state.isShipSelected(0, slot));
        }
    }

    @Test
    void nullUnitNameNormalizesToEmptyString() {
        AdmiralPersistentState state = new AdmiralPersistentState(1);

        state.setUnitName(0, null);

        assertEquals("", state.getUnitName(0));
    }

    private static void makeNonDefault(Field field, AdmiralPersistentState source) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == boolean.class) {
            field.setBoolean(source, true);
        } else if (type == int.class) {
            field.setInt(source, 77);
        } else if (type == int[][].class) {
            for (int[] row : (int[][]) field.get(source)) {
                Arrays.fill(row, 77);
            }
        } else if (type == boolean[][].class) {
            for (boolean[] row : (boolean[][]) field.get(source)) {
                Arrays.fill(row, true);
            }
        } else if (type == int[].class) {
            Arrays.fill((int[]) field.get(source), 77);
        } else if (type == String[].class) {
            Arrays.fill((String[]) field.get(source), "copied");
        } else {
            fail("Unhandled persistent state field type: " + field);
        }
    }

    private static void assertCopied(Field field, AdmiralPersistentState source,
                                     AdmiralPersistentState target) throws IllegalAccessException {
        field.setAccessible(true);
        Object sourceValue = field.get(source);
        Object targetValue = field.get(target);
        if (sourceValue instanceof int[][] sourceRows && targetValue instanceof int[][] targetRows) {
            assertTrue(Arrays.deepEquals(sourceRows, targetRows), field.getName());
            assertNotSame(sourceRows, targetRows, field.getName());
            for (int row = 0; row < sourceRows.length; row++) {
                assertNotSame(sourceRows[row], targetRows[row], field.getName() + " row " + row);
            }
        } else if (sourceValue instanceof boolean[][] sourceRows
                && targetValue instanceof boolean[][] targetRows) {
            assertTrue(Arrays.deepEquals(sourceRows, targetRows), field.getName());
            assertNotSame(sourceRows, targetRows, field.getName());
            for (int row = 0; row < sourceRows.length; row++) {
                assertNotSame(sourceRows[row], targetRows[row], field.getName() + " row " + row);
            }
        } else if (sourceValue instanceof int[] sourceArray && targetValue instanceof int[] targetArray) {
            assertArrayEquals(sourceArray, targetArray, field.getName());
            assertNotSame(sourceArray, targetArray, field.getName());
        } else if (sourceValue instanceof String[] sourceArray && targetValue instanceof String[] targetArray) {
            assertArrayEquals(sourceArray, targetArray, field.getName());
            assertNotSame(sourceArray, targetArray, field.getName());
        } else {
            assertEquals(sourceValue, targetValue, field.getName());
        }
    }
}
