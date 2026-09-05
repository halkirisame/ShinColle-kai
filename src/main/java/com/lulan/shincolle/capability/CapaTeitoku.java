package com.lulan.shincolle.capability;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Arrays;
import java.util.List;

/**
 * Player capability for ShinColle ("Admiral" / "Teitoku" data).
 * <p>
 * Stores per-player persistent data:
 * - hasRing / isRingActive / isRingFlying (marriage ring state)
 * - marriageNum (total married ships)
 * - bossCooldown / teamCooldown (cooldown timers)
 * - teamList[9][6] - persistent ship UIDs per team slot
 * - sidList[9][6] - transient runtime entity IDs
 * - formatID[9] - formation type per team
 * - unitNames[9] - team names
 * - playerUID - unique player ID
 * - selectTeam - currently selected team index
 * - colledShipNum / colledEquipNum - collection counters
 */
public class CapaTeitoku implements INBTSerializable<CompoundTag> {

    public static final int TEAM_NUM = 9;
    public static final int SLOT_NUM = 6;
    private final AdmiralPersistentState persistent;
    private final AdmiralRuntimeState runtime;
    private final AdmiralClientMirror mirror;

    public CapaTeitoku() {
        this.persistent = new AdmiralPersistentState(ConfigHandler.bossCooldown());
        this.runtime = new AdmiralRuntimeState();
        this.mirror = new AdmiralClientMirror();
    }

    // ========== NBT Serialization ==========

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

        nbt.putBoolean("HasRing", persistent.hasRing());
        nbt.putBoolean("RingActive", persistent.isRingActive());
        nbt.putBoolean("RingFlying", persistent.isRingFlying());
        nbt.putInt("MarriageNum", persistent.getMarriageNum());
        nbt.putInt("BossCD", persistent.getBossCooldown());
        nbt.putInt("TeamCD", persistent.getTeamCooldown());
        nbt.putInt("PlayerUID", persistent.getPlayerUID());
        nbt.putInt("SelectTeam", persistent.getSelectTeam());
        nbt.putInt("ColledShip", persistent.getColledShipNum());
        nbt.putInt("ColledEquip", persistent.getColledEquipNum());

        // Save team data
        ListTag teamTag = new ListTag();
        for (int i = 0; i < TEAM_NUM; i++) {
            CompoundTag team = new CompoundTag();
            team.putIntArray("EIDs", persistent.copyTeamMembers(i));
            team.putByteArray("Selected", persistent.copyShipSelection(i));
            // Runtime entity IDs are deliberately not persisted. Minecraft
            // may reuse them after reload or a dimension transition.
            team.putInt("Format", persistent.getFormatID(i));
            team.putString("Name", persistent.getUnitName(i));
            teamTag.add(team);
        }
        nbt.put("Teams", teamTag);

        // Save persistent list/string fields
        nbt.putString("TeamName", mirror.getTeamName());
        nbt.putIntArray("TargetClassList", toIntArray(mirror.getTargetClassList()));
        nbt.putIntArray("AllyList", toIntArray(mirror.getAllyList()));
        nbt.putIntArray("BanList", toIntArray(mirror.getBanList()));
        nbt.putIntArray("KnownTeamIds", toIntArray(mirror.getKnownTeamIds()));
        nbt.putIntArray("ColledShipList", toIntArray(mirror.getColledShipList()));
        nbt.putIntArray("ColledEquipList", toIntArray(mirror.getColledEquipList()));
        nbt.putIntArray("ShipList", toIntArray(mirror.getShipList()));

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        persistent.setHasRing(nbt.getBoolean("HasRing"));
        persistent.setRingActive(nbt.getBoolean("RingActive"));
        persistent.setRingFlying(nbt.getBoolean("RingFlying"));
        persistent.setMarriageNum(nbt.getInt("MarriageNum"));
        persistent.setBossCooldown(nbt.contains("BossCD", Tag.TAG_INT)
                ? nbt.getInt("BossCD")
                : ConfigHandler.bossCooldown());
        persistent.setTeamCooldown(nbt.getInt("TeamCD"));
        persistent.setPlayerUID(nbt.getInt("PlayerUID"));
        persistent.loadSelectTeam(nbt.getInt("SelectTeam"));
        persistent.setColledShipNum(nbt.getInt("ColledShip"));
        persistent.setColledEquipNum(nbt.getInt("ColledEquip"));

        // Load team data
        if (nbt.contains("Teams")) {
            ListTag teamTag = nbt.getList("Teams", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(teamTag.size(), TEAM_NUM); i++) {
                CompoundTag team = teamTag.getCompound(i);
                int[] eids = team.getIntArray("EIDs");

                persistent.loadTeamMembers(i, eids);

                if (team.contains("Selected", Tag.TAG_BYTE_ARRAY)) {
                    persistent.loadShipSelection(i, team.getByteArray("Selected"));
                } else {
                    // Migration from saves made before pointer selection was ported.
                    // Keep single mode usable by choosing the first occupied slot.
                    persistent.migrateShipSelection(i, eids);
                }

                persistent.setFormatID(i, team.getInt("Format"));
                persistent.setUnitName(i, team.getString("Name"));
            }
        }

        // Load persistent list/string fields
        mirror.setTeamName(nbt.contains("TeamName") ? nbt.getString("TeamName") : "");
        mirror.setTargetClassNames(List.of());
        mirror.setTargetClassList(readIntList(nbt, "TargetClassList"));
        mirror.setAllyList(readIntList(nbt, "AllyList"));
        mirror.setBanList(readIntList(nbt, "BanList"));
        mirror.setKnownTeamIds(readIntList(nbt, "KnownTeamIds"));
        mirror.setColledShipList(readIntList(nbt, "ColledShipList"));
        mirror.setColledEquipList(readIntList(nbt, "ColledEquipList"));
        mirror.setShipList(readIntList(nbt, "ShipList"));
    }

    // ========== Getters / Setters ==========

    public boolean hasRing() {
        return persistent.hasRing();
    }

    public void setHasRing(boolean val) {
        persistent.setHasRing(val);
    }

    public boolean isRingActive() {
        return persistent.isRingActive();
    }

    public void setRingActive(boolean val) {
        persistent.setRingActive(val);
    }

    public boolean isRingFlying() {
        return persistent.isRingFlying();
    }

    public void setRingFlying(boolean val) {
        persistent.setRingFlying(val);
    }

    public int getMarriageNum() {
        return persistent.getMarriageNum();
    }

    public void setMarriageNum(int val) {
        persistent.setMarriageNum(val);
    }

    public void addMarriageNum(int val) {
        persistent.addMarriageNum(val);
    }

    public int getBossCooldown() {
        return persistent.getBossCooldown();
    }

    public void setBossCooldown(int val) {
        persistent.setBossCooldown(val);
    }

    public int getTeamCooldown() {
        return persistent.getTeamCooldown();
    }

    public void setTeamCooldown(int val) {
        persistent.setTeamCooldown(val);
    }

    public int getPlayerUID() {
        return persistent.getPlayerUID();
    }

    public void setPlayerUID(int val) {
        persistent.setPlayerUID(val);
    }

    public int getSelectTeam() {
        return persistent.getSelectTeam();
    }

    public void setSelectTeam(int val) {
        persistent.setSelectTeam(val);
    }

    public int getColledShipNum() {
        return persistent.getColledShipNum();
    }

    public void setColledShipNum(int val) {
        persistent.setColledShipNum(val);
    }

    public int getColledEquipNum() {
        return persistent.getColledEquipNum();
    }

    public void setColledEquipNum(int val) {
        persistent.setColledEquipNum(val);
    }

    public boolean isOpeningGUI() {
        return runtime.isOpeningGUI();
    }

    public void setOpeningGUI(boolean val) {
        runtime.setOpeningGUI(val);
    }

    // ========== Team accessors ==========

    // ========== New list/flag accessors ==========

    public List<Integer> getTargetClassList() {
        return mirror.getTargetClassList();
    }

    public void setTargetClassList(List<Integer> list) {
        mirror.setTargetClassList(list);
    }

    public List<String> getTargetClassNames() {
        return mirror.getTargetClassNames();
    }

    public void setTargetClassNames(List<String> names) {
        mirror.setTargetClassNames(names);
    }

    public String getTeamName() {
        return mirror.getTeamName();
    }

    public void setTeamName(String name) {
        mirror.setTeamName(name);
    }

    public List<Integer> getAllyList() {
        return mirror.getAllyList();
    }

    public void setAllyList(List<Integer> list) {
        mirror.setAllyList(list);
    }

    public List<Integer> getBanList() {
        return mirror.getBanList();
    }

    public void setBanList(List<Integer> list) {
        mirror.setBanList(list);
    }

    public List<Integer> getKnownTeamIds() {
        return mirror.getKnownTeamIds();
    }

    public void setKnownTeamIds(List<Integer> list) {
        mirror.setKnownTeamIds(list);
    }

    public List<Integer> getColledShipList() {
        return mirror.getColledShipList();
    }

    public void setColledShipList(List<Integer> list) {
        mirror.setColledShipList(list);
    }

    public List<Integer> getColledEquipList() {
        return mirror.getColledEquipList();
    }

    public void setColledEquipList(List<Integer> list) {
        mirror.setColledEquipList(list);
    }

    public List<Integer> getShipList() {
        return mirror.getShipList();
    }

    public void setShipList(List<Integer> list) {
        mirror.setShipList(list);
    }

    public boolean isInitSID() {
        return mirror.isInitSID();
    }

    public void setInitSID(boolean val) {
        mirror.setInitSID(val);
    }

    public boolean isShowPlayerSkill() {
        return mirror.isShowPlayerSkill();
    }

    public void setShowPlayerSkill(boolean val) {
        mirror.setShowPlayerSkill(val);
    }

    public float[] getEntityItemList() {
        return mirror.getEntityItemList();
    }

    public void setEntityItemList(float[] items) {
        mirror.setEntityItemList(items);
    }

    // ========== Team member accessors ==========

    public int getTeamMember(int team, int slot) {
        return persistent.getTeamMember(team, slot);
    }

    public void setTeamMember(int team, int slot, int shipUID) {
        persistent.setTeamMember(team, slot, shipUID);
    }

    public int getTeamSID(int team, int slot) {
        return runtime.getTeamSID(team, slot);
    }

    public void setTeamSID(int team, int slot, int entityId) {
        runtime.setTeamSID(team, slot, entityId);
    }

    /**
     * Find the team containing a persistent ship UID.
     *
     * @return the team index, or -1 when the ship is not assigned to this player
     */
    public int findTeamOfShip(int shipUID) {
        return persistent.findTeamOfShip(shipUID);
    }

    /**
     * Return the lowest current buffed MOV among the resolved ships in a team.
     */
    public float getMinMOVInTeam(ServerLevel level, int team) {
        if (level == null || team < 0 || team >= TEAM_NUM) {
            return 0F;
        }

        float minMov = 10F;
        boolean foundShip = false;
        for (int slot = 0; slot < SLOT_NUM; slot++) {
            BasicEntityShip ship = resolveTeamShip(level, team, slot);
            if (ship != null && ship.getAttrs() != null) {
                foundShip = true;
                minMov = Math.min(minMov, ship.getAttrs().getMoveSpeed());
            }
        }
        return foundShip ? minMov : 0F;
    }

    /**
     * Count loaded, living ships in a team.
     */
    public int getNumberOfShip(ServerLevel level, int team) {
        if (level == null || team < 0 || team >= TEAM_NUM) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < SLOT_NUM; slot++) {
            BasicEntityShip ship = resolveTeamShip(level, team, slot);
            if (ship != null && ship.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Return a living ship's compacted position in a team, for five-ship diamond formations.
     */
    public int getFormationPos(ServerLevel level, int team, int shipUID) {
        if (level == null || team < 0 || team >= TEAM_NUM) {
            return -1;
        }

        int position = 0;
        for (int slot = 0; slot < SLOT_NUM; slot++) {
            BasicEntityShip ship = resolveTeamShip(level, team, slot);
            if (ship != null && ship.isAlive()) {
                if (ship.getShipUID() == shipUID) {
                    return position;
                }
                position++;
            }
        }
        return -1;
    }

    private BasicEntityShip resolveTeamShip(ServerLevel level, int team, int slot) {
        int shipUID = getTeamMember(team, slot);
        if (shipUID <= 0) {
            return null;
        }

        int entityId = getTeamSID(team, slot);
        if (entityId > 0) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof BasicEntityShip ship
                    && ship.getShipUID() == shipUID
                    && ship.getPlayerUID() == persistent.getPlayerUID()) {
                return ship;
            }
        }

        BasicEntityShip ship = ServerDataManager.getShipByUID(shipUID);
        if (ship != null && ship.level() == level && ship.getPlayerUID() == persistent.getPlayerUID()) {
            setTeamSID(team, slot, ship.getId());
            return ship;
        }
        setTeamSID(team, slot, -1);
        return null;
    }

    public boolean isShipSelected(int team, int slot) {
        return persistent.isShipSelected(team, slot);
    }

    public void setShipSelected(int team, int slot, boolean selected) {
        persistent.setShipSelected(team, slot, selected);
    }

    public void clearShipSelection(int team) {
        persistent.clearShipSelection(team);
    }

    public int getFormatID(int team) {
        return persistent.getFormatID(team);
    }

    public void setFormatID(int team, int format) {
        persistent.setFormatID(team, format);
    }

    public String getUnitName(int team) {
        return persistent.getUnitName(team);
    }

    public void setUnitName(int team, String name) {
        persistent.setUnitName(team, name);
    }

    /**
     * Clear all entity IDs in team slots (e.g. on dimension change)
     */
    public void clearTeamEntityIDs() {
        runtime.clearTeamEntityIDs();
    }

    /**
     * Check if this player has a team (has team data in ServerDataManager)
     */
    public boolean hasTeam() {
        return persistent.getPlayerUID() > 0
                && com.lulan.shincolle.server.ServerDataManager.getTeamData(persistent.getPlayerUID()) != null;
    }

    /**
     * Copy all persistent data from another CapaTeitoku (for player respawn)
     */
    public void copyFrom(CapaTeitoku other) {
        this.persistent.copyFrom(other.persistent);
        this.runtime.copyForRespawn(other.runtime);
        this.mirror.copyFrom(other.mirror);
    }

    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<Integer> readIntList(CompoundTag nbt, String key) {
        return nbt.contains(key) ? Arrays.stream(nbt.getIntArray(key)).boxed().toList() : List.of();
    }
}
