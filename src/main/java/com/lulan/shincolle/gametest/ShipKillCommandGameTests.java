package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipKillCommandGameTests {
    private static final Vec3 POSITION = new Vec3(2.5D, 2D, 2.5D);
    private static final int GODDESS_SLOT = 6;

    private ShipKillCommandGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command")
    public static void killStartsOwnedShipDeath(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = ownedShip(helper, entities);
            ship.kill();
            helper.assertTrue(ship.isDeadOrDying(), "kill must start normal death for an owned ship");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command")
    public static void killBypassesRepairGoddessWithoutConsumingIt(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = ownedShip(helper, entities);
            equipGoddess(helper, ship);
            ship.kill();
            assertKilledWithGoddess(helper, ship);
            helper.assertTrue(ship.getStateTimer(ID.T.ImmuneTime) == 0,
                    "kill must not activate repair goddess immunity");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command")
    public static void killBypassesOwnedShipImmunityAndRepairGoddess(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = ownedShip(helper, entities);
            equipGoddess(helper, ship);
            ship.setStateTimer(ID.T.ImmuneTime, 120);
            helper.assertTrue(ship.getStateTimer(ID.T.ImmuneTime) == 120
                            && ship.isInvulnerableTo(ship.damageSources().generic()),
                    "repair goddess immunity fixture must reject ordinary damage");
            ship.kill();
            assertKilledWithGoddess(helper, ship);
            helper.assertTrue(!ship.isInvulnerableTo(ship.damageSources().genericKill()),
                    "generic kill must bypass the ship's immunity timer");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command",
            timeoutTicks = 3660)
    public static void killedOwnedShipDropsExactlyOneOwnerTaggedEgg(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = ownedShip(helper, entities);
            UUID owner = ship.getOwnerUUID();
            helper.assertTrue(eggs(helper).isEmpty(), "death egg fixture must start empty");
            ship.kill();
            helper.assertTrue(ship.isDeadOrDying() && ship.deathTime == 0,
                    "kill must enter normal death waiting");
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ship.isRemoved(),
                    "waiting for configured death duration")).thenExecute(() -> {
                List<BasicEntityItem> eggs = eggs(helper);
                eggs.forEach(entities::add);
                helper.assertTrue(ship.deathTime == Math.max(1, ConfigHandler.deathTime()),
                        "kill must use the configured normal death duration");
                helper.assertTrue(eggs.size() == 1 && eggs.get(0).getEntityItem().getCount() == 1,
                        "kill must leave exactly one ship egg");
                helper.assertTrue(owner.toString().equals(
                                eggs.get(0).getEntityItem().getOrCreateTag().getString("owner")),
                        "the death egg must preserve its owner's UUID");
                helper.assertTrue(!ship.getStateFlag(ID.F.CanDrop),
                        "normal death must consume the single-drop guard");
                helper.succeed();
            });
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command")
    public static void killBypassesHostileShipImmunity(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShipHostile ship = hostileShip(helper, entities);
            ship.setStateTimer(ID.T.ImmuneTime, 120);
            helper.assertTrue(ship.getStateTimer(ID.T.ImmuneTime) == 120
                            && ship.isInvulnerableTo(ship.damageSources().generic()),
                    "hostile immunity fixture must reject ordinary damage");
            ship.kill();
            helper.assertTrue(ship.isDeadOrDying(), "kill must bypass hostile ship immunity");
            helper.assertTrue(!ship.isInvulnerableTo(ship.damageSources().genericKill()),
                    "generic kill must bypass the hostile immunity timer");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command")
    public static void ordinaryAttackerlessDamageRemainsIgnored(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip ship = ownedShip(helper, entities);
            float health = ship.getHealth();
            helper.assertTrue(ship.damageSources().generic().getEntity() == null
                            && !ship.isInvulnerableTo(ship.damageSources().generic()),
                    "ordinary damage fixture must be attackerless and outside immunity");
            helper.assertTrue(!ship.hurt(ship.damageSources().generic(), Float.MAX_VALUE)
                            && !ship.hurt(ship.damageSources().inFire(), Float.MAX_VALUE),
                    "ordinary attackerless damage must still be rejected");
            helper.assertTrue(ship.getHealth() == health && !ship.isDeadOrDying(),
                    "ignored damage must not change health or start death");
            helper.succeed();
        }, POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_kill_command")
    public static void voidDamageStillRescuesBothShipKindsDuringImmunity(GameTestHelper helper) {
        GameTestEntities entities = GameTestEntities.open(helper);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BasicEntityShip owned = ownedShip(helper, entities);
            BasicEntityShipHostile hostile = hostileShip(helper, entities);
            owned.setStateTimer(ID.T.ImmuneTime, 120);
            hostile.setStateTimer(ID.T.ImmuneTime, 120);
            helper.assertTrue(owned.getStateTimer(ID.T.ImmuneTime) == 120
                            && hostile.getStateTimer(ID.T.ImmuneTime) == 120,
                    "both ships must have active immunity");
            float ownedHealth = owned.getHealth();
            float hostileHealth = hostile.getHealth();
            owned.setPos(owned.getX(), -80D, owned.getZ());
            hostile.setPos(hostile.getX(), -80D, hostile.getZ());
            helper.assertTrue(!owned.hurt(owned.damageSources().fellOutOfWorld(), Float.MAX_VALUE)
                            && !hostile.hurt(hostile.damageSources().fellOutOfWorld(), Float.MAX_VALUE),
                    "void damage must still use rescue instead of lethal damage");
            helper.assertTrue(owned.getY() == 4D && hostile.getY() == 4D
                            && owned.getHealth() == ownedHealth && hostile.getHealth() == hostileHealth
                            && !owned.isDeadOrDying() && !hostile.isDeadOrDying(),
                    "both ships must be rescued alive at the existing height");
            helper.succeed();
        }, POSITION);
    }

    private static BasicEntityShip ownedShip(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_I.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "owned ship fixture must exist");
        UUID owner = UUID.randomUUID();
        ship.setOwnerUUID(owner);
        ship.setTame(true);
        ship.setNoGravity(true);
        ship.setStateMinor(ID.M.NumGrudge, 100_000);
        ship.setStateFlag(ID.F.NoFuel, false);
        ship.setStateFlag(ID.F.CanDrop, true);
        ship.moveTo(helper.absoluteVec(POSITION));
        helper.assertTrue(owner.equals(ship.getOwnerUUID()) && ship.isTame()
                        && ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel)
                        && ship.getStateFlag(ID.F.CanDrop) && ship.getStateTimer(ID.T.ImmuneTime) == 0,
                "ownership, fuel, drop guard and initial immunity fixture must be applied");
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "owned ship must enter the level");
        GameTestEntities.removeAllGoalsEachTick(helper, ship);
        return ship;
    }

    private static BasicEntityShipHostile hostileShip(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShipHostile ship = entities.add(
                ModEntities.DESTROYER_SHIMAKAZE_MOB.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "hostile ship fixture must exist");
        ship.setNoGravity(true);
        ship.moveTo(helper.absoluteVec(POSITION));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "hostile ship must enter the level");
        GameTestEntities.removeAllGoalsEachTick(helper, ship);
        return ship;
    }

    private static void equipGoddess(GameTestHelper helper, BasicEntityShip ship) {
        ship.getCapaShipInventory().setStackInSlot(GODDESS_SLOT, new ItemStack(ModItems.REPAIR_GODDESS.get()));
        helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(GODDESS_SLOT).is(ModItems.REPAIR_GODDESS.get())
                        && ship.getCapaShipInventory().getStackInSlot(GODDESS_SLOT).getCount() == 1,
                "one repair goddess must be present in cargo");
    }

    private static void assertKilledWithGoddess(GameTestHelper helper, BasicEntityShip ship) {
        helper.assertTrue(ship.isDeadOrDying(), "kill must defeat a ship carrying a repair goddess");
        helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(GODDESS_SLOT).is(ModItems.REPAIR_GODDESS.get())
                        && ship.getCapaShipInventory().getStackInSlot(GODDESS_SLOT).getCount() == 1,
                "kill must not consume the repair goddess");
    }

    private static List<BasicEntityItem> eggs(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                new AABB(BlockPos.containing(helper.absoluteVec(POSITION))).inflate(3D),
                item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
    }
}
