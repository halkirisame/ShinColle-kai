package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.network.ShipAttributeSyncV2Codec;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/**
 * Regression tests for full attribute snapshots sent when tracking begins.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttributeTrackingSyncGameTests {

    private static final float MIN_MOV = 0.375F;

    private ShipAttributeTrackingSyncGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyFullSnapshotIgnoresAndPreservesDeltaFlags(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship) || !(ship.getAttrs() instanceof AttrsAdv attrs)) {
            throw new AssertionError("Failed to create friendly ship with AttrsAdv");
        }
        ship.setId(4101);
        fillDistinctValues(attrs);
        setAllAttributeFlags(ship, false);

        S2CEntitySyncPacket emptyDelta = S2CEntitySyncPacket.syncAttrs(ship);
        assertEmptyDelta(emptyDelta);

        S2CEntitySyncPacket snapshot = S2CEntitySyncPacket.syncAllAttrs(ship);
        assertFullSnapshot(snapshot, ship.getId(), attrs);

        ship.setUpdateFlag(ID.FlagUpdate.AttrsRaw, true);
        S2CEntitySyncPacket delta = S2CEntitySyncPacket.syncAttrs(ship);
        if (!ship.getUpdateFlag(ID.FlagUpdate.AttrsRaw)) {
            throw new AssertionError("Delta factory must not consume a pending flag");
        }
        ShipAttributeSyncV2Codec.Snapshot decoded = ShipAttributeSyncV2Codec.decode(delta.getPayload());
        if (decoded.fieldMask() != ShipAttributeSyncV2Codec.RAW_MASK) {
            throw new AssertionError("Delta did not encode only the dirty raw layer");
        }
        S2CEntitySyncPacket.clearSyncedAttributeFlags(ship, decoded.fieldMask());
        if (ship.getUpdateFlag(ID.FlagUpdate.AttrsRaw)) {
            throw new AssertionError("Explicit successful-send cleanup did not clear the raw flag");
        }

        ship.setUpdateFlag(ID.FlagUpdate.AttrsEquip, true);
        S2CEntitySyncPacket.syncAllAttrs(ship);
        if (!ship.getUpdateFlag(ID.FlagUpdate.AttrsEquip)) {
            throw new AssertionError("Full snapshot must not consume a pending delta flag");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void hostileFullSnapshotContainsEveryLayer(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShipHostile hostile)
                || !(hostile.getAttrs() instanceof AttrsAdv attrs)) {
            throw new AssertionError("Failed to create hostile ship with AttrsAdv");
        }
        hostile.setId(4102);
        fillDistinctValues(attrs);

        S2CEntitySyncPacket snapshot = S2CEntitySyncPacket.syncAllAttrs(hostile);
        assertFullSnapshot(snapshot, hostile.getId(), attrs);
        helper.succeed();
    }

    private static void fillDistinctValues(AttrsAdv attrs) {
        byte[] bonus = new byte[attrs.getAttrsBonus().length];
        for (int i = 0; i < bonus.length; i++) {
            bonus[i] = (byte) (i + 1);
        }
        attrs.setAttrsBonus(bonus);
        attrs.setAttrsRaw(values(10F));
        attrs.setAttrsEquip(values(20F));
        attrs.setAttrsMorale(values(30F));
        attrs.setAttrsPotion(values(40F));
        attrs.setAttrsFormation(values(50F));
        attrs.setAttrsBuffed(values(60F));
        attrs.setMinMOV(MIN_MOV);
    }

    private static float[] values(float base) {
        float[] values = new float[Attrs.AttrsLength];
        for (int i = 0; i < values.length; i++) {
            values[i] = base + i * 0.25F;
        }
        return values;
    }

    private static void setAllAttributeFlags(BasicEntityShip ship, boolean value) {
        ship.setUpdateFlag(ID.FlagUpdate.AttrsBonus, value);
        ship.setUpdateFlag(ID.FlagUpdate.AttrsRaw, value);
        ship.setUpdateFlag(ID.FlagUpdate.AttrsEquip, value);
        ship.setUpdateFlag(ID.FlagUpdate.AttrsMorale, value);
        ship.setUpdateFlag(ID.FlagUpdate.AttrsPotion, value);
        ship.setUpdateFlag(ID.FlagUpdate.AttrsFormation, value);
        ship.setUpdateFlag(ID.FlagUpdate.AttrsBuffed, value);
    }

    private static void assertEmptyDelta(S2CEntitySyncPacket packet) {
        ShipAttributeSyncV2Codec.Snapshot snapshot = ShipAttributeSyncV2Codec.decode(packet.getPayload());
        if (snapshot.fieldMask() != 0 || snapshot.bonus() != null || !snapshot.layers().isEmpty()) {
            throw new AssertionError("Delta with cleared flags unexpectedly included data");
        }
    }

    private static void assertFullSnapshot(S2CEntitySyncPacket packet, int entityId, AttrsAdv expected) {
        if (packet.getType() != S2CEntitySyncPacket.SyncShip_Attrs || packet.getEntityId() != entityId) {
            throw new AssertionError("Full snapshot has the wrong packet type or entity ID");
        }

        ShipAttributeSyncV2Codec.Snapshot snapshot = ShipAttributeSyncV2Codec.decode(packet.getPayload());
        if (snapshot.fieldMask() != ShipAttributeSyncV2Codec.ALL_FIELDS_MASK) {
            throw new AssertionError("Full snapshot omitted fields");
        }
        assertArrayEquals(expected.getAttrsBonus(), snapshot.bonus(), "bonus");
        assertLayerEquals(expected, snapshot, ShipAttributeLayer.RAW, "raw");
        assertLayerEquals(expected, snapshot, ShipAttributeLayer.EQUIPMENT, "equipment");
        assertLayerEquals(expected, snapshot, ShipAttributeLayer.MORALE, "morale");
        assertLayerEquals(expected, snapshot, ShipAttributeLayer.POTION, "potion");
        assertLayerEquals(expected, snapshot, ShipAttributeLayer.FORMATION, "formation");
        assertLayerEquals(expected, snapshot, ShipAttributeLayer.BUFFED, "buffed");
        assertFloatEquals(MIN_MOV, snapshot.minMOV(), "MinMOV");
    }

    private static void assertLayerEquals(AttrsAdv expected, ShipAttributeSyncV2Codec.Snapshot snapshot,
                                          ShipAttributeLayer layer, String name) {
        if (!expected.shipAttributes(layer).asMap().equals(snapshot.layers().get(layer).asMap())) {
            throw new AssertionError(name + " snapshot differs from server values");
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String layer) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(layer + " snapshot differs from server values");
        }
    }

    private static void assertArrayEquals(float[] expected, float[] actual, String layer) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(layer + " snapshot differs from server values");
        }
    }

    private static void assertFloatEquals(float expected, float actual, String name) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }
}
