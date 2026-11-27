package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.NBTHelper;
import com.lulan.shincolle.utility.ShipNameTagHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipUnitNamePersistenceGameTests {
    private ShipUnitNamePersistenceGameTests() {}

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedUnitNamesSurviveReloadWithAnOfflineOwner(GameTestHelper helper) {
        BasicEntityShip source = ship(helper);
        source.setOwnerUUID(UUID.randomUUID());
        source.unitNames.add("First Fleet");
        CompoundTag saved = source.saveWithoutId(new CompoundTag());
        assertSavedNames(helper, saved, List.of("First Fleet"));
        BasicEntityShip loaded = load(helper, saved);
        helper.assertTrue(loaded != source && loaded.getOwner() == null
                        && loaded.unitNames.equals(List.of("First Fleet")),
                "A fresh entity must recover the saved name without an online owner");
        helper.assertTrue(!ShipNameTagHelper.refresh(loaded)
                        && loaded.unitNames.equals(List.of("First Fleet")),
                "An unresolved owner must not erase the restored names");
        BasicEntityShip reloaded = load(helper, loaded.saveWithoutId(new CompoundTag()));
        helper.assertTrue(reloaded.unitNames.equals(List.of("First Fleet")),
                "Writing the new format again must retain names on a second fresh load");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void emptyUnitNamesAreWrittenAndReadBack(GameTestHelper helper) {
        BasicEntityShip source = ship(helper);
        helper.assertTrue(source.unitNames.isEmpty(), "New ship must have no names");
        CompoundTag saved = source.saveWithoutId(new CompoundTag());
        assertSavedNames(helper, saved, List.of());
        helper.assertTrue(load(helper, saved).unitNames.isEmpty(), "Empty names must survive a fresh load");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void olderCurrentSavesWithoutUnitNamesLoadEmpty(GameTestHelper helper) {
        CompoundTag saved = ship(helper).saveWithoutId(new CompoundTag());
        saved.remove("UnitNames");
        helper.assertTrue(saved.contains("StateMinor", Tag.TAG_INT_ARRAY)
                        && saved.contains("StateFlag", Tag.TAG_BYTE_ARRAY)
                        && saved.contains("StateEmotion", Tag.TAG_INT_ARRAY)
                        && !saved.contains("ShipExtProps") && !saved.contains("UnitNames"),
                "Fixture must match the current schema before names were persisted");
        helper.assertTrue(load(helper, saved).unitNames.isEmpty(), "Absent names must leave a fresh list empty");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyUnitNamesLoadAndMigrateThroughTheNewFormat(GameTestHelper helper) {
        CompoundTag saved = ship(helper).saveWithoutId(new CompoundTag());
        saved.remove("StateMinor");
        saved.remove("StateFlag");
        saved.remove("StateEmotion");
        saved.remove("UnitNames");
        CompoundTag legacy = new CompoundTag();
        NBTHelper.saveStringTagArrayList(legacy, "uname", new ArrayList<>(List.of("Legacy Fleet")));
        saved.put("ShipExtProps", legacy);
        helper.assertTrue(!saved.contains("UnitNames") && !saved.contains("StateMinor")
                        && legacy.contains("uname", Tag.TAG_LIST), "Legacy fallback fixture");
        BasicEntityShip loaded = load(helper, saved);
        helper.assertTrue(loaded.unitNames.equals(List.of("Legacy Fleet")), "Legacy uname must still load");
        CompoundTag migrated = loaded.saveWithoutId(new CompoundTag());
        assertSavedNames(helper, migrated, List.of("Legacy Fleet"));
        helper.assertTrue(load(helper, migrated).unitNames.equals(List.of("Legacy Fleet")),
                "Names loaded from the old format must survive writing and reading the new one");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void explicitEmptyCurrentNamesWinOverLegacyNames(GameTestHelper helper) {
        assertCurrentPrecedence(helper, List.of());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void populatedCurrentNamesWinOverLegacyNames(GameTestHelper helper) {
        assertCurrentPrecedence(helper, List.of("Current Fleet", "第五艦隊"));
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipRecoveryBackupIncludesReloadableUnitNames(GameTestHelper helper) throws Exception {
        BasicEntityShip source = ship(helper);
        source.unitNames.add("Backup Fleet");
        Method backup = ServerDataManager.class.getDeclaredMethod("createShipNBTBackup", BasicEntityShip.class);
        backup.setAccessible(true);
        CompoundTag saved = (CompoundTag) backup.invoke(null, source);
        assertSavedNames(helper, saved, List.of("Backup Fleet"));
        helper.assertTrue(load(helper, saved).unitNames.equals(List.of("Backup Fleet")),
                "Recovery backup must restore the names on a fresh ship");
        helper.succeed();
    }

    private static void assertCurrentPrecedence(GameTestHelper helper, List<String> names) {
        for (boolean legacyFallback : new boolean[]{false, true}) {
            BasicEntityShip source = ship(helper);
            source.unitNames = new ArrayList<>(names);
            CompoundTag saved = source.saveWithoutId(new CompoundTag());
            assertSavedNames(helper, saved, names);
            CompoundTag legacy = new CompoundTag();
            NBTHelper.saveStringTagArrayList(legacy, "uname", new ArrayList<>(List.of("Old Fleet")));
            saved.put("ShipExtProps", legacy);
            if (legacyFallback) saved.remove("StateEmotion");
            helper.assertTrue(saved.contains("StateEmotion", Tag.TAG_INT_ARRAY) != legacyFallback,
                    "Fixture must exercise both the current-only and legacy-fallback read paths");
            BasicEntityShip loaded = load(helper, saved);
            helper.assertTrue(loaded.unitNames.equals(names),
                    "Current list must take precedence, including empty: fallback=" + legacyFallback);
            helper.assertTrue(load(helper, loaded.saveWithoutId(new CompoundTag())).unitNames.equals(names),
                    "Current precedence must survive a subsequent new-format round trip");
        }
    }

    private static void assertSavedNames(GameTestHelper helper, CompoundTag saved, List<String> names) {
        helper.assertTrue(saved.contains("UnitNames", Tag.TAG_LIST)
                        && NBTHelper.loadStringTagArrayList(saved, "UnitNames").equals(names),
                "UnitNames must be present as a string list, even when empty: " + names);
        helper.assertTrue(names.isEmpty() || saved.getList("UnitNames", Tag.TAG_STRING).getElementType() == Tag.TAG_STRING,
                "Nonempty names must be stored as string elements");
    }

    private static BasicEntityShip load(GameTestHelper helper, CompoundTag saved) {
        BasicEntityShip loaded = ship(helper);
        helper.assertTrue(loaded.unitNames.isEmpty(), "Load destination must begin with an empty list");
        loaded.load(saved);
        return loaded;
    }

    private static BasicEntityShip ship(GameTestHelper helper) {
        // No entity is added to the level: these fixtures only exercise synchronous save/load.
        BasicEntityShip ship = ModEntities.DESTROYER_I.get().create(helper.getLevel());
        helper.assertTrue(ship != null, "Ship type must create a fixture");
        return ship;
    }
}
