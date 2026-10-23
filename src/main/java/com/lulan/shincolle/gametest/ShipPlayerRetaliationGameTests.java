package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPlayerRetaliationGameTests {

    private static final int FLEET_OWNER_UID = 6403;
    private static final int FLEET_SHIP_UID = 6404;

    private ShipPlayerRetaliationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_player_retaliation_is_refused_by_default")
    public static void playerRetaliationIsRefusedByDefault(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities, 6401);
            helper.assertTrue(!ship.getStateFlag(ID.F.RetaliatePlayer),
                    "Fixture: a new ship must start without permission to retaliate against players");
            ServerPlayer attacker = attacker(helper, entities, 6402);

            helper.assertTrue(lockedTargetAfterRevenge(ship, attacker) != attacker,
                    "A ship without permission locked the player who attacked it");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_player_retaliation_follows_the_ship_permission")
    public static void playerRetaliationFollowsTheShipPermission(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities, 6411);
            ship.setStateFlag(ID.F.RetaliatePlayer, true);
            ServerPlayer attacker = attacker(helper, entities, 6412);

            helper.assertTrue(lockedTargetAfterRevenge(ship, attacker) == attacker,
                    "A ship with permission did not lock the player who attacked it");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_player_retaliation_follows_the_fleet_permission")
    public static void playerRetaliationFollowsTheFleetPermission(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "retaliation_fleet_owner",
                    "00000000-0000-0000-0000-000000006403");
            CapaTeitoku capa = owner.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                    () -> new AssertionError("Fleet owner has no admiral capability"));
            capa.setPlayerUID(FLEET_OWNER_UID);
            BasicEntityShip ship = ship(helper, entities, FLEET_OWNER_UID);
            ship.setShipUID(FLEET_SHIP_UID);
            capa.setTeamMember(2, 0, FLEET_SHIP_UID);
            ServerPlayer attacker = attacker(helper, entities, 6405);

            try (FormationGameTestOwner online = new FormationGameTestOwner(owner)) {
                helper.assertTrue(capa.isShipInTeam(2, FLEET_SHIP_UID),
                        "Fixture: the ship is not in the fleet it is supposed to belong to");

                capa.setRetaliatePlayer(5, true);
                helper.assertTrue(lockedTargetAfterRevenge(ship, attacker) != attacker,
                        "Another fleet's permission let a ship outside it lock a player");

                capa.setRetaliatePlayer(2, true);
                helper.assertTrue(lockedTargetAfterRevenge(ship, attacker) == attacker,
                        "A ship in a permitted fleet did not lock the player who attacked it");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_player_retaliation_leaves_other_targets_alone")
    public static void playerRetaliationLeavesOtherTargetsAlone(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = ship(helper, entities, 6421);
            Zombie attacker = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            attacker.setNoAi(true);
            attacker.setPersistenceRequired();
            move(helper, attacker, new Vec3(3D, 4D, 1D));
            helper.assertTrue(helper.getLevel().addFreshEntity(attacker), "Failed to add zombie");

            helper.assertTrue(lockedTargetAfterRevenge(ship, attacker) == attacker,
                    "The player permission stopped a ship from locking a non-player attacker");
            helper.succeed();
        }
    }

    private static Entity lockedTargetAfterRevenge(BasicEntityShip ship, Entity attacker) {
        ship.setTarget(null);
        ship.tickCount = 10;
        ship.setEntityRevengeTarget(attacker);
        ship.setEntityRevengeTime();
        ShipTargetAuthorityGoal goal = new ShipTargetAuthorityGoal(ship);
        goal.tick();
        return ship.getTarget();
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, int ownerUid) {
        Entity entity = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create friendly ship");
        }
        ship.setNoAi(true);
        ship.setPlayerUID(ownerUid);
        ship.setStateFlag(ID.F.PassiveAI, true);
        move(helper, ship, new Vec3(1D, 4D, 1D));
        if (!helper.getLevel().addFreshEntity(ship)) {
            throw new AssertionError("Failed to add friendly ship");
        }
        return ship;
    }

    private static ServerPlayer attacker(GameTestHelper helper, GameTestEntities entities, int uid) {
        ServerPlayer player = player(helper, entities, "retaliation_attacker_" + uid,
                "00000000-0000-0000-0000-00000000" + uid);
        player.getCapability(CapaTeitokuProvider.CAPABILITY).ifPresent(capa -> capa.setPlayerUID(uid));
        move(helper, player, new Vec3(3D, 4D, 1D));
        return player;
    }

    private static ServerPlayer player(GameTestHelper helper, GameTestEntities entities, String name, String uuid) {
        ServerPlayer player = entities.add(FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)));
        player.getAbilities().invulnerable = false;
        move(helper, player, new Vec3(0.5D, 4D, 0.5D));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void move(GameTestHelper helper, Entity entity, Vec3 relativePosition) {
        Vec3 position = helper.absoluteVec(relativePosition);
        entity.moveTo(position.x, position.y, position.z);
    }
}
