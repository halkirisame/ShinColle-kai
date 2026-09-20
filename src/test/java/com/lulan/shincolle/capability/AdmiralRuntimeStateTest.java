package com.lulan.shincolle.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmiralRuntimeStateTest {

    @Test
    void createsIndependentLegacyDefaults() {
        AdmiralRuntimeState first = new AdmiralRuntimeState();
        AdmiralRuntimeState second = new AdmiralRuntimeState();

        assertFalse(first.isOpeningGUI());
        for (int team = 0; team < 9; team++) {
            for (int slot = 0; slot < 6; slot++) {
                assertEquals(-1, first.getTeamSID(team, slot));
            }
        }
        first.setTeamSID(0, 0, 7);
        first.setOpeningGUI(true);
        assertEquals(-1, second.getTeamSID(0, 0));
        assertFalse(second.isOpeningGUI());
    }

    @Test
    void clearTeamEntityIdsLeavesGuiStateUntouched() {
        AdmiralRuntimeState state = new AdmiralRuntimeState();
        state.setTeamSID(0, 0, 7);
        state.setTeamSID(8, 5, 9);
        state.setOpeningGUI(true);

        state.clearTeamEntityIDs();

        for (int team = 0; team < 9; team++) {
            for (int slot = 0; slot < 6; slot++) {
                assertEquals(-1, state.getTeamSID(team, slot));
            }
        }
        assertTrue(state.isOpeningGUI());
    }

    @Test
    void respawnCopyExcludesGuiState() {
        AdmiralRuntimeState source = new AdmiralRuntimeState();
        AdmiralRuntimeState target = new AdmiralRuntimeState();
        source.setTeamSID(0, 0, 7);
        source.setOpeningGUI(true);

        target.copyForRespawn(source);

        assertEquals(7, target.getTeamSID(0, 0));
        assertFalse(target.isOpeningGUI());
    }
}
