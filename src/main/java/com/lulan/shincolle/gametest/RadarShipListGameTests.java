package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Checks the server-side list used by both desk and handheld radars. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadarShipListGameTests {
    private static final Vec3 SHIP_POSITION = new Vec3(1.5D, 2D, 1.5D);

    private RadarShipListGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void radarListContainsOwnedLoadedShipSameDimension(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            Map<Integer, List<Integer>> lists = fixture.collect();
            helper.assertTrue(lists.containsKey(fixture.ownerUid()), "Online admiral must have a radar list");
            helper.assertTrue(lists.get(fixture.ownerUid()).equals(List.of(fixture.ship().getId())),
                    "Radar must contain exactly the loaded owned ship in the admiral's dimension");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void radarListExcludesOtherOwnersShip(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            helper.assertTrue(ServerDataManager.changeShipOwner(fixture.ship(), fixture.other()),
                    "Fixture must transfer the ship to the other admiral");
            helper.assertTrue(fixture.ship().getPlayerUID() == fixture.otherUid()
                            && fixture.ship().getOwner() == fixture.other(),
                    "Fixture ship must belong to the other admiral");
            Map<Integer, List<Integer>> lists = fixture.collect();
            helper.assertTrue(lists.containsKey(fixture.ownerUid()) && lists.get(fixture.ownerUid()).isEmpty(),
                    "Admiral with no ships must retain an empty list, not another admiral's ship");
            helper.assertTrue(lists.get(fixture.otherUid()).equals(List.of(fixture.ship().getId())),
                    "The ship must appear only in its actual owner's radar list");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void radarListEmptyAfterShipRemoved(GameTestHelper helper) {
        withFixture(helper, fixture -> {
            helper.assertTrue(fixture.collect().get(fixture.ownerUid()).contains(fixture.ship().getId()),
                    "Fixture ship must be visible before removal");
            fixture.ship().discard();
            helper.assertTrue(fixture.ship().isRemoved()
                            && helper.getLevel().getEntity(fixture.ship().getId()) == null,
                    "Discarded ship must no longer be a loaded entity");
            helper.assertTrue(ServerDataManager.getShipWorldData(fixture.ship().getShipUID()) != null,
                    "Fixture must leave a stale cache entry to exercise loaded-entity filtering");
            Map<Integer, List<Integer>> lists = fixture.collect();
            helper.assertTrue(lists.containsKey(fixture.ownerUid()) && lists.get(fixture.ownerUid()).isEmpty(),
                    "Removing the last ship must produce an empty radar list");
        });
    }

    private static void withFixture(GameTestHelper helper, Consumer<RadarFixture> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                FakePlayer owner = FakePlayerFactory.get(helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "radar_owner"));
                FakePlayer other = FakePlayerFactory.get(helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "radar_other"));
                BasicEntityShip ship = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
                helper.assertTrue(ship != null, "Failed to create radar fixture ship");
                try {
                    owner.moveTo(helper.absoluteVec(SHIP_POSITION));
                    other.moveTo(owner.position());
                    helper.getLevel().addNewPlayer(owner);
                    helper.getLevel().addNewPlayer(other);
                    ServerDataManager.updatePlayerID(owner);
                    ServerDataManager.updatePlayerID(other);
                    int ownerUid = ServerDataManager.getTeitokuCapability(owner).getPlayerUID();
                    int otherUid = ServerDataManager.getTeitokuCapability(other).getPlayerUID();
                    helper.assertTrue(ownerUid > 0 && otherUid > 0 && ownerUid != otherUid,
                            "Fixture admirals must have distinct positive UIDs");
                    ship.setNoAi(true);
                    ship.setInvulnerable(true);
                    ship.setStateMinor(ID.M.NumGrudge, 100_000);
                    ship.setStateFlag(ID.F.NoFuel, false);
                    ship.calcShipAttributes(31, false);
                    ship.moveTo(helper.absoluteVec(SHIP_POSITION));
                    helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add radar fixture ship");
                    helper.assertTrue(ServerDataManager.changeShipOwner(ship, owner),
                            "Fixture must register the ship and its owner");
                    GameTestEntities.assertRegistered(helper, ship);
                    helper.assertTrue(ship.getOwner() == owner && ship.getPlayerUID() == ownerUid
                                    && ship.getShipUID() > 0
                                    && ServerDataManager.getShipWorldData(ship.getShipUID()).entityID == ship.getId(),
                            "Fixture ship identity and ownership must be registered");
                    helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                            "Fixture ship must have fuel");
                    verification.accept(new RadarFixture(owner, other, ship, ownerUid, otherUid));
                    helper.succeed();
                } finally {
                    if (!ship.isRemoved()) {
                        ship.discard();
                    }
                    ServerDataManager.removeShipData(ship.getShipUID());
                    helper.getLevel().removePlayerImmediately(other, Entity.RemovalReason.DISCARDED);
                    helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                }
            }
        }, SHIP_POSITION);
    }

    private record RadarFixture(ServerPlayer owner, ServerPlayer other, BasicEntityShip ship,
                                int ownerUid, int otherUid) {
        Map<Integer, List<Integer>> collect() {
            return ServerDataManager.collectRadarShipIds(this.owner.getServer(),
                    Map.of(this.ownerUid, this.owner, this.otherUid, this.other));
        }
    }
}
