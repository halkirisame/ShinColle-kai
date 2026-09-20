package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/** Regression tests for owner restoration when a ship is respawned from a death egg. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpawnEggOwnerGameTests {

    private static final int OWNER_UID = 15_801;
    private static final int SPAWNER_UID = 15_802;

    private SpawnEggOwnerGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggUsedByOtherRestoresOriginalOwnerAfterCacheUpdate(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            FakePlayer owner = player(level, "00000000-0000-0000-0000-000000015801", "egg_owner_a", OWNER_UID);
            FakePlayer spawner = player(level, "00000000-0000-0000-0000-000000015802", "egg_spawner_b",
                    SPAWNER_UID);
            BasicEntityShip ship = ship(entities, level);

            initShipFromEgg(ship, savedEgg(ship, OWNER_UID, owner.getUUID().toString()), spawner);
            UUID ownerBeforeUpdate = ship.getOwnerUUID();
            int uidBeforeUpdate = ship.getPlayerUID();
            ship.updateShipCacheData(true);

            if (!owner.getUUID().equals(ownerBeforeUpdate) || uidBeforeUpdate != OWNER_UID
                    || !owner.getUUID().equals(ship.getOwnerUUID()) || ship.getPlayerUID() != OWNER_UID) {
                helper.fail("Death egg owner changed when another player spawned it: beforeUuid="
                        + ownerBeforeUpdate + " beforeUid=" + uidBeforeUpdate + " afterUuid="
                        + ship.getOwnerUUID() + " afterUid=" + ship.getPlayerUID());
                return;
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggUsedByOwnerKeepsOwner(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            FakePlayer owner = player(level, "00000000-0000-0000-0000-000000015811", "egg_same_owner",
                    OWNER_UID + 10);
            BasicEntityShip ship = ship(entities, level);

            initShipFromEgg(ship, savedEgg(ship, OWNER_UID + 10, owner.getUUID().toString()), owner);

            assertOwner(helper, ship, owner.getUUID(), OWNER_UID + 10, "Owner spawning own death egg");
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggWithoutOwnerKeepsPositiveUidAndRejectsSpawner(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            FakePlayer spawner = player(level, "00000000-0000-0000-0000-000000015822", "egg_uid_spawner",
                    SPAWNER_UID + 20);
            BasicEntityShip ship = ship(entities, level);

            initShipFromEgg(ship, savedEgg(ship, OWNER_UID + 20, null), spawner);

            if (ship.getPlayerUID() != OWNER_UID + 20 || spawner.getUUID().equals(ship.getOwnerUUID())) {
                helper.fail("Death egg without owner UUID was assigned to its spawner: uuid="
                        + ship.getOwnerUUID() + " uid=" + ship.getPlayerUID());
                return;
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggWithoutOwnerOrUidUsesSpawner(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            FakePlayer spawner = player(level, "00000000-0000-0000-0000-000000015832", "egg_empty_spawner",
                    SPAWNER_UID + 30);
            BasicEntityShip ship = ship(entities, level);

            initShipFromEgg(ship, savedEgg(ship, 0, null), spawner);

            assertOwner(helper, ship, spawner.getUUID(), SPAWNER_UID + 30,
                    "Death egg without owner identity");
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggWithMalformedOwnerUsesZeroUidFallback(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            FakePlayer spawner = player(level, "00000000-0000-0000-0000-000000015842", "egg_bad_owner_spawner",
                    SPAWNER_UID + 40);
            BasicEntityShip ship = ship(entities, level);

            initShipFromEgg(ship, savedEgg(ship, 0, "not-a-uuid"), spawner);

            assertOwner(helper, ship, spawner.getUUID(), SPAWNER_UID + 40,
                    "Death egg with malformed owner and no owner UID");
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void freshEggUsesSpawner(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerLevel level = helper.getLevel();
            FakePlayer spawner = player(level, "00000000-0000-0000-0000-000000015852", "egg_fresh_spawner",
                    SPAWNER_UID + 50);
            BasicEntityShip ship = ship(entities, level);

            initShipFromEgg(ship, new ItemStack(ModItems.SHIP_SPAWN_EGG.get()), spawner);

            assertOwner(helper, ship, spawner.getUUID(), SPAWNER_UID + 50, "Fresh egg");
        }
    }

    private static BasicEntityShip ship(GameTestEntities entities, ServerLevel level) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_I.get().create(level));
        if (ship == null) {
            throw new AssertionError("Could not create ship for spawn egg owner test");
        }
        return ship;
    }

    private static FakePlayer player(ServerLevel level, String uuid, String name, int playerUid) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(uuid), name));
        CapaTeitoku capability = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capability == null) {
            throw new AssertionError("Fake player has no Teitoku capability: " + name);
        }
        capability.setPlayerUID(playerUid);
        return player;
    }

    private static ItemStack savedEgg(BasicEntityShip ship, int ownerUid, String ownerUuid) {
        ItemStack egg = new ItemStack(ModItems.SHIP_SPAWN_EGG.get());
        int[] stateMinor = ship.getStateMinorArray().clone();
        stateMinor[ID.M.PlayerUID] = ownerUid;
        egg.getOrCreateTag().putIntArray("StateMinor", stateMinor);
        if (ownerUuid != null) {
            egg.getOrCreateTag().putString("owner", ownerUuid);
        }
        return egg;
    }

    private static void initShipFromEgg(BasicEntityShip ship, ItemStack egg, Player player) {
        try {
            Method method = ShipSpawnEgg.class.getDeclaredMethod(
                    "initShipFromEgg", BasicEntityShip.class, ItemStack.class, Player.class);
            method.setAccessible(true);
            method.invoke(null, ship, egg, player);
        } catch (InvocationTargetException e) {
            throw new AssertionError("ShipSpawnEgg#initShipFromEgg threw an exception", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not invoke ShipSpawnEgg#initShipFromEgg", e);
        }
    }

    private static void assertOwner(GameTestHelper helper, BasicEntityShip ship, UUID expectedUuid,
                                    int expectedUid, String scenario) {
        if (!expectedUuid.equals(ship.getOwnerUUID()) || ship.getPlayerUID() != expectedUid || !ship.isTame()) {
            helper.fail(scenario + " did not produce the expected tamed owner: uuid="
                    + ship.getOwnerUUID() + " uid=" + ship.getPlayerUID() + " tame=" + ship.isTame());
            return;
        }
        helper.succeed();
    }
}
