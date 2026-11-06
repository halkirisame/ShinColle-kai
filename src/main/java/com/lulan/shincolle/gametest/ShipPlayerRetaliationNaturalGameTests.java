package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.ShipTargetAuthorityGoal;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Natural scheduling tests; attack execution is deliberately isolated from target adoption. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipPlayerRetaliationNaturalGameTests {
    private static final Vec3 ORIGIN = new Vec3(1D, 4D, 1D);
    private static final int OWNER_UID = 732201;
    private static final int ATTACKER_UID = 732202;
    private static final int SHIP_UID = 732203;

    private ShipPlayerRetaliationNaturalGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_natural_off")
    public static void naturalHitWithBothPermissionsOff(GameTestHelper helper) {
        direct(helper, false, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_natural_ship")
    public static void naturalHitWithShipPermission(GameTestHelper helper) {
        direct(helper, true, false, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_natural_fleet")
    public static void naturalHitWithFleetPermission(GameTestHelper helper) {
        direct(helper, false, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_natural_both")
    public static void naturalHitWithBothPermissions(GameTestHelper helper) {
        direct(helper, true, true, false);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_owner_off")
    public static void ownerHitWithoutPermissionKeepsOwnerBypass(GameTestHelper helper) {
        direct(helper, false, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_owner_on")
    public static void ownerHitWithPermissionKeepsOwnerBypass(GameTestHelper helper) {
        direct(helper, true, true, true);
    }

    private static void direct(GameTestHelper helper, boolean shipPermission, boolean fleetPermission,
                               boolean ownerHit) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip ship = ship(helper, entities, 0D);
            ServerPlayer[] attacker = {null};
            CapaTeitoku[] fleet = {null};
            helper.runAfterDelay(26, () -> {
                helper.assertTrue(ship.tickCount >= 24 && !ship.isNoAi(),
                        "Hit must follow natural goal re-registration");
                ServerPlayer owner = player(helper, entities, OWNER_UID, 0D);
                CapaTeitoku capa = capability(owner);
                fleet[0] = capa;
                FormationGameTestOwner online = new FormationGameTestOwner(owner);
                GameTestEntities.onCompletion(helper, online::close, "online retaliation owner");
                ship.setOwnerUUID(owner.getUUID());
                capa.setTeamMember(2, 0, SHIP_UID);
                capa.setRetaliatePlayer(2, fleetPermission);
                ship.setStateFlag(ID.F.RetaliatePlayer, shipPermission);
                attacker[0] = ownerHit ? owner : player(helper, entities, ATTACKER_UID, 2D);
                assertFixture(helper, ship, owner, attacker[0], capa, shipPermission, fleetPermission);
                helper.assertTrue(new TargetHelper.RevengeSelector(ship).test(attacker[0])
                                == (!ownerHit && (shipPermission || fleetPermission)),
                        "Selector eligibility does not match the controlled fixture");
                // Only the synchronous hit needs a zero dodge value; later recalculation is immaterial.
                ship.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
                helper.assertTrue(ship.getAttrs().getAttrsBuffed(ID.Attrs.DODGE) == 0F,
                        "Hit fixture must not dodge");
                ship.setInvulnerable(false);
                float health = ship.getHealth();
                int revengeTime = ship.getEntityRevengeTime();
                helper.assertTrue(!ship.isInvulnerableTo(ship.damageSources().playerAttack(attacker[0])),
                        "Hit fixture must pass invulnerability check");
                helper.assertTrue(ship.hurt(ship.damageSources().playerAttack(attacker[0]), 1F)
                                && ship.getHealth() < health && ship.isAlive(),
                        "The melee damage must actually land on the ship");
                helper.assertTrue(ownerHit
                                ? ship.getEntityRevengeTarget() == null
                                    && ship.getEntityRevengeTime() == revengeTime
                                : ship.getEntityRevengeTarget() == attacker[0]
                                    && ship.getEntityRevengeTime() == ship.tickCount
                                    && ship.getEntityRevengeTime() > revengeTime,
                        "Hit did not take the expected owner/normal revenge-recording branch");
                ship.setInvulnerable(true);
            });
            helper.runAfterDelay(36, () -> {
                assertLock(helper, ship, attacker[0], !ownerHit && (shipPermission || fleetPermission));
                ship.setStateFlag(ID.F.RetaliatePlayer, false);
                fleet[0].setRetaliatePlayer(2, false);
            });
            helper.runAfterDelay(44, () -> {
                assertLock(helper, ship, attacker[0], !ownerHit && (shipPermission || fleetPermission));
                helper.succeed();
            });
        }, ORIGIN, ORIGIN.add(6D, 0D, 0D));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_owner_attacks_event")
    public static void ownerAttacksEventAdoptsOnlyPermittedSameOwnerShips(GameTestHelper helper) {
        propagated(helper, true);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_owner_attacked_event")
    public static void ownerAttackedEventAdoptsOnlyPermittedSameOwnerShips(GameTestHelper helper) {
        propagated(helper, false);
    }

    private static void propagated(GameTestHelper helper, boolean ownerAttacks) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip off = ship(helper, entities, 0D);
            BasicEntityShip on = ship(helper, entities, 2D);
            BasicEntityShip other = ship(helper, entities, 4D);
            ServerPlayer[] target = {null};
            helper.runAfterDelay(26, () -> {
                ServerPlayer owner = player(helper, entities, OWNER_UID, 0D);
                target[0] = player(helper, entities, ATTACKER_UID, 6D);
                off.setOwnerUUID(owner.getUUID());
                on.setOwnerUUID(owner.getUUID());
                on.setStateFlag(ID.F.RetaliatePlayer, true);
                other.setPlayerUID(ATTACKER_UID + 1);
                helper.assertTrue(off.tickCount >= 24 && on.tickCount >= 24 && other.tickCount >= 24
                                && off.getOwner() == owner && on.getOwner() == owner
                                && off.getGrudge() > 0 && on.getGrudge() > 0
                                && !off.getStateFlag(ID.F.NoFuel) && !on.getStateFlag(ID.F.NoFuel)
                                && !target[0].getAbilities().invulnerable,
                        "Propagation fixture must be online, fueled and naturally ticking");
                LivingAttackEvent event = ownerAttacks
                        ? new LivingAttackEvent(target[0], owner.damageSources().playerAttack(owner), 1F)
                        : new LivingAttackEvent(owner, target[0].damageSources().playerAttack(target[0]), 1F);
                // The real Forge bus, not a direct handler call; FakePlayer damage is not claimed here.
                MinecraftForge.EVENT_BUS.post(event);
                helper.assertTrue(off.getEntityRevengeTarget() == target[0]
                                && on.getEntityRevengeTarget() == target[0]
                                && off.getEntityRevengeTime() == off.tickCount
                                && on.getEntityRevengeTime() == on.tickCount
                                && other.getEntityRevengeTarget() == null,
                        "Event propagation must record both same-owner ships, not the other owner's ship");
            });
            helper.runAfterDelay(36, () -> {
                assertLock(helper, off, target[0], false);
                assertLock(helper, on, target[0], true);
                helper.assertTrue(other.currentTargetLock().isEmpty(), "Another owner's ship acquired revenge");
                helper.succeed();
            });
        }, ORIGIN, ORIGIN.add(6D, 0D, 0D));
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 120,
            batch = "isolated_retaliation_nonplayer_manual")
    public static void disabledPermissionLeavesNaturalNonPlayerRevengeAndManualPlayerOrder(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            GameTestEntities entities = GameTestEntities.open(helper);
            BasicEntityShip ship = ship(helper, entities, 0D);
            Zombie[] zombie = {null};
            ServerPlayer[] target = {null};
            helper.runAfterDelay(26, () -> {
                ServerPlayer owner = player(helper, entities, OWNER_UID, 0D);
                ship.setOwnerUUID(owner.getUUID());
                zombie[0] = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
                zombie[0].setNoAi(true);
                zombie[0].setNoGravity(true);
                zombie[0].moveTo(ship.position().add(2D, 0D, 0D));
                helper.assertTrue(helper.getLevel().addFreshEntity(zombie[0]), "Failed to add zombie");
                ship.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
                helper.assertTrue(ship.hurt(ship.damageSources().mobAttack(zombie[0]), 1F)
                                && ship.getEntityRevengeTarget() == zombie[0], "Non-player hit must record revenge");
                ship.setInvulnerable(true);
                target[0] = player(helper, entities, ATTACKER_UID, 3D);
            });
            helper.runAfterDelay(36, () -> {
                assertLock(helper, ship, zombie[0], true);
                helper.assertTrue(!ship.getStateFlag(ID.F.RetaliatePlayer), "Ship permission must remain off");
                ship.setManualTarget(target[0]);
            });
            helper.runAfterDelay(44, () -> {
                helper.assertTrue(ship.getEntityTarget() == target[0]
                                && ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.MANUAL)
                                    .isPresent(), "Disabled player retaliation must not block manual player orders");
                helper.succeed();
            });
        }, ORIGIN, ORIGIN.add(6D, 0D, 0D));
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities, double offset) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Failed to create ship");
        ship.setPlayerUID(OWNER_UID);
        ship.setShipUID(SHIP_UID + (int) offset);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.PassiveAI, true);
        ship.setAmmoLight(1000);
        ship.calcShipAttributes(30, false);
        ship.setHealth(ship.getMaxHealth());
        ship.setNoGravity(true);
        ship.moveTo(helper.absoluteVec(ORIGIN.add(offset, 0D, 0D)));
        GameTestEntities.removeGoalsEachTick(helper, ship, goal -> !(goal instanceof ShipTargetAuthorityGoal));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Failed to add ship");
        return ship;
    }

    private static ServerPlayer player(GameTestHelper helper, GameTestEntities entities, int uid, double offset) {
        ServerPlayer player = entities.add(FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "retaliation_" + uid)));
        capability(player).setPlayerUID(uid);
        player.getAbilities().invulnerable = false;
        player.setInvulnerable(false);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(ORIGIN.add(offset, 0D, 0D)));
        helper.getLevel().addNewPlayer(player);
        GameTestEntities.onCompletion(helper, () -> helper.getLevel().removePlayerImmediately(
                player, Entity.RemovalReason.DISCARDED), "retaliation player cleanup");
        return player;
    }

    private static CapaTeitoku capability(ServerPlayer player) {
        return player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new IllegalStateException("Fixture player has no admiral capability"));
    }

    private static void assertFixture(GameTestHelper helper, BasicEntityShip ship, ServerPlayer owner,
                                       ServerPlayer attacker, CapaTeitoku capa,
                                       boolean shipPermission, boolean fleetPermission) {
        helper.assertTrue(ConfigHandler.friendlyFire() && ship.getGrudge() > 0
                        && !ship.getStateFlag(ID.F.NoFuel) && ship.getHealth() > 2F && ship.hasAmmoLight()
                        && ship.getOwner() == owner && ship.getPlayerUID() == OWNER_UID
                        && capa.getPlayerUID() == OWNER_UID && ship.getShipUID() == SHIP_UID
                        && capa.isShipInTeam(2, SHIP_UID)
                        && capa.getRetaliatePlayer(2) == fleetPermission
                        && ship.getStateFlag(ID.F.RetaliatePlayer) == shipPermission
                        && !attacker.getAbilities().invulnerable && !attacker.isInvulnerable()
                        && ship.distanceToSqr(attacker) <= 9D && ship.currentTargetLock().isEmpty(),
                "Natural hit fixture prerequisites were not established");
    }

    private static void assertLock(GameTestHelper helper, BasicEntityShip ship, Entity attacker, boolean expected) {
        helper.assertTrue(ship.getGrudge() > 0 && !ship.getStateFlag(ID.F.NoFuel) && !ship.isNoAi(),
                "Observation must retain fuel and natural AI");
        helper.assertTrue(expected
                        ? ship.getEntityTarget() == attacker
                            && ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.REVENGE).isPresent()
                        : ship.getEntityTarget() == null && ship.currentTargetLock().isEmpty(),
                "Unexpected natural REVENGE adoption: expected=" + expected + ", lock=" + ship.currentTargetLock());
    }
}
