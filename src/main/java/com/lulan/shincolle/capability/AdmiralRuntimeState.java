package com.lulan.shincolle.capability;

/**
 * Minecraft-independent owner of transient admiral state.
 */
final class AdmiralRuntimeState {

    private static final int TEAM_NUM = 9;
    private static final int SLOT_NUM = 6;

    private final int[][] sidList;
    private boolean isOpeningGUI;

    AdmiralRuntimeState() {
        this.sidList = new int[TEAM_NUM][SLOT_NUM];
        clearTeamEntityIDs();
    }

    int getTeamSID(int team, int slot) {
        return isValidSlot(team, slot) ? sidList[team][slot] : -1;
    }

    void setTeamSID(int team, int slot, int entityId) {
        if (isValidSlot(team, slot)) {
            sidList[team][slot] = entityId;
        }
    }

    void clearTeamEntityIDs() {
        for (int team = 0; team < TEAM_NUM; team++) {
            for (int slot = 0; slot < SLOT_NUM; slot++) {
                sidList[team][slot] = -1;
            }
        }
    }

    boolean isOpeningGUI() {
        return isOpeningGUI;
    }

    void setOpeningGUI(boolean value) {
        this.isOpeningGUI = value;
    }

    /**
     * Copies only runtime entity resolution state needed after respawn.
     * GUI-open state belongs to the old player instance and is deliberately not copied.
     */
    void copyForRespawn(AdmiralRuntimeState other) {
        for (int team = 0; team < TEAM_NUM; team++) {
            System.arraycopy(other.sidList[team], 0, this.sidList[team], 0, SLOT_NUM);
        }
    }

    private static boolean isValidSlot(int team, int slot) {
        return team >= 0 && team < TEAM_NUM && slot >= 0 && slot < SLOT_NUM;
    }
}
