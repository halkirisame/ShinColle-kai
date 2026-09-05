package com.lulan.shincolle.capability;

/**
 * Minecraft-independent owner of persistent admiral state.
 */
final class AdmiralPersistentState {

    static final int TEAM_NUM = 9;
    static final int SLOT_NUM = 6;

    private final int[][] teamList;
    private final boolean[][] selectState;
    private final int[] formatID;
    private final String[] unitNames;
    private boolean hasRing;
    private boolean isRingActive;
    private boolean isRingFlying;
    private int marriageNum;
    private int bossCooldown;
    private int teamCooldown;
    private int playerUID;
    private int selectTeam;
    private int colledShipNum;
    private int colledEquipNum;

    AdmiralPersistentState(int bossCooldownDefault) {
        this.teamList = new int[TEAM_NUM][SLOT_NUM];
        this.selectState = new boolean[TEAM_NUM][SLOT_NUM];
        this.formatID = new int[TEAM_NUM];
        this.unitNames = new String[TEAM_NUM];
        this.bossCooldown = bossCooldownDefault;
        this.playerUID = -1;

        for (int team = 0; team < TEAM_NUM; team++) {
            for (int slot = 0; slot < SLOT_NUM; slot++) {
                this.teamList[team][slot] = -1;
            }
            this.unitNames[team] = "Team " + (team + 1);
        }
    }

    boolean hasRing() {
        return hasRing;
    }

    void setHasRing(boolean value) {
        this.hasRing = value;
    }

    boolean isRingActive() {
        return isRingActive;
    }

    void setRingActive(boolean value) {
        this.isRingActive = value;
    }

    boolean isRingFlying() {
        return isRingFlying;
    }

    void setRingFlying(boolean value) {
        this.isRingFlying = value;
    }

    int getMarriageNum() {
        return marriageNum;
    }

    void setMarriageNum(int value) {
        this.marriageNum = value;
    }

    void addMarriageNum(int value) {
        this.marriageNum += value;
    }

    int getBossCooldown() {
        return bossCooldown;
    }

    void setBossCooldown(int value) {
        this.bossCooldown = value;
    }

    int getTeamCooldown() {
        return teamCooldown;
    }

    void setTeamCooldown(int value) {
        this.teamCooldown = value;
    }

    int getPlayerUID() {
        return playerUID;
    }

    void setPlayerUID(int value) {
        this.playerUID = value;
    }

    int getSelectTeam() {
        return selectTeam;
    }

    void setSelectTeam(int value) {
        this.selectTeam = Math.max(0, Math.min(value, TEAM_NUM - 1));
    }

    void loadSelectTeam(int value) {
        this.selectTeam = value;
    }

    int getColledShipNum() {
        return colledShipNum;
    }

    void setColledShipNum(int value) {
        this.colledShipNum = value;
    }

    int getColledEquipNum() {
        return colledEquipNum;
    }

    void setColledEquipNum(int value) {
        this.colledEquipNum = value;
    }

    int getTeamMember(int team, int slot) {
        return isValidSlot(team, slot) ? teamList[team][slot] : -1;
    }

    void setTeamMember(int team, int slot, int shipUID) {
        if (isValidSlot(team, slot)) {
            teamList[team][slot] = shipUID;
        }
    }

    int[] copyTeamMembers(int team) {
        return isValidTeam(team) ? teamList[team].clone() : new int[0];
    }

    void loadTeamMembers(int team, int[] members) {
        if (!isValidTeam(team) || members == null) {
            return;
        }
        System.arraycopy(members, 0, teamList[team], 0, Math.min(members.length, SLOT_NUM));
    }

    boolean isShipSelected(int team, int slot) {
        return isValidSlot(team, slot) && selectState[team][slot];
    }

    void setShipSelected(int team, int slot, boolean selected) {
        if (isValidSlot(team, slot)) {
            selectState[team][slot] = selected;
        }
    }

    void clearShipSelection(int team) {
        if (!isValidTeam(team)) {
            return;
        }
        for (int slot = 0; slot < SLOT_NUM; slot++) {
            selectState[team][slot] = false;
        }
    }

    byte[] copyShipSelection(int team) {
        byte[] selected = new byte[isValidTeam(team) ? SLOT_NUM : 0];
        for (int slot = 0; slot < selected.length; slot++) {
            selected[slot] = (byte) (selectState[team][slot] ? 1 : 0);
        }
        return selected;
    }

    void loadShipSelection(int team, byte[] selected) {
        if (!isValidTeam(team) || selected == null) {
            return;
        }
        for (int slot = 0; slot < Math.min(selected.length, SLOT_NUM); slot++) {
            selectState[team][slot] = selected[slot] != 0;
        }
    }

    void migrateShipSelection(int team, int[] members) {
        if (!isValidTeam(team) || members == null) {
            return;
        }
        for (int slot = 0; slot < Math.min(members.length, SLOT_NUM); slot++) {
            if (members[slot] > 0) {
                selectState[team][slot] = true;
                break;
            }
        }
    }

    int getFormatID(int team) {
        return isValidTeam(team) ? formatID[team] : 0;
    }

    void setFormatID(int team, int format) {
        if (isValidTeam(team)) {
            formatID[team] = format;
        }
    }

    String getUnitName(int team) {
        return isValidTeam(team) ? unitNames[team] : "";
    }

    void setUnitName(int team, String name) {
        if (isValidTeam(team)) {
            unitNames[team] = name != null ? name : "";
        }
    }

    int findTeamOfShip(int shipUID) {
        if (shipUID <= 0) {
            return -1;
        }
        for (int team = 0; team < TEAM_NUM; team++) {
            for (int slot = 0; slot < SLOT_NUM; slot++) {
                if (teamList[team][slot] == shipUID) {
                    return team;
                }
            }
        }
        return -1;
    }

    void copyFrom(AdmiralPersistentState other) {
        this.hasRing = other.hasRing;
        this.isRingActive = other.isRingActive;
        this.isRingFlying = other.isRingFlying;
        this.marriageNum = other.marriageNum;
        this.bossCooldown = other.bossCooldown;
        this.teamCooldown = other.teamCooldown;
        this.playerUID = other.playerUID;
        this.selectTeam = other.selectTeam;
        this.colledShipNum = other.colledShipNum;
        this.colledEquipNum = other.colledEquipNum;
        for (int team = 0; team < TEAM_NUM; team++) {
            System.arraycopy(other.teamList[team], 0, this.teamList[team], 0, SLOT_NUM);
            System.arraycopy(other.selectState[team], 0, this.selectState[team], 0, SLOT_NUM);
            this.formatID[team] = other.formatID[team];
            this.unitNames[team] = other.unitNames[team];
        }
    }

    private static boolean isValidTeam(int team) {
        return team >= 0 && team < TEAM_NUM;
    }

    private static boolean isValidSlot(int team, int slot) {
        return isValidTeam(team) && slot >= 0 && slot < SLOT_NUM;
    }
}
