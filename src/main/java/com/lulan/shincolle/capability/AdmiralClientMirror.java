package com.lulan.shincolle.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minecraft-independent owner of client synchronization mirrors.
 */
final class AdmiralClientMirror {

    private List<Integer> targetClassList = new ArrayList<>();
    private List<String> targetClassNames = new ArrayList<>();
    private String teamName = "";
    private List<Integer> allyList = new ArrayList<>();
    private List<Integer> banList = new ArrayList<>();
    private List<Integer> knownTeamIds = new ArrayList<>();
    private List<Integer> colledShipList = new ArrayList<>();
    private List<Integer> colledEquipList = new ArrayList<>();
    private List<Integer> shipList = new ArrayList<>();
    private boolean initSID;
    private boolean showPlayerSkill;
    private float[] entityItemList = new float[0];

    List<Integer> getTargetClassList() {
        return Collections.unmodifiableList(targetClassList);
    }

    void setTargetClassList(List<Integer> values) {
        this.targetClassList = copyOrEmpty(values);
    }

    List<String> getTargetClassNames() {
        return Collections.unmodifiableList(targetClassNames);
    }

    void setTargetClassNames(List<String> names) {
        this.targetClassNames = copyOrEmpty(names);
        this.targetClassList = this.targetClassNames.stream().map(String::hashCode).toList();
    }

    String getTeamName() {
        return teamName;
    }

    void setTeamName(String name) {
        this.teamName = name != null ? name : "";
    }

    List<Integer> getAllyList() {
        return Collections.unmodifiableList(allyList);
    }

    void setAllyList(List<Integer> values) {
        this.allyList = copyOrEmpty(values);
    }

    List<Integer> getBanList() {
        return Collections.unmodifiableList(banList);
    }

    void setBanList(List<Integer> values) {
        this.banList = copyOrEmpty(values);
    }

    List<Integer> getKnownTeamIds() {
        return Collections.unmodifiableList(knownTeamIds);
    }

    void setKnownTeamIds(List<Integer> values) {
        this.knownTeamIds = copyOrEmpty(values);
    }

    List<Integer> getColledShipList() {
        return Collections.unmodifiableList(colledShipList);
    }

    void setColledShipList(List<Integer> values) {
        this.colledShipList = copyOrEmpty(values);
    }

    List<Integer> getColledEquipList() {
        return Collections.unmodifiableList(colledEquipList);
    }

    void setColledEquipList(List<Integer> values) {
        this.colledEquipList = copyOrEmpty(values);
    }

    List<Integer> getShipList() {
        return Collections.unmodifiableList(shipList);
    }

    void setShipList(List<Integer> values) {
        this.shipList = copyOrEmpty(values);
    }

    boolean isInitSID() {
        return initSID;
    }

    void setInitSID(boolean value) {
        this.initSID = value;
    }

    boolean isShowPlayerSkill() {
        return showPlayerSkill;
    }

    void setShowPlayerSkill(boolean value) {
        this.showPlayerSkill = value;
    }

    float[] getEntityItemList() {
        return entityItemList.clone();
    }

    void setEntityItemList(float[] values) {
        this.entityItemList = values != null ? values.clone() : new float[0];
    }

    void copyFrom(AdmiralClientMirror other) {
        this.targetClassList = new ArrayList<>(other.targetClassList);
        this.targetClassNames = new ArrayList<>(other.targetClassNames);
        this.teamName = other.teamName;
        this.allyList = new ArrayList<>(other.allyList);
        this.banList = new ArrayList<>(other.banList);
        this.knownTeamIds = new ArrayList<>(other.knownTeamIds);
        this.colledShipList = new ArrayList<>(other.colledShipList);
        this.colledEquipList = new ArrayList<>(other.colledEquipList);
        this.shipList = new ArrayList<>(other.shipList);
        this.initSID = other.initSID;
        this.showPlayerSkill = other.showPlayerSkill;
        this.entityItemList = other.entityItemList.clone();
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values != null ? new ArrayList<>(values) : new ArrayList<>();
    }
}
