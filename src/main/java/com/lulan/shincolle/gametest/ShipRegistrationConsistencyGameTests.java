package com.lulan.shincolle.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.other.EntityAirplane;
import com.lulan.shincolle.entity.other.EntityAirplaneT;
import com.lulan.shincolle.entity.other.EntityAirplaneTakoyaki;
import com.lulan.shincolle.entity.other.EntityAirplaneZero;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.utility.BuffHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every ship is described in several hand-written tables (stats, spawn egg,
 * name glyph, translations). A table left out keeps the game running on a
 * fallback, so these checks make a missing or duplicated entry fail loudly.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipRegistrationConsistencyGameTests {

    private static final String[] LANGS = {"en_us", "ja_jp", "zh_cn", "zh_tw"};

    private ShipRegistrationConsistencyGameTests() {
    }

    @GameTest(template = "arena")
    public static void everyShipTypeHasConsistentRegistration(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            Map<String, JsonObject> langs = loadLangs();
            List<String> problems = new ArrayList<>();
            Map<Integer, String> friendlyOwners = new HashMap<>();
            Map<Integer, String> hostileOwners = new HashMap<>();

            for (RegistryObject<EntityType<?>> typeObject : ModEntities.ENTITIES.getEntries()) {
                EntityType<?> type = typeObject.get();
                String path = typeObject.getId().getPath();
                Entity entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                entities.add(entity);

                if (entity instanceof BasicEntityShip ship) {
                    int shipClass = ship.getShipClass();
                    checkUnique(problems, friendlyOwners, shipClass, path);
                    if (!Values.ShipAttrMap.containsKey(shipClass)) {
                        problems.add(path + ": ship class " + shipClass + " has no Values.ShipAttrMap row");
                    }
                    if (!Values.ShipNameIconMap.containsKey(shipClass)) {
                        problems.add(path + ": ship class " + shipClass + " has no Values.ShipNameIconMap glyph");
                    }
                    checkEgg(problems, path, shipClass, type);
                    checkLang(problems, langs, path, shipClass + 2);
                } else if (entity instanceof BasicEntityShipHostile hostile) {
                    int shipClass = hostile.getShipClass();
                    checkUnique(problems, hostileOwners, shipClass, path);
                    if (!Values.HostileShipAttrMap.containsKey(shipClass)) {
                        problems.add(path + ": ship class " + shipClass + " has no Values.HostileShipAttrMap row");
                    }
                    if (ShipSpawnEgg.getEntityTypeForClass(shipClass) == null) {
                        problems.add(path + ": drops the egg of ship class " + shipClass
                                + ", which spawns nothing");
                    }
                    checkEgg(problems, path, shipClass + 2000, type);
                    checkLang(problems, langs, path, shipClass + 2002);
                }
            }

            if (friendlyOwners.isEmpty() || hostileOwners.isEmpty()) {
                throw new AssertionError("No ship entity types were inspected");
            }
            if (!problems.isEmpty()) {
                throw new AssertionError(problems.size() + " ship registration problem(s):\n  "
                        + String.join("\n  ", problems));
            }
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void carrierLaunchHeightMatchesLegacyModelPoint(GameTestHelper helper) {
        Map<Supplier<? extends EntityType<?>>, Double> expected = new LinkedHashMap<>();
        expected.put(ModEntities.CV_AKAGI, 1.875D * 0.65D);
        expected.put(ModEntities.CV_KAGA, 1.875D * 0.65D);
        expected.put(ModEntities.CV_WO, 1.9D * 0.9D);
        expected.put(ModEntities.BB_RE, 1.55D * 0.8D);
        expected.put(ModEntities.AIRFIELD_HIME, 1.9D * 0.7D);
        expected.put(ModEntities.CV_HIME, 1.9D * 0.9D);
        expected.put(ModEntities.CV_WD, 1.9D * 1.2D);
        expected.put(ModEntities.HARBOUR_HIME, 2.2D * 0.7D);
        expected.put(ModEntities.ISOLATED_HIME, 1.6D * 0.7D);
        expected.put(ModEntities.MIDWAY_HIME, 2.0D * 0.7D);
        expected.put(ModEntities.NORTHERN_HIME, 0.9D);

        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (Map.Entry<Supplier<? extends EntityType<?>>, Double> entry : expected.entrySet()) {
                BasicEntityShipCV carrier = createCarrier(helper, entities, entry.getKey());
                double actual = readLaunchHeight(carrier);
                if (Math.abs(actual - entry.getValue()) > 1.0E-4D) {
                    throw new AssertionError(carrier.getClass().getSimpleName() + " launch height expected "
                            + entry.getValue() + " but was " + actual);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void carrierLaunchPointAcceptsWaterButNotStone(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShipCV carrier = createCarrier(helper, entities, ModEntities.CV_AKAGI);
            BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
            carrier.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0F, 0F);
            BlockPos launch = BlockPos.containing(carrier.getX(), carrier.getY() + readLaunchHeight(carrier),
                    carrier.getZ());

            helper.getLevel().setBlockAndUpdate(launch, Blocks.WATER.defaultBlockState());
            if (!readLaunchPointSafe(carrier)) {
                throw new AssertionError("Water at the launch point should not block airplanes");
            }
            helper.getLevel().setBlockAndUpdate(launch, Blocks.STONE.defaultBlockState());
            if (readLaunchPointSafe(carrier)) {
                throw new AssertionError("Stone at the launch point should block airplanes");
            }
            helper.getLevel().setBlockAndUpdate(launch, Blocks.AIR.defaultBlockState());
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void carrierMaxAircraftKeepsPerShipGrowth(GameTestHelper helper) {
        // level 100, no aircraft equipment: common 8 + lv/5 and 4 + lv/10, plus the ship's own growth
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            assertMaxAircraft(helper, entities, ModEntities.CV_AKAGI, 56, 32);
            assertMaxAircraft(helper, entities, ModEntities.CV_KAGA, 68, 34);
            assertMaxAircraft(helper, entities, ModEntities.CV_WO, 53, 29);
            assertMaxAircraft(helper, entities, ModEntities.BB_RE, 38, 19);
            assertMaxAircraft(helper, entities, ModEntities.CV_HIME, 28, 14);
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void akagiAndKagaLaunchZeroAndTenzan(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (Supplier<? extends EntityType<?>> type : List.of(ModEntities.CV_AKAGI, ModEntities.CV_KAGA)) {
                BasicEntityShipCV carrier = createCarrier(helper, entities, type);
                assertAirplane(carrier, true, EntityAirplaneZero.class);
                assertAirplane(carrier, false, EntityAirplaneT.class);
            }
            BasicEntityShipCV wo = createCarrier(helper, entities, ModEntities.CV_WO);
            assertAirplane(wo, true, EntityAirplane.class);
            assertAirplane(wo, false, EntityAirplaneTakoyaki.class);
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void periodicBuffTickDoesNotChargeIdleGrudge(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShipCV carrier = createCarrier(helper, entities, ModEntities.CV_AKAGI);
            carrier.setStateMinor(ID.M.NumGrudge, 1000);
            BuffHelper.applyBuffOnTicks(carrier);
            int grudge = carrier.getStateMinor(ID.M.NumGrudge);
            if (grudge != 1000) {
                throw new AssertionError("Periodic buff tick consumed grudge: 1000 -> " + grudge);
            }
            helper.succeed();
        }
    }

    private static void checkUnique(List<String> problems, Map<Integer, String> owners, int shipClass, String path) {
        String previous = owners.putIfAbsent(shipClass, path);
        if (previous != null) {
            problems.add(path + ": ship class " + shipClass + " is already used by " + previous);
        }
    }

    private static void checkEgg(List<String> problems, String path, int eggClass, EntityType<?> type) {
        EntityType<?> eggType = ShipSpawnEgg.getEntityTypeForClass(eggClass);
        if (eggType != type) {
            problems.add(path + ": spawn egg class " + eggClass + " resolves to "
                    + (eggType == null ? "nothing" : EntityType.getKey(eggType).toString()));
        }
    }

    private static void checkLang(List<String> problems, Map<String, JsonObject> langs, String path, int eggKey) {
        String entityKey = "entity." + Reference.MOD_ID + "." + path;
        String eggItemKey = "item." + Reference.MOD_ID + ".ship_egg_" + eggKey;
        for (Map.Entry<String, JsonObject> lang : langs.entrySet()) {
            for (String key : new String[]{entityKey, eggItemKey}) {
                if (!lang.getValue().has(key)) {
                    problems.add(path + ": " + lang.getKey() + " has no " + key);
                }
            }
        }
    }

    private static Map<String, JsonObject> loadLangs() {
        Map<String, JsonObject> langs = new LinkedHashMap<>();
        for (String lang : LANGS) {
            String resource = "/assets/" + Reference.MOD_ID + "/lang/" + lang + ".json";
            try (InputStream in = ShipRegistrationConsistencyGameTests.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new AssertionError("Missing language file " + resource);
                }
                langs.put(lang, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new AssertionError("Failed to read " + resource, e);
            }
        }
        return langs;
    }

    private static BasicEntityShipCV createCarrier(GameTestHelper helper, GameTestEntities entities,
                                                   Supplier<? extends EntityType<?>> type) {
        Entity entity = type.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShipCV carrier)) {
            throw new AssertionError("Not a carrier: " + EntityType.getKey(type.get()));
        }
        return entities.add(carrier);
    }

    private static void assertMaxAircraft(GameTestHelper helper, GameTestEntities entities,
                                          Supplier<? extends EntityType<?>> type, int light, int heavy) {
        BasicEntityShipCV carrier = createCarrier(helper, entities, type);
        carrier.setShipLevel(100, true);
        int actualLight = readIntField(carrier, "maxAircraftLight");
        int actualHeavy = readIntField(carrier, "maxAircraftHeavy");
        if (actualLight != light || actualHeavy != heavy) {
            throw new AssertionError(carrier.getClass().getSimpleName() + " level 100 max aircraft expected "
                    + light + "/" + heavy + " but was " + actualLight + "/" + actualHeavy);
        }
    }

    private static void assertAirplane(BasicEntityShipCV carrier, boolean light, Class<?> expected) {
        try {
            Method method = BasicEntityShipCV.class.getDeclaredMethod("getAttackAirplane", boolean.class);
            method.setAccessible(true);
            BasicEntityAirplane plane = (BasicEntityAirplane) method.invoke(carrier, light);
            if (plane == null || plane.getClass() != expected) {
                throw new AssertionError(carrier.getClass().getSimpleName() + (light ? " light" : " heavy")
                        + " airplane expected " + expected.getSimpleName() + " but was "
                        + (plane == null ? "null" : plane.getClass().getSimpleName()));
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to call getAttackAirplane", e);
        }
    }

    private static double readLaunchHeight(BasicEntityShipCV carrier) {
        try {
            Field field = BasicEntityShipCV.class.getDeclaredField("launchHeight");
            field.setAccessible(true);
            return field.getDouble(carrier);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read launchHeight", e);
        }
    }

    private static boolean readLaunchPointSafe(BasicEntityShipCV carrier) {
        try {
            Method method = BasicEntityShipCV.class.getDeclaredMethod("isLaunchPointSafe");
            method.setAccessible(true);
            return (boolean) method.invoke(carrier);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to call isLaunchPointSafe", e);
        }
    }

    private static int readIntField(BasicEntityShipCV carrier, String name) {
        try {
            Field field = BasicEntityShipCV.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(carrier);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read " + name, e);
        }
    }
}
