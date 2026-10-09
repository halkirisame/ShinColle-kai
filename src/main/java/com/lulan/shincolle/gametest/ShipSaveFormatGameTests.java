package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommandState;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pins the saved form of an owned ship: which keys are written, how long the state arrays are,
 * and that a save written in an older form still loads to the same ship.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipSaveFormatGameTests {

    private static final int MINOR_LENGTH = 45;
    private static final int FLAG_LENGTH = 27;
    private static final int EMOTION_LENGTH = 8;

    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final UUID GUARDED_UUID = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
    private static final CommandPos GUARD_POS = new CommandPos(120, 64, -45);
    private static final CommandPos MOVE_POS = new CommandPos(8, 70, 9);

    private static final String OWNER_NAME = "fixture_admiral";
    private static final int OWNER_UID = 254_001;
    private static final int SHIP_UID = 2_540_001;
    private static final int EGG_SHIP_UID = 2_540_101;
    private static final int DEATH_SHIP_UID = 2_540_201;
    private static final String VERSION_KEY = "AiDataVersion";
    private static final byte[] ATTRS_BONUS = {1, 2, 3, 0, 1, 2};
    private static final Vec3 EGG_SPAWN = new Vec3(1.5D, 2D, 1.5D);

    private ShipSaveFormatGameTests() {
    }

    /** The command fields a save carries, as the ship holds them after a load. */
    private record Guard(int x, int y, int z, int dim, int type, int id, ResourceKey<Level> dimension,
                         UUID entity, boolean canFollow, boolean release, boolean sitting) {
        static Guard of(BasicEntityShip ship) {
            return new Guard(ship.getStateMinor(ID.M.GuardX), ship.getStateMinor(ID.M.GuardY),
                    ship.getStateMinor(ID.M.GuardZ), ship.getStateMinor(ID.M.GuardDim),
                    ship.getStateMinor(ID.M.GuardType), ship.getStateMinor(ID.M.GuardID),
                    ship.getGuardedDimension(), ship.getGuardedEntityUuid(),
                    ship.getStateFlag(ID.F.CanFollow), ship.shouldReleaseGuardOnArrival(),
                    ship.isOrderedToSit());
        }

        Guard withDimension(ResourceKey<Level> projected) {
            return new Guard(x, y, z, dim, type, id, projected, entity, canFollow, release, sitting);
        }
    }

    /** A saved ship and what it must load to. */
    private record Saved(String name, CompoundTag nbt, Guard loaded, MovementOrder order) {
    }

    /** A command put on a live ship and what its save must load to. */
    private record Command(String name, Consumer<BasicEntityShip> setup, Guard loaded, MovementOrder order) {
    }

    // ===== the saved form =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedShipDataHasFixedKeysAndArrayLengths(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);

            CompoundTag plain = new CompoundTag();
            CapaShipSavedValues.saveNBTData(plain, ship);
            assertKeys(helper, plain, Set.of("AiDataVersion", "StateMinor", "StateFlag", "StateEmotion",
                    "AttrsBonus", "OwnerName", "TextureID"), "unnamed ship");
            helper.assertTrue(plain.contains("StateMinor", Tag.TAG_INT_ARRAY)
                            && plain.getIntArray("StateMinor").length == MINOR_LENGTH,
                    "StateMinor must be an int array of " + MINOR_LENGTH);
            helper.assertTrue(plain.contains("StateFlag", Tag.TAG_BYTE_ARRAY)
                            && plain.getByteArray("StateFlag").length == FLAG_LENGTH,
                    "StateFlag must be a byte array of " + FLAG_LENGTH);
            helper.assertTrue(plain.contains("StateEmotion", Tag.TAG_INT_ARRAY)
                            && plain.getIntArray("StateEmotion").length == EMOTION_LENGTH,
                    "StateEmotion must be an int array of " + EMOTION_LENGTH);
            helper.assertTrue(plain.contains("AttrsBonus", Tag.TAG_BYTE_ARRAY)
                            && plain.contains("OwnerName", Tag.TAG_STRING)
                            && plain.contains("TextureID", Tag.TAG_INT),
                    "AttrsBonus, OwnerName and TextureID changed their saved types");

            ship.setCustomName(Component.literal("Verniy"));
            CompoundTag named = new CompoundTag();
            CapaShipSavedValues.saveNBTData(named, ship);
            assertKeys(helper, named, Set.of("AiDataVersion", "StateMinor", "StateFlag", "StateEmotion",
                    "AttrsBonus", "OwnerName", "TextureID", "CustomName"), "named ship");
            helper.assertTrue(named.contains("CustomName", Tag.TAG_STRING), "CustomName must be a string");

            CompoundTag idle = new CompoundTag();
            ship.addAdditionalSaveData(idle);
            helper.assertTrue(idle.contains("ReleaseGuardOnArrival", Tag.TAG_BYTE)
                            && !idle.contains("GuardEntityUUID") && !idle.contains("GuardDimension"),
                    "A ship without a guard target must save only the release flag beside its state");

            ship.setGuardedPos(-1, -1, -1, helper.getLevel().dimension(), 2);
            ship.projectGuardIdentity(GUARDED_UUID, helper.getLevel().dimension());
            CompoundTag guarding = new CompoundTag();
            ship.addAdditionalSaveData(guarding);
            helper.assertTrue(guarding.hasUUID("GuardEntityUUID")
                            && GUARDED_UUID.equals(guarding.getUUID("GuardEntityUUID"))
                            && guarding.contains("GuardDimension", Tag.TAG_STRING)
                            && "minecraft:overworld".equals(guarding.getString("GuardDimension")),
                    "A ship guarding an entity must save the entity UUID and its dimension");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedShipDataCarriesVersionMark(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities);

            CompoundTag state = new CompoundTag();
            CapaShipSavedValues.saveNBTData(state, ship);
            assertMarked(helper, state, "state save");

            CompoundTag entity = new CompoundTag();
            ship.addAdditionalSaveData(entity);
            assertMarked(helper, entity, "entity save");

            CompoundTag copy = new CompoundTag();
            ship.saveWithoutId(copy);
            assertMarked(helper, copy, "whole-entity copy");
            helper.succeed();
        }
    }

    // ===== saves written by the running code =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedCommandsReloadInLegacy(GameTestHelper helper) {
        savedCommandsReload(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedCommandsReloadInNew(GameTestHelper helper) {
        savedCommandsReload(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    private static void savedCommandsReload(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority authority) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (Command command : commands()) {
                CompoundTag saved = new CompoundTag();
                try (var legacy = ShipAiAuthorityOverride.use(ConfigHandler.ShipAiTargetAuthority.LEGACY)) {
                    BasicEntityShip source = ship(helper, entities);
                    command.setup().accept(source);
                    source.addAdditionalSaveData(saved);
                }
                assertMarked(helper, saved, command.name());
                CompoundTag unmarked = saved.copy();
                unmarked.remove(VERSION_KEY);
                try (var scoped = ShipAiAuthorityOverride.use(authority)) {
                    BasicEntityShip loaded = ship(helper, entities);
                    loaded.readAdditionalSaveData(saved);
                    assertCommand(helper, loaded, authority, command.loaded(), command.order(), command.name());

                    BasicEntityShip fromUnmarked = ship(helper, entities);
                    fromUnmarked.readAdditionalSaveData(unmarked);
                    String label = command.name() + " without the mark";
                    assertCommand(helper, fromUnmarked, authority, command.loaded(), command.order(), label);
                    assertSameShip(helper, loaded, fromUnmarked, label);
                }
            }
            helper.succeed();
        }
    }

    private static List<Command> commands() {
        return List.of(
                new Command("follow", ship -> ship.setStateFlag(ID.F.CanFollow, true),
                        new Guard(-1, -1, -1, 0, 0, -1, null, null, true, false, false),
                        new MovementOrder.Follow()),
                new Command("move", ship -> {
                    ship.setGuardedPos(MOVE_POS.x(), MOVE_POS.y(), MOVE_POS.z(), Level.OVERWORLD, 0);
                    ship.setStateFlag(ID.F.CanFollow, false);
                    ship.setReleaseGuardOnArrival(true);
                }, new Guard(MOVE_POS.x(), MOVE_POS.y(), MOVE_POS.z(), 0, 0, -1, Level.OVERWORLD, null,
                        false, true, false),
                        new MovementOrder.MoveTo(OVERWORLD, MOVE_POS, true)),
                new Command("guard position", ship -> {
                    ship.setGuardedPos(GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), Level.NETHER, 1);
                    ship.setStateFlag(ID.F.CanFollow, false);
                }, new Guard(GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), -1, 1, -1, Level.NETHER, null,
                        false, false, false),
                        new MovementOrder.GuardPosition(NETHER, GUARD_POS, false)),
                new Command("guard entity", ship -> {
                    ship.setGuardedPos(-1, -1, -1, Level.OVERWORLD, 2);
                    ship.projectGuardIdentity(GUARDED_UUID, Level.OVERWORLD);
                    // the runtime entity id is written with the array and must not come back
                    ship.setStateMinor(ID.M.GuardID, 4242);
                    ship.setStateFlag(ID.F.CanFollow, false);
                }, new Guard(-1, -1, -1, 0, 2, -1, Level.OVERWORLD, GUARDED_UUID, false, false, false),
                        new MovementOrder.GuardEntity(new TargetHandle(GUARDED_UUID, OVERWORLD))),
                new Command("sitting", ship -> {
                    ship.setStateFlag(ID.F.CanFollow, true);
                    ship.setEntitySit(true);
                }, new Guard(-1, -1, -1, 0, 0, -1, null, null, true, false, true),
                        new MovementOrder.Follow()));
    }

    // ===== the nested save of the original mod =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void nestedLegacySaveLoads(GameTestHelper helper) {
        Guard expected = new Guard(10, 60, -7, 0, 1, -1, null, null, false, false, false);
        MovementOrder order = new MovementOrder.GuardPosition(OVERWORLD, new CommandPos(10, 60, -7), false);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (ConfigHandler.ShipAiTargetAuthority authority : ConfigHandler.ShipAiTargetAuthority.values()) {
                try (var scoped = ShipAiAuthorityOverride.use(authority)) {
                    BasicEntityShip ship = ship(helper, entities);
                    CompoundTag nested = nestedLegacySave();
                    helper.assertTrue(!nested.contains(VERSION_KEY), "The nested save must start without a mark");
                    ship.readAdditionalSaveData(nested);
                    String label = "nested save (" + authority + ")";
                    helper.assertTrue(ship.getLevel() == 25 && ship.getStateMinor(ID.M.NumGrudge) == 200,
                            label + ": level and grudge were not read");
                    helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == 4
                                    && ship.getStateMinor(ID.M.FollowMax) == 18
                                    && ship.getStateMinor(ID.M.FleeHP) == 40,
                            label + ": follow range and flee HP were not read");
                    helper.assertTrue(ship.getStateMinor(ID.M.FormatPos) == 2
                                    && ship.getStateMinor(ID.M.Task) == 1
                                    && ship.getStateMinor(ID.M.TaskSide) == 3,
                            label + ": formation slot and task were not read");
                    assertFormationRead(helper, entities, nestedLegacySave(), 1, 2, label);
                    helper.assertTrue(ship.getStateFlag(ID.F.IsMarried) && "legacy_admiral".equals(ship.ownerName),
                            label + ": marriage flag and owner name were not read");
                    assertUndimensionedCommand(helper, ship, authority, expected, order, Level.OVERWORLD, label);
                    CompoundTag again = new CompoundTag();
                    ship.addAdditionalSaveData(again);
                    assertMarked(helper, again, label + " saved again");
                }
            }
            helper.succeed();
        }
    }

    private static CompoundTag nestedLegacySave() {
        CompoundTag minor = new CompoundTag();
        minor.putInt("Level", 25);
        minor.putInt("NumGrudge", 200);
        minor.putInt("FMin", 4);
        minor.putInt("FMax", 18);
        minor.putInt("FHP", 40);
        minor.putInt("GuardX", 10);
        minor.putInt("GuardY", 60);
        minor.putInt("GuardZ", -7);
        minor.putInt("GuardDim", 0);
        minor.putInt("GuardID", 55);
        minor.putInt("GuardType", 1);
        minor.putInt("FType", 1);
        minor.putInt("FPos", 2);
        minor.putInt("Task", 1);
        minor.putInt("Side", 3);
        CompoundTag flags = new CompoundTag();
        flags.putBoolean("CanFollow", false);
        flags.putBoolean("IsMarried", true);
        flags.putBoolean("NoFuel", false);
        CompoundTag props = new CompoundTag();
        props.put("Minor", minor);
        props.put("ShipFlags", flags);
        props.putString("Owner", "legacy_admiral");
        CompoundTag nbt = new CompoundTag();
        nbt.put("ShipExtProps", props);
        return nbt;
    }

    // ===== saves without a version mark, written value by value =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unmarkedEntitySaveLoadsInLegacy(GameTestHelper helper) {
        unmarkedEntitySaveLoads(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unmarkedEntitySaveLoadsInNew(GameTestHelper helper) {
        unmarkedEntitySaveLoads(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    private static void unmarkedEntitySaveLoads(GameTestHelper helper,
                                                ConfigHandler.ShipAiTargetAuthority authority) {
        try (GameTestEntities entities = GameTestEntities.open(helper);
                var scoped = ShipAiAuthorityOverride.use(authority)) {
            for (Saved saved : unmarkedEntitySaves()) {
                BasicEntityShip ship = ship(helper, entities);
                ship.readAdditionalSaveData(saved.nbt().copy());
                assertSettings(helper, ship, SHIP_UID, saved.name());
                assertFormationRead(helper, entities, saved.nbt().copy(), 2, 3, saved.name());
                assertCommand(helper, ship, authority, saved.loaded(), saved.order(), saved.name());
            }
            helper.succeed();
        }
    }

    private static List<Saved> unmarkedEntitySaves() {
        CompoundTag follow = unmarkedState(SHIP_UID, -1, -1, -1, 0, 0, -1, true);
        follow.putBoolean("ReleaseGuardOnArrival", false);

        CompoundTag guardPosition = unmarkedState(SHIP_UID, GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(),
                -1, 1, -1, false);
        guardPosition.putString("GuardDimension", "minecraft:the_nether");
        guardPosition.putBoolean("ReleaseGuardOnArrival", false);

        CompoundTag guardEntity = unmarkedState(SHIP_UID, -1, -1, -1, 0, 2, 4242, false);
        guardEntity.putUUID("GuardEntityUUID", GUARDED_UUID);
        guardEntity.putString("GuardDimension", "minecraft:overworld");
        guardEntity.putBoolean("ReleaseGuardOnArrival", false);

        CompoundTag move = unmarkedState(SHIP_UID, MOVE_POS.x(), MOVE_POS.y(), MOVE_POS.z(), 0, 0, -1, false);
        move.putString("GuardDimension", "minecraft:overworld");
        move.putBoolean("ReleaseGuardOnArrival", true);

        // follow switched off with nothing to guard: the load turns follow back on
        CompoundTag cleared = unmarkedState(SHIP_UID, -1, -1, -1, 0, 0, 77, false);
        cleared.putBoolean("ReleaseGuardOnArrival", false);

        return List.of(
                new Saved("unmarked follow", follow,
                        new Guard(-1, -1, -1, 0, 0, -1, null, null, true, false, false),
                        new MovementOrder.Follow()),
                new Saved("unmarked guard position", guardPosition,
                        new Guard(GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), -1, 1, -1, Level.NETHER, null,
                                false, false, false),
                        new MovementOrder.GuardPosition(NETHER, GUARD_POS, false)),
                new Saved("unmarked guard entity", guardEntity,
                        new Guard(-1, -1, -1, 0, 2, -1, Level.OVERWORLD, GUARDED_UUID, false, false, false),
                        new MovementOrder.GuardEntity(new TargetHandle(GUARDED_UUID, OVERWORLD))),
                new Saved("unmarked move", move,
                        new Guard(MOVE_POS.x(), MOVE_POS.y(), MOVE_POS.z(), 0, 0, -1, Level.OVERWORLD, null,
                                false, true, false),
                        new MovementOrder.MoveTo(OVERWORLD, MOVE_POS, true)),
                new Saved("unmarked cleared guard", cleared,
                        new Guard(-1, -1, -1, 0, 0, -1, null, null, true, false, false),
                        new MovementOrder.Follow()));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unmarkedEggRestoresInLegacy(GameTestHelper helper) {
        unmarkedEggRestores(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unmarkedEggRestoresInNew(GameTestHelper helper) {
        unmarkedEggRestores(helper, ConfigHandler.ShipAiTargetAuthority.NEW);
    }

    private static void unmarkedEggRestores(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority authority) {
        GameTestEntities.whenPositionsTicking(helper, () -> guarded(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper);
                    var scoped = ShipAiAuthorityOverride.use(authority)) {
                // An egg carries neither the guard dimension nor the release flag, so a position
                // guard comes back with the numeric dimension only.
                Guard follow = new Guard(-1, -1, -1, 0, 0, -1, null, null, true, false, false);
                Guard guard = new Guard(GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), -1, 1, -1, null, null,
                        false, false, false);
                List<Saved> eggs = List.of(
                        new Saved("unmarked follow egg",
                                unmarkedEgg(unmarkedState(EGG_SHIP_UID, -1, -1, -1, 0, 0, -1, true)),
                                follow, new MovementOrder.Follow()),
                        new Saved("unmarked guard egg",
                                unmarkedEgg(unmarkedState(EGG_SHIP_UID, GUARD_POS.x(), GUARD_POS.y(),
                                        GUARD_POS.z(), -1, 1, -1, false)),
                                guard, new MovementOrder.GuardPosition(NETHER, GUARD_POS, false)));
                for (Saved egg : eggs) {
                    BasicEntityShip ship = hatch(helper, entities, egg.nbt());
                    assertSettings(helper, ship, EGG_SHIP_UID, egg.name());
                    assertFormationRead(helper, entities, egg.nbt().copy(), 2, 3, egg.name());
                    helper.assertTrue(eggOwner(helper).getUUID().equals(ship.getOwnerUUID()),
                            egg.name() + ": the saved owner was not restored");
                    assertUndimensionedCommand(helper, ship, authority, egg.loaded(), egg.order(),
                            egg.order() instanceof MovementOrder.GuardPosition ? Level.NETHER : null, egg.name());
                    ship.discard();
                }
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(EGG_SHIP_UID);
            }
        }), EGG_SPAWN);
    }

    // ===== marks this build does not write =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unknownVersionMarkStillLoadsAndIsRewritten(GameTestHelper helper) {
        Guard expected = new Guard(GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), -1, 1, -1, Level.NETHER, null,
                false, false, false);
        MovementOrder order = new MovementOrder.GuardPosition(NETHER, GUARD_POS, false);
        List<Consumer<CompoundTag>> marks = List.of(
                nbt -> nbt.putInt(VERSION_KEY, 99),
                nbt -> nbt.putInt(VERSION_KEY, 0),
                nbt -> nbt.putInt(VERSION_KEY, -5),
                nbt -> nbt.putString(VERSION_KEY, "2"));
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (ConfigHandler.ShipAiTargetAuthority authority : ConfigHandler.ShipAiTargetAuthority.values()) {
                try (var scoped = ShipAiAuthorityOverride.use(authority)) {
                    for (Consumer<CompoundTag> mark : marks) {
                        CompoundTag nbt = unmarkedState(SHIP_UID, GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(),
                                -1, 1, -1, false);
                        nbt.putString("GuardDimension", "minecraft:the_nether");
                        nbt.putBoolean("ReleaseGuardOnArrival", false);
                        mark.accept(nbt);
                        String label = "mark " + nbt.get(VERSION_KEY) + " (" + authority + ")";

                        BasicEntityShip ship = ship(helper, entities);
                        ship.readAdditionalSaveData(nbt);
                        assertSettings(helper, ship, SHIP_UID, label);
                        assertCommand(helper, ship, authority, expected, order, label);

                        CompoundTag again = new CompoundTag();
                        ship.addAdditionalSaveData(again);
                        assertMarked(helper, again, label + " saved again");
                    }
                }
            }
            helper.succeed();
        }
    }

    // ===== the egg a dying ship leaves =====

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deathEggIsMarkedAndRestoresLikeUnmarkedEggInLegacy(GameTestHelper helper) {
        deathEggRestores(helper, ConfigHandler.ShipAiTargetAuthority.LEGACY, DEATH_SHIP_UID);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deathEggIsMarkedAndRestoresLikeUnmarkedEggInNew(GameTestHelper helper) {
        deathEggRestores(helper, ConfigHandler.ShipAiTargetAuthority.NEW, DEATH_SHIP_UID + 1);
    }

    private static void deathEggRestores(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority authority,
                                         int shipUid) {
        GameTestEntities.whenPositionsTicking(helper, () -> guarded(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper);
                    var scoped = ShipAiAuthorityOverride.use(authority)) {
                CompoundTag egg = deathEgg(helper, entities, shipUid);
                assertMarked(helper, egg, "death egg");
                CompoundTag unmarked = egg.copy();
                unmarked.remove(VERSION_KEY);

                Guard expected = new Guard(GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), -1, 1, -1, null, null,
                        false, false, false);
                MovementOrder order = new MovementOrder.GuardPosition(NETHER, GUARD_POS, false);

                BasicEntityShip marked = hatch(helper, entities, egg);
                assertSettings(helper, marked, shipUid, "death egg");
                assertUndimensionedCommand(helper, marked, authority, expected, order, Level.NETHER, "death egg");
                marked.discard();

                BasicEntityShip plain = hatch(helper, entities, unmarked);
                assertSettings(helper, plain, shipUid, "death egg without the mark");
                assertUndimensionedCommand(helper, plain, authority, expected, order, Level.NETHER,
                        "death egg without the mark");
                assertSameShip(helper, marked, plain, "death egg without the mark");
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(shipUid);
            }
        }), EGG_SPAWN);
    }

    /** Lets a ship holding a position guard reach the end of its death and returns the egg it drops. */
    private static CompoundTag deathEgg(GameTestHelper helper, GameTestEntities entities, int shipUid) {
        CompoundTag state = unmarkedState(shipUid, GUARD_POS.x(), GUARD_POS.y(), GUARD_POS.z(), -1, 1, -1, false);
        state.putString("GuardDimension", "minecraft:the_nether");
        state.putBoolean("ReleaseGuardOnArrival", false);
        BasicEntityShip ship = ship(helper, entities);
        ship.readAdditionalSaveData(state);
        Vec3 position = helper.absoluteVec(EGG_SPAWN);
        ship.moveTo(position.x, position.y, position.z, 0F, 0F);
        ship.setNoAi(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Could not add the dying ship");
        helper.assertTrue(ship.getStateFlag(ID.F.CanDrop), "The dying ship must be allowed to drop its egg");

        ship.deathTime = ConfigHandler.deathTime() - 1;
        try {
            Method tickDeath = BasicEntityShip.class.getDeclaredMethod("tickDeath");
            tickDeath.setAccessible(true);
            tickDeath.invoke(ship);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not advance the ship's death", failure);
        }
        helper.assertTrue(ship.isRemoved(), "The ship must be gone once its death ends");

        List<BasicEntityItem> eggs = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                new AABB(BlockPos.containing(position)).inflate(1.5D),
                item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
        helper.assertTrue(eggs.size() == 1, "Expected one death egg, found " + eggs.size());
        BasicEntityItem dropped = entities.add(eggs.get(0));
        CompoundTag tag = dropped.getEntityItem().getTag();
        helper.assertTrue(tag != null && tag.contains("StateMinor", Tag.TAG_INT_ARRAY)
                        && tag.getInt("ShipClass") == ID.ShipClass.DDHibiki,
                "The death egg does not hold the ship's saved state");
        CompoundTag copy = tag.copy();
        dropped.discard();
        return copy;
    }

    // ===== fixtures =====

    /**
     * The seven state keys of a ship saved before the version mark existed. Every value is
     * written here rather than taken from the running save code, so the form stays fixed.
     */
    private static CompoundTag unmarkedState(int shipUid, int guardX, int guardY, int guardZ, int guardDim,
                                             int guardType, int guardId, boolean canFollow) {
        int[] minor = {
                37, 5, 120, 40, 300,
                150, 500, 0, 0, 3,
                5, 20, 50, 1, guardX,
                guardY, guardZ, guardDim, guardId, ID.ShipType.DESTROYER,
                ID.ShipClass.DDHibiki, OWNER_UID, shipUid, -1, guardType,
                0, 2, 3, 0, 0,
                3000, 0, 10, 0, 0,
                -1, 0, 0, 0, 0,
                2, 5, -1, 0, 2
        };
        byte[] flags = {
                0, 1, 0, 0, 1,
                1, 1, 1, 0, 1,
                1, (byte) (canFollow ? 1 : 0), 1, 1, 1,
                1, 1, 1, 1, 0,
                0, 0, 1, 1, 0,
                1, 0
        };
        CompoundTag nbt = new CompoundTag();
        nbt.putIntArray("StateMinor", minor);
        nbt.putByteArray("StateFlag", flags);
        nbt.putIntArray("StateEmotion", new int[EMOTION_LENGTH]);
        nbt.putByteArray("AttrsBonus", ATTRS_BONUS.clone());
        nbt.putString("OwnerName", OWNER_NAME);
        nbt.putInt("TextureID", 0);
        return nbt;
    }

    /** The values {@link #unmarkedState} sets away from a new ship's defaults. */
    private static void assertSettings(GameTestHelper helper, BasicEntityShip ship, int shipUid, String label) {
        helper.assertTrue(ship.getStateMinorArray().length == MINOR_LENGTH
                        && ship.getStateFlagArray().length == FLAG_LENGTH
                        && ship.getStateEmotionArray().length == EMOTION_LENGTH,
                label + ": the state arrays changed length");
        helper.assertTrue(ship.getLevel() == 37 && ship.getStateMinor(ID.M.Kills) == 5
                        && ship.getStateMinor(ID.M.ExpCurrent) == 120,
                label + ": level " + ship.getLevel() + ", kills " + ship.getStateMinor(ID.M.Kills)
                        + ", exp " + ship.getStateMinor(ID.M.ExpCurrent));
        helper.assertTrue(ship.getShipClass() == ID.ShipClass.DDHibiki
                        && ship.getStateMinor(ID.M.ShipType) == ID.ShipType.DESTROYER,
                label + ": ship class and type were not kept");
        helper.assertTrue(OWNER_NAME.equals(ship.ownerName) && ship.getPlayerUID() == OWNER_UID
                        && ship.getShipUID() == shipUid,
                label + ": owner " + ship.ownerName + ", owner uid " + ship.getPlayerUID()
                        + ", ship uid " + ship.getShipUID());
        helper.assertTrue(ship.getStateMinor(ID.M.FollowMin) == 5 && ship.getStateMinor(ID.M.FollowMax) == 20
                        && ship.getStateMinor(ID.M.FleeHP) == 50,
                label + ": follow range " + ship.getStateMinor(ID.M.FollowMin) + ".."
                        + ship.getStateMinor(ID.M.FollowMax) + ", flee HP " + ship.getStateMinor(ID.M.FleeHP));
        helper.assertTrue(ship.getStateMinor(ID.M.FormatPos) == 3,
                label + ": formation slot " + ship.getStateMinor(ID.M.FormatPos));
        helper.assertTrue(ship.getStateMinor(ID.M.Task) == 2 && ship.getStateMinor(ID.M.TaskSide) == 5
                        && ship.getStateMinor(ID.M.WpStay) == 2,
                label + ": task " + ship.getStateMinor(ID.M.Task) + ", side "
                        + ship.getStateMinor(ID.M.TaskSide) + ", stay " + ship.getStateMinor(ID.M.WpStay));
        helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) == 500 && ship.getStateMinor(ID.M.Morale) == 3000
                        && ship.getStateFlag(ID.F.IsMarried) && !ship.getStateFlag(ID.F.NoFuel),
                label + ": grudge, morale, marriage or fuel flag were not read");
        helper.assertTrue(Arrays.equals(ATTRS_BONUS, ship.getAttrs().getAttrsBonus()),
                label + ": bonus points " + Arrays.toString(ship.getAttrs().getAttrsBonus()));
        helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0, label + ": crane state must load as 0");
    }

    /**
     * The formation type as the save holds it. Recalculating attributes switches it off for a ship
     * that is not in a team of five, so it is read here before that step.
     */
    private static void assertFormationRead(GameTestHelper helper, GameTestEntities entities, CompoundTag nbt,
                                            int type, int slot, String label) {
        BasicEntityShip ship = ship(helper, entities);
        CapaShipSavedValues.loadNBTData(nbt, ship);
        helper.assertTrue(ship.getStateMinor(ID.M.FormatType) == type
                        && ship.getStateMinor(ID.M.FormatPos) == slot,
                label + ": formation read as " + ship.getStateMinor(ID.M.FormatType) + "/"
                        + ship.getStateMinor(ID.M.FormatPos));
    }

    /** A death egg of the same era: the state keys, the ship class and the owner tags. */
    private static CompoundTag unmarkedEgg(CompoundTag state) {
        state.putInt("ShipClass", ID.ShipClass.DDHibiki);
        state.putString("owner", eggOwnerId().toString());
        state.putString("ownername", OWNER_NAME);
        return state;
    }

    private static UUID eggOwnerId() {
        return UUID.fromString("00000000-0000-0000-0000-000000254001");
    }

    private static FakePlayer eggOwner(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(eggOwnerId(), "save_format_owner"));
    }

    /** Uses the egg on a block the way a player does and returns the ship it put into the world. */
    private static BasicEntityShip hatch(GameTestHelper helper, GameTestEntities entities, CompoundTag eggNbt) {
        FakePlayer player = eggOwner(helper);
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        egg.setTag(eggNbt);
        BlockPos spawn = BlockPos.containing(helper.absoluteVec(EGG_SPAWN));
        BlockPos floor = spawn.below();
        boolean creative = player.getAbilities().instabuild;
        // a saved egg costs experience levels outside creative mode
        player.getAbilities().instabuild = true;
        InteractionResult result;
        try {
            result = egg.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    egg, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        } finally {
            player.getAbilities().instabuild = creative;
        }
        helper.assertTrue(result == InteractionResult.CONSUME, "Using the egg did not spawn a ship: " + result);
        List<BasicEntityShip> spawned = helper.getLevel().getEntitiesOfClass(BasicEntityShip.class, new AABB(spawn));
        helper.assertTrue(spawned.size() == 1, "Expected one ship from the egg, found " + spawned.size());
        BasicEntityShip ship = entities.add(spawned.get(0));
        ship.setNoAi(true);
        return ship;
    }

    // ===== shared checks =====

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities) {
        Entity entity = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create ship");
        }
        return ship;
    }

    private static void assertKeys(GameTestHelper helper, CompoundTag nbt, Set<String> expected, String label) {
        helper.assertTrue(nbt.getAllKeys().equals(expected),
                label + ": saved keys " + new TreeSet<>(nbt.getAllKeys()) + ", expected " + new TreeSet<>(expected));
    }

    private static void assertMarked(GameTestHelper helper, CompoundTag nbt, String label) {
        helper.assertTrue(nbt.contains(VERSION_KEY, Tag.TAG_INT) && nbt.getInt(VERSION_KEY) == 1,
                label + ": expected the integer version mark 1, found " + nbt.get(VERSION_KEY));
    }

    /** Two ships loaded from saves that differ only in the mark hold the same command and settings. */
    private static void assertSameShip(GameTestHelper helper, BasicEntityShip marked, BasicEntityShip unmarked,
                                       String label) {
        helper.assertTrue(Guard.of(marked).equals(Guard.of(unmarked)),
                label + ": command fields " + Guard.of(unmarked) + " differ from " + Guard.of(marked));
        helper.assertTrue(Objects.equals(marked.getCommandState(), unmarked.getCommandState()),
                label + ": typed order " + unmarked.getCommandState() + " differs from " + marked.getCommandState());
        helper.assertTrue(Arrays.equals(marked.getStateMinorArray(), unmarked.getStateMinorArray())
                        && Arrays.equals(marked.getStateFlagArray(), unmarked.getStateFlagArray())
                        && Arrays.equals(marked.getStateEmotionArray(), unmarked.getStateEmotionArray()),
                label + ": state arrays differ");
    }

    private static void assertGuard(GameTestHelper helper, BasicEntityShip ship, Guard expected, String label) {
        Guard actual = Guard.of(ship);
        helper.assertTrue(expected.equals(actual), label + ": loaded " + actual + ", expected " + expected);
    }

    private static void assertOrder(GameTestHelper helper, BasicEntityShip ship, MovementOrder order,
                                    boolean sitting, String label) {
        ShipCommandState state = ship.getCommandState();
        ShipCommandState expected = new ShipCommandState(order, sitting, Optional.empty());
        helper.assertTrue(expected.equals(state), label + ": typed order " + state + ", expected " + expected);
    }

    /** The loaded fields, and under NEW the order derived from them, which must leave them as they were. */
    private static void assertCommand(GameTestHelper helper, BasicEntityShip ship,
                                      ConfigHandler.ShipAiTargetAuthority authority, Guard expected,
                                      MovementOrder order, String label) {
        assertGuard(helper, ship, expected, label);
        if (authority == ConfigHandler.ShipAiTargetAuthority.NEW) {
            assertOrder(helper, ship, order, expected.sitting(), label);
            assertGuard(helper, ship, expected, label + " after the order");
        } else {
            helper.assertTrue(ship.getCommandState() == null, label + ": LEGACY must not hold a typed order");
        }
    }

    /**
     * {@link #assertCommand} for a save that names its guard dimension by number only: deriving
     * the typed order under NEW writes back the dimension that number stood for.
     */
    private static void assertUndimensionedCommand(GameTestHelper helper, BasicEntityShip ship,
                                                   ConfigHandler.ShipAiTargetAuthority authority, Guard expected,
                                                   MovementOrder order, ResourceKey<Level> projected,
                                                   String label) {
        if (authority == ConfigHandler.ShipAiTargetAuthority.NEW) {
            assertOrder(helper, ship, order, expected.sitting(), label);
            assertGuard(helper, ship, expected.withDimension(projected), label + " after the order");
        } else {
            assertGuard(helper, ship, expected, label);
            helper.assertTrue(ship.getCommandState() == null, label + ": LEGACY must not hold a typed order");
        }
    }

    /** A sequence step may only fail through the GameTest exception; anything else stops the server. */
    private static void guarded(GameTestHelper helper, Runnable body) {
        try {
            body.run();
        } catch (GameTestAssertException failure) {
            throw failure;
        } catch (RuntimeException | AssertionError failure) {
            helper.fail("Fixture failed: " + failure);
        }
    }
}
