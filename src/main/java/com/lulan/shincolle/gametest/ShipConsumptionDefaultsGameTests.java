package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntitySummon;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRe;
import com.lulan.shincolle.entity.other.EntityRensouhou;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Class defaults, both saved-ship entrypoints, and the real resource consumers. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipConsumptionDefaultsGameTests {
    static final Vec3 SPAWN = new Vec3(1.5D, 2D, 1.5D);
    private static final UUID OWNER = UUID.fromString("74100000-0000-4000-8000-000000000001");
    private static final UUID SPAWNER = UUID.fromString("74100000-0000-4000-8000-000000000002");
    private static final int SHIP_UID = 7_410_001;

    private record ShipCase(Supplier<? extends EntityType<? extends BasicEntityShip>> type, int index) {
        BasicEntityShip create(GameTestHelper helper, GameTestEntities entities) {
            BasicEntityShip ship = entities.add(type.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Could not create " + type.get());
            ship.setNoAi(true);
            ship.setInvulnerable(true);
            return ship;
        }
    }

    private static List<ShipCase> changed() {
        return List.of(
                new ShipCase(ModEntities.BB_RE::get, ID.ShipConsume.BBV),
                new ShipCase(ModEntities.BB_RU::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.BB_TA::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.CA_NE::get, ID.ShipConsume.CA),
                new ShipCase(ModEntities.CA_RI::get, ID.ShipConsume.CA),
                new ShipCase(ModEntities.DESTROYER_I::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_RO::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_HA::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_NI::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.AIRFIELD_HIME::get, ID.ShipConsume.BBV),
                new ShipCase(ModEntities.BB_HIME::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.CA_HIME::get, ID.ShipConsume.CA),
                new ShipCase(ModEntities.CV_HIME::get, ID.ShipConsume.CV),
                new ShipCase(ModEntities.CV_WD::get, ID.ShipConsume.CV),
                new ShipCase(ModEntities.DD_HIME::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.HARBOUR_HIME::get, ID.ShipConsume.BBV),
                new ShipCase(ModEntities.ISOLATED_HIME::get, ID.ShipConsume.BBV),
                new ShipCase(ModEntities.MIDWAY_HIME::get, ID.ShipConsume.BBV),
                new ShipCase(ModEntities.NORTHERN_HIME::get, ID.ShipConsume.BBV),
                new ShipCase(ModEntities.SS_HIME::get, ID.ShipConsume.SS),
                new ShipCase(ModEntities.SSNH::get, ID.ShipConsume.SS),
                new ShipCase(ModEntities.SS_KA::get, ID.ShipConsume.SS),
                new ShipCase(ModEntities.SS_YO::get, ID.ShipConsume.SS),
                new ShipCase(ModEntities.SS_SO::get, ID.ShipConsume.SS),
                new ShipCase(ModEntities.AP_WA::get, ID.ShipConsume.AP));
    }

    private static List<ShipCase> unchanged() {
        return List.of(
                new ShipCase(ModEntities.BB_NAGATO::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.BB_YAMATO::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.BB_KONGOU::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.BB_HIEI::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.BB_HARUNA::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.BB_KIRISHIMA::get, ID.ShipConsume.BB),
                new ShipCase(ModEntities.CV_WO::get, ID.ShipConsume.CV),
                new ShipCase(ModEntities.CV_AKAGI::get, ID.ShipConsume.CV),
                new ShipCase(ModEntities.CV_KAGA::get, ID.ShipConsume.CV),
                new ShipCase(ModEntities.CA_TAKAO::get, ID.ShipConsume.CA),
                new ShipCase(ModEntities.CA_ATAGO::get, ID.ShipConsume.CA),
                new ShipCase(ModEntities.CL_TENRYUU::get, ID.ShipConsume.CL),
                new ShipCase(ModEntities.CL_TATSUTA::get, ID.ShipConsume.CL),
                new ShipCase(ModEntities.DESTROYER_AKATSUKI::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_HIBIKI::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_IKAZUCHI::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_INAZUMA::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.DESTROYER_SHIMAKAZE::get, ID.ShipConsume.DD),
                new ShipCase(ModEntities.SS_U511::get, ID.ShipConsume.SS),
                new ShipCase(ModEntities.SS_RO500::get, ID.ShipConsume.SS));
    }

    private ShipConsumptionDefaultsGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void newShipsUseClassConfig(GameTestHelper helper) {
        withConfig(false, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                helper.assertTrue(changed().size() == 25, "Expected 25 class mappings");
                for (ShipCase entry : changed()) {
                    BasicEntityShip ship = entry.create(helper, entities);
                    assertConfig(helper, ship, entry.index());
                    for (int level : new int[]{1, 100}) {
                        ship.setShipLevel(level, false);
                        ship.calcShipAttributes(31, false);
                        helper.assertTrue(ship.getLevel() == level, "Level fixture was ignored");
                        assertConfig(helper, ship, entry.index());
                    }
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unaffectedShipsRetainConstructorAndSavedBases(GameTestHelper helper) {
        withConfig(false, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                helper.assertTrue(unchanged().size() == 20, "Expected 20 unaffected mappings");
                for (ShipCase entry : unchanged()) {
                    BasicEntityShip ship = entry.create(helper, entities);
                    assertConfig(helper, ship, entry.index());
                    CompoundTag saved = savedFixture(ship, SHIP_UID);
                    setSavedBases(saved, 37, 17);
                    ship.readAdditionalSaveData(saved);
                    assertBases(helper, ship, 37, 17);
                }
                var hostile = entities.add(ModEntities.DESTROYER_AKATSUKI_MOB.get().create(helper.getLevel()));
                helper.assertTrue(hostile != null && hostile.getAmmoConsumption() == 1,
                        "Hostile constructor consumption changed");
                BasicEntityShip subclass = entities.add(new EntityBattleshipRe(ModEntities.BB_RE.get(), helper.getLevel()) { });
                CompoundTag saved = savedFixture(subclass, SHIP_UID);
                setSavedBases(saved, 37, 17);
                subclass.readAdditionalSaveData(saved);
                assertBases(helper, subclass, 37, 17);
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void worldLoadRebuildsOnlyConsumptionBases(GameTestHelper helper) {
        withConfig(false, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (ShipCase entry : changed()) {
                    for (int[] bases : new int[][]{{1, 1}, {77, 33}}) {
                        BasicEntityShip source = entry.create(helper, entities);
                        CompoundTag saved = savedFixture(source, SHIP_UID);
                        setSavedBases(saved, bases[0], bases[1]);
                        BasicEntityShip loaded = entry.create(helper, entities);
                        loaded.readAdditionalSaveData(saved);
                        assertConfig(helper, loaded, entry.index());
                        assertSavedFixture(helper, loaded, saved);
                        CompoundTag normalized = new CompoundTag();
                        loaded.addAdditionalSaveData(normalized);
                        helper.assertTrue(normalized.getIntArray("StateMinor")[ID.M.GrudgeCon]
                                        == ConfigHandler.consumeGrudgeShip[entry.index()]
                                        && normalized.getIntArray("StateMinor")[ID.M.AmmoCon]
                                        == ConfigHandler.consumeAmmoShip[entry.index()],
                                "Re-save did not store normalized consumption");
                        BasicEntityShip again = entry.create(helper, entities);
                        again.readAdditionalSaveData(normalized);
                        assertConfig(helper, again, entry.index());
                        assertSavedFixture(helper, again, saved);
                    }
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void savedEggRestoresClassConsumptionAndIdentity(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> guarded(helper, () -> withConfig(false, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (ShipCase entry : changed()) {
                    BasicEntityShip source = entry.create(helper, entities);
                    CompoundTag saved = savedFixture(source, SHIP_UID);
                    setSavedBases(saved, 1, 1);
                    BasicEntityShip loaded = hatch(helper, entities, saved);
                    helper.assertTrue(loaded.getClass() == source.getClass(),
                            "Egg spawned the wrong concrete class");
                    assertConfig(helper, loaded, entry.index());
                    assertSavedFixture(helper, loaded, saved);
                    loaded.discard();
                }
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(SHIP_UID);
            }
        })), SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyAndShortLoadsUseClassConfig(GameTestHelper helper) {
        withConfig(false, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CompoundTag minor = new CompoundTag();
                minor.putInt("Level", 37);
                minor.putInt("NumGrudge", 503);
                minor.putInt("NumAmmoL", 127);
                minor.putInt("NumAmmoH", 83);
                CompoundTag props = new CompoundTag();
                props.put("Minor", minor);
                CompoundTag legacy = new CompoundTag();
                legacy.put("ShipExtProps", props);
                for (ShipCase entry : changed()) {
                    BasicEntityShip ship = entry.create(helper, entities);
                    CompoundTag shortSave = new CompoundTag();
                    int[] shortMinor = java.util.Arrays.copyOf(ship.getStateMinorArray(), ID.M.GrudgeCon);
                    shortMinor[ID.M.ShipLevel] = 37;
                    shortMinor[ID.M.NumGrudge] = 503;
                    shortMinor[ID.M.NumAmmoLight] = 127;
                    shortMinor[ID.M.NumAmmoHeavy] = 83;
                    shortSave.putIntArray("StateMinor", shortMinor);
                    for (CompoundTag saved : List.of(legacy, shortSave, new CompoundTag())) {
                        ship.setGrudgeConsumption(77);
                        ship.setAmmoConsumption(33);
                        CapaShipSavedValues.loadNBTData(saved, ship);
                        assertConfig(helper, ship, entry.index());
                        helper.assertTrue(ship.getLevel() == 37 && ship.getGrudge() == 503
                                        && ship.getStateMinor(ID.M.NumAmmoLight) == 127
                                        && ship.getStateMinor(ID.M.NumAmmoHeavy) == 83,
                                "Legacy, short or missing state changed unrelated saved data");
                    }
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void repeatedLoadUsesLatestConfigIncludingZero(GameTestHelper helper) {
        withConfig(false, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (ShipCase entry : changed()) {
                    BasicEntityShip ship = entry.create(helper, entities);
                    CompoundTag saved = savedFixture(ship, SHIP_UID);
                    setSavedBases(saved, 77, 33);
                    // Saved classification is not the authority for the Java class's defaults.
                    int[] minors = saved.getIntArray("StateMinor");
                    minors[ID.M.ShipClass] = ID.ShipClass.DDAkatsuki;
                    minors[ID.M.ShipType] = ID.ShipType.DESTROYER;
                    saved.putIntArray("StateMinor", minors);
                    for (int[] bases : new int[][]{{20 + entry.index(), 10 + entry.index()}, {0, 0}, {1, 1}, {41, 19}}) {
                        int oldGrudge = ship.getGrudgeConsumption();
                        int oldAmmo = ship.getAmmoConsumption();
                        ConfigHandler.consumeGrudgeShip[entry.index()] = bases[0];
                        ConfigHandler.consumeAmmoShip[entry.index()] = bases[1];
                        assertBases(helper, ship, oldGrudge, oldAmmo);
                        CapaShipSavedValues.loadNBTData(saved, ship);
                        assertBases(helper, ship, bases[0], bases[1]);
                    }
                }
                for (ShipCase entry : unchanged()) {
                    BasicEntityShip ship = entry.create(helper, entities);
                    CompoundTag saved = savedFixture(ship, SHIP_UID);
                    setSavedBases(saved, 37, 17);
                    CapaShipSavedValues.loadNBTData(saved, ship);
                    CapaShipSavedValues.loadNBTData(saved, ship);
                    assertBases(helper, ship, 37, 17);
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void idleConsumptionUsesConfiguredBase(GameTestHelper helper) {
        withConfig(true, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (ShipCase entry : List.of(new ShipCase(ModEntities.BB_RE::get, ID.ShipConsume.BBV),
                        new ShipCase(ModEntities.AP_WA::get, ID.ShipConsume.AP))) {
                    BasicEntityShip ship = entry.create(helper, entities);
                    combatResources(helper, ship);
                    ship.setPos(helper.absoluteVec(SPAWN));
                    ship.tickCount = 256;
                    for (String axis : List.of("X", "Y", "Z")) {
                        double position = switch (axis) {
                            case "X" -> ship.getX();
                            case "Y" -> ship.getY();
                            default -> ship.getZ();
                        };
                        field(BasicEntityShip.class, ship, "ShipPrev" + axis, position);
                    }
                    int cost = entry.index() == ID.ShipConsume.AP ? 3 : 14;
                    int experience = ship.getStateMinor(ID.M.ExpCurrent);
                    for (int repeat = 0; repeat < 2; repeat++) {
                        helper.assertTrue((double) field(BasicEntityShip.class, ship, "ShipPrevX") == ship.getX()
                                        && (double) field(BasicEntityShip.class, ship, "ShipPrevY") == ship.getY()
                                        && (double) field(BasicEntityShip.class, ship, "ShipPrevZ") == ship.getZ(),
                                "Idle fixture has nonzero sampled distance");
                        int grudge = ship.getGrudge();
                        invoke(BasicEntityShip.class, ship, "updateConsumeItem");
                        helper.assertTrue(grudge - ship.getGrudge() == cost, "Incorrect idle consumption for " + ship.getType());
                        helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight) == 200
                                        && ship.getStateMinor(ID.M.NumAmmoHeavy) == 200
                                        && ship.getStateMinor(ID.M.ExpCurrent) == experience,
                                "Idle consumption changed ammo or awarded distance experience");
                    }
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void attacksAndAircraftUseConfiguredAmmoBase(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> guarded(helper, () -> withConfig(true, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip re = new ShipCase(ModEntities.BB_RE::get, ID.ShipConsume.BBV).create(helper, entities);
                BasicEntityShipCV carrier = (BasicEntityShipCV)
                        new ShipCase(ModEntities.CV_HIME::get, ID.ShipConsume.CV).create(helper, entities);
                LivingEntity target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                helper.assertTrue(target != null, "Could not create attack target");
                target.setInvulnerable(true);
                re.setPos(helper.absoluteVec(SPAWN));
                carrier.setPos(re.position());
                target.setPos(re.position().add(3D, 0D, 0D));
                combatResources(helper, re);
                combatResources(helper, carrier);
                Set<UUID> before = new HashSet<>();
                helper.getLevel().getAllEntities().forEach(entity -> before.add(entity.getUUID()));
                try {
                    re.attackEntityWithAmmo(target);
                    assertCost(helper, re, 4, 0, 4);
                    re.attackEntityWithHeavyAmmo(target);
                    assertCost(helper, re, 4, 4, 12);
                    carrier.setNumAircraftLight(2);
                    carrier.setNumAircraftHeavy(2);
                    helper.assertTrue(carrier.getNumAircraftLight() == 2 && carrier.getNumAircraftHeavy() == 2,
                            "Aircraft fixture was ignored");
                    helper.assertTrue(carrier.attackEntityWithAircraft(target), "Light aircraft did not launch");
                    assertCost(helper, carrier, 18, 0, 6);
                    helper.assertTrue(carrier.attackEntityWithHeavyAircraft(target), "Heavy aircraft did not launch");
                    assertCost(helper, carrier, 18, 6, 18);
                    helper.assertTrue(carrier.getNumAircraftLight() == 1 && carrier.getNumAircraftHeavy() == 1,
                            "Launch did not consume aircraft slots");
                    helper.assertTrue(target.isAlive(), "Attack target died");
                } finally {
                    for (Entity entity : helper.getLevel().getAllEntities()) {
                        if (!before.contains(entity.getUUID())) entities.add(entity);
                    }
                }
                helper.succeed();
            }
        })), SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void aircraftReturnUsesHostConsumptionBase(GameTestHelper helper) {
        withConfig(true, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShipCV host = (BasicEntityShipCV)
                        new ShipCase(ModEntities.CV_HIME::get, ID.ShipConsume.CV).create(helper, entities);
                combatResources(helper, host);
                BasicEntityAirplane plane = entities.add(ModEntities.AIRPLANE.get().create(helper.getLevel()));
                helper.assertTrue(plane != null, "Could not create airplane");
                plane.initAttrs(host, null, 0, 2F);
                field(BasicEntitySummon.class, plane, "numAmmoLight", 9);
                field(BasicEntitySummon.class, plane, "numAmmoHeavy", 3);
                helper.assertTrue((int) field(BasicEntitySummon.class, plane, "numAmmoLight") == 9
                                && (int) field(BasicEntitySummon.class, plane, "numAmmoHeavy") == 3,
                        "Airplane remaining ammo fixture was ignored");
                invoke(BasicEntityAirplane.class, plane, "returnSummonResource");
                helper.assertTrue(host.getStateMinor(ID.M.NumAmmoLight) == 218
                                && host.getStateMinor(ID.M.NumAmmoHeavy) == 206,
                        "Aircraft return did not use the host's ammo base");
                BasicEntityShip shimakaze = new ShipCase(ModEntities.DESTROYER_SHIMAKAZE::get, ID.ShipConsume.DD)
                        .create(helper, entities);
                combatResources(helper, shimakaze);
                EntityRensouhou turret = entities.add(ModEntities.RENSOUHOU.get().create(helper.getLevel()));
                helper.assertTrue(turret != null, "Could not create turret");
                turret.initAttrs(shimakaze, null, 0);
                field(BasicEntitySummon.class, turret, "numAmmoLight", 6);
                helper.assertTrue((int) field(BasicEntitySummon.class, turret, "numAmmoLight") == 6,
                        "Turret remaining ammo fixture was ignored");
                invoke(EntityRensouhou.class, turret, "returnSummonResource");
                helper.assertTrue(shimakaze.getStateMinor(ID.M.NumAmmoLight) == 204
                                && shimakaze.getStateMinor(ID.M.NumAmmoHeavy) == 200,
                        "Turret return no longer excludes its two bonus shots");
                helper.succeed();
            }
        });
    }

    /** Config mutation never crosses a tick or callback boundary. */
    private static void withConfig(boolean defaults, Runnable body) {
        int[] grudge = ConfigHandler.consumeGrudgeShip.clone();
        int[] ammo = ConfigHandler.consumeAmmoShip.clone();
        int[] action = ConfigHandler.consumeGrudgeAction.clone();
        try {
            int[] defaultGrudge = {5, 7, 8, 9, 8, 11, 12, 15, 14, 4, 3};
            int[] defaultAmmo = {1, 2, 2, 2, 2, 3, 3, 4, 4, 1, 1};
            for (int i = 0; i < grudge.length; i++) {
                ConfigHandler.consumeGrudgeShip[i] = defaults ? defaultGrudge[i] : 20 + i;
                ConfigHandler.consumeAmmoShip[i] = defaults ? defaultAmmo[i] : 10 + i;
            }
            System.arraycopy(new int[]{4, 8, 6, 12, 3}, 0, ConfigHandler.consumeGrudgeAction, 0, action.length);
            body.run();
        } finally {
            System.arraycopy(grudge, 0, ConfigHandler.consumeGrudgeShip, 0, grudge.length);
            System.arraycopy(ammo, 0, ConfigHandler.consumeAmmoShip, 0, ammo.length);
            System.arraycopy(action, 0, ConfigHandler.consumeGrudgeAction, 0, action.length);
        }
    }

    private static void assertConfig(GameTestHelper helper, BasicEntityShip ship, int index) {
        assertBases(helper, ship, ConfigHandler.consumeGrudgeShip[index], ConfigHandler.consumeAmmoShip[index]);
    }

    private static void assertBases(GameTestHelper helper, BasicEntityShip ship, int grudge, int ammo) {
        helper.assertTrue(ship.getGrudgeConsumption() == grudge && ship.getAmmoConsumption() == ammo,
                ship.getType() + ": expected bases " + grudge + "/" + ammo + ", found "
                        + ship.getGrudgeConsumption() + "/" + ship.getAmmoConsumption());
    }

    private static void setSavedBases(CompoundTag saved, int grudge, int ammo) {
        int[] minor = saved.getIntArray("StateMinor");
        minor[ID.M.GrudgeCon] = grudge;
        minor[ID.M.AmmoCon] = ammo;
        saved.putIntArray("StateMinor", minor);
    }

    static CompoundTag savedFixture(BasicEntityShip ship, int uid) {
        ship.setOwnerUUID(OWNER);
        ship.ownerName = "consumption_owner";
        ship.setPlayerUID(741_001);
        ship.setShipUID(uid);
        ship.setStateMinor(ID.M.ShipLevel, 37);
        ship.setStateMinor(ID.M.ExpCurrent, 123);
        ship.setStateMinor(ID.M.NumGrudge, 503);
        ship.setStateMinor(ID.M.NumAmmoLight, 127);
        ship.setStateMinor(ID.M.NumAmmoHeavy, 83);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.getCapaShipInventory().setStackInSlot(0, new ItemStack(ModItems.EQUIP_SEARCHLIGHT.get()));
        ship.getCapaShipInventory().setStackInSlot(CapaShipInventory.EquipSlots, new ItemStack(Items.DIAMOND, 7));
        CompoundTag saved = new CompoundTag();
        ship.addAdditionalSaveData(saved);
        return saved;
    }

    private static void assertSavedFixture(GameTestHelper helper, BasicEntityShip ship, CompoundTag saved) {
        int[] minor = saved.getIntArray("StateMinor");
        for (int id : new int[]{ID.M.ShipLevel, ID.M.ExpCurrent, ID.M.NumGrudge, ID.M.NumAmmoLight,
                ID.M.NumAmmoHeavy, ID.M.ShipUID, ID.M.PlayerUID}) {
            helper.assertTrue(ship.getStateMinor(id) == minor[id], "Saved value changed at index " + id);
        }
        helper.assertTrue(OWNER.equals(ship.getOwnerUUID()) && "consumption_owner".equals(ship.ownerName),
                "Original owner UUID or name changed");
        helper.assertTrue(saved.getCompound(CapaShipInventory.InvName).equals(ship.getCapaShipInventory().serializeNBT()),
                "Equipment or cargo changed during load");
    }

    static BasicEntityShip hatch(GameTestHelper helper, GameTestEntities entities, CompoundTag saved) {
        CompoundTag eggNbt = saved.copy();
        eggNbt.putInt("ShipClass", saved.getIntArray("StateMinor")[ID.M.ShipClass]);
        eggNbt.putString("owner", OWNER.toString());
        eggNbt.putString("ownername", "consumption_owner");
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(SPAWNER, "other_admiral"));
        helper.assertTrue(!OWNER.equals(player.getUUID()), "Egg user must not be the saved owner");
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        egg.setTag(eggNbt);
        BlockPos spawn = BlockPos.containing(helper.absoluteVec(SPAWN));
        BlockPos floor = spawn.below();
        boolean creative = player.getAbilities().instabuild;
        InteractionResult result;
        try {
            player.getAbilities().instabuild = true;
            result = egg.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    egg, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        } finally {
            player.getAbilities().instabuild = creative;
        }
        helper.assertTrue(result == InteractionResult.CONSUME, "Saved egg failed: " + result);
        List<BasicEntityShip> ships = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class, new AABB(spawn));
        helper.assertTrue(ships.size() == 1, "Expected one saved egg ship, found " + ships.size());
        BasicEntityShip ship = entities.add(ships.get(0));
        ship.setNoAi(true);
        ship.setInvulnerable(true);
        return ship;
    }

    private static void combatResources(GameTestHelper helper, BasicEntityShip ship) {
        ship.calcShipAttributes(31, false);
        ship.setStateMinor(ID.M.NumGrudge, 1000);
        ship.setStateMinor(ID.M.NumAmmoLight, 200);
        ship.setStateMinor(ID.M.NumAmmoHeavy, 200);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.removeEffect(MobEffects.HUNGER);
        helper.assertTrue(ship.getGrudge() == 1000 && !ship.getStateFlag(ID.F.NoFuel)
                        && ship.getStateMinor(ID.M.NumAmmoLight) == 200
                        && ship.getStateMinor(ID.M.NumAmmoHeavy) == 200 && !ship.hasEffect(MobEffects.HUNGER),
                "Resource fixture was ignored");
        for (int i = 0; i < ship.getCapaShipInventory().getSlots(); i++) {
            helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(i).isEmpty(), "Combat inventory must be empty");
        }
    }

    private static void assertCost(GameTestHelper helper, BasicEntityShip ship, int light, int heavy, int grudge) {
        helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight) == 200 - light
                        && ship.getStateMinor(ID.M.NumAmmoHeavy) == 200 - heavy && ship.getGrudge() == 1000 - grudge,
                "Incorrect attack cost for " + ship.getType() + ": remaining " + ship.getStateMinor(ID.M.NumAmmoLight)
                        + "/" + ship.getStateMinor(ID.M.NumAmmoHeavy) + "/" + ship.getGrudge());
    }

    private static Object field(Class<?> type, Object object, String name, Object... value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            if (value.length != 0) field.set(object, value[0]);
            return field.get(object);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Cannot access fixture field " + name + ": " + failure);
        }
    }

    private static void invoke(Class<?> type, Object object, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(object);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Cannot invoke " + name + ": " + failure);
        }
    }

    static void guarded(GameTestHelper helper, Runnable body) {
        try {
            body.run();
        } catch (GameTestAssertException failure) {
            throw failure;
        } catch (RuntimeException | AssertionError failure) {
            helper.fail("Fixture failed: " + failure);
        }
    }
}
