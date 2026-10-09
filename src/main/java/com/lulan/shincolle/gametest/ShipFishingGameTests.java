package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityShipFishingHook;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TaskHelper;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipFishingGameTests {
    private ShipFishingGameTests() {
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void taskFloatStaysAtTheSurfaceWithoutGeneratingItsOwnLoot(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            TaskHelper.onUpdateFishing(ship);
            EntityShipFishingHook hook = entities.add(ship.fishHook);
            helper.assertTrue(hook != null, "Fixture: a deep pond must produce a float");
            Vec3 at = hook.position();
            int before = inventoryCount(ship);
            for (int i = 1; i <= 50; i++) {
                hook.tickCount = i;
                hook.tick();
            }
            helper.assertTrue(!hook.isRemoved(), "Float remains alive while waiting");
            helper.assertTrue(hook.position().distanceToSqr(at) < 0.000001, "Task float must stay on the water surface");
            helper.assertTrue(inventoryCount(ship) == before, "The float must not award a second catch");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void catchAwardsOnceAndAllowsTheNextCast(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            TaskHelper.onUpdateFishing(ship);
            EntityShipFishingHook first = entities.add(ship.fishHook);
            helper.assertTrue(first != null, "Fixture: cast");
            int count = inventoryCount(ship);
            int grudge = ship.getStateMinor(ID.M.NumGrudge);
            int exp = ship.getStateMinor(ID.M.ExpCurrent);
            int morale = ship.getMorale();
            ship.getAttrs().setAttrsBuffed(ID.Attrs.XP, 1F);
            helper.assertTrue(ship.getAttrs().getAttrsBuffed(ID.Attrs.XP) == 1F, "Fixture: unit experience multiplier");
            first.tickCount = ConfigHandler.tickFishing[0] + ConfigHandler.tickFishing[1];
            TaskHelper.onUpdateFishing(ship);
            helper.assertTrue(first.isRemoved() && ship.fishHook == null, "Catch must release the float reference");
            helper.assertTrue(inventoryCount(ship) == count + 1, "One catch enters the ship inventory");
            helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) == grudge - ConfigHandler.consumeGrudgeTask[1], "One cost");
            helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) == exp + ConfigHandler.expGainTask[1], "One experience award");
            helper.assertTrue(ship.getMorale() == morale + 300, "One morale award");
            TaskHelper.onUpdateFishing(ship);
            helper.assertTrue(ship.fishHook != null && ship.fishHook != first && !ship.fishHook.isRemoved(), "Next cast works");
            entities.add(ship.fishHook);
            helper.assertTrue(inventoryCount(ship) == count + 1, "Casting again does not duplicate the catch");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void removingAnOldFloatDoesNotClearItsReplacement(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            EntityShipFishingHook old = entities.add(new EntityShipFishingHook(ModEntities.FISHING_HOOK.get(), ship.level()));
            old.setHost(ship);
            EntityShipFishingHook next = entities.add(new EntityShipFishingHook(ModEntities.FISHING_HOOK.get(), ship.level()));
            next.setHost(ship);
            ship.fishHook = next;
            old.discard();
            helper.assertTrue(ship.fishHook == next, "An older float must not erase the current one");
            next.discard();
            helper.assertTrue(ship.fishHook == null, "Removing the current float clears the reference");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void removingTheRodEndsTheCastWithoutAwardingAnything(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            TaskHelper.onUpdateFishing(ship);
            EntityShipFishingHook hook = entities.add(ship.fishHook);
            helper.assertTrue(hook != null, "Fixture: cast");
            ship.getCapaShipInventory().setStackInSlot(22, ItemStack.EMPTY);
            int count = inventoryCount(ship);
            hook.tickCount = 1;
            hook.tick();
            helper.assertTrue(hook.isRemoved() && ship.fishHook == null, "No rod ends the cast");
            helper.assertTrue(inventoryCount(ship) == count, "Cancellation awards no loot");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void distanceAndConfiguredLifetimeReleaseTheFloat(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            TaskHelper.onUpdateFishing(ship);
            EntityShipFishingHook far = entities.add(ship.fishHook);
            helper.assertTrue(far != null, "Fixture: cast");
            far.setPos(ship.getX() + 33D, ship.getY(), ship.getZ());
            far.tick();
            helper.assertTrue(far.isRemoved() && ship.fishHook == null, "Beyond 32 blocks the cast ends");
            TaskHelper.onUpdateFishing(ship);
            EntityShipFishingHook expired = entities.add(ship.fishHook);
            helper.assertTrue(expired != null, "Fixture: recast");
            expired.tickCount = ConfigHandler.tickFishing[0] + ConfigHandler.tickFishing[1] + 1;
            expired.tick();
            helper.assertTrue(expired.isRemoved() && ship.fishHook == null, "Configured timeout releases the float");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void aTrackingCopyCanResolveTheHostFromSyncedData(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            TaskHelper.onUpdateFishing(ship);
            EntityShipFishingHook hook = entities.add(ship.fishHook);
            helper.assertTrue(hook != null, "Fixture: cast");
            var values = hook.getEntityData().getNonDefaultValues();
            helper.assertTrue(values != null && !values.isEmpty(), "Tracking data must include the host id");
            EntityShipFishingHook copy = entities.add(new EntityShipFishingHook(ModEntities.FISHING_HOOK.get(), ship.level()));
            copy.getEntityData().assignValues(values);
            helper.assertTrue(copy.getHost() == ship, "A copy without a local host reference resolves the tracking id");
            helper.assertTrue(!ModEntities.FISHING_HOOK.get().canSerialize(), "Transient floats must not be saved");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void luckBuffCanSelectTreasureFromTheVanillaTable(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            ship.getBuffMap().put(26, 99);
            helper.assertTrue(ship.getBuffMap().get(26) == 99, "Fixture: luck level 100");
            Set<net.minecraft.world.item.Item> treasures = Set.of(Items.BOW, Items.ENCHANTED_BOOK,
                    Items.FISHING_ROD, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE);
            int before = inventoryCount(ship);
            TaskHelper.generateFishingResult(ship);
            helper.assertTrue(inventoryCount(ship) == before + 1, "Treasure is inserted");
            boolean treasure = false;
            for (int i = 0; i < ship.getCapaShipInventory().getSlots(); i++) {
                if (i != 22 && treasures.contains(ship.getCapaShipInventory().getStackInSlot(i).getItem())) {
                    treasure = true;
                }
            }
            helper.assertTrue(treasure, "At luck 100 fish/junk weights are zero, so a treasure must be selected");
        });
    }

    @GameTest(template = "arena", batch = "isolated_ship_fishing")
    public static void aFullInventoryDropsTheCatchBesideTheShip(GameTestHelper helper) {
        fixture(helper, (ship, entities) -> {
            for (int i = 6; i < ship.getCapaShipInventory().getSlots(); i++) {
                if (i != 22) {
                    ship.getCapaShipInventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
                }
            }
            int before = inventoryCount(ship);
            TaskHelper.generateFishingResult(ship);
            var drops = ship.level().getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(2));
            drops.forEach(entities::add);
            helper.assertTrue(inventoryCount(ship) == before, "Full inventory stays intact");
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "One catch drops beside the ship");
        });
    }

    private static int inventoryCount(BasicEntityShip ship) {
        int count = 0;
        for (int i = 0; i < ship.getCapaShipInventory().getSlots(); i++) {
            count += ship.getCapaShipInventory().getStackInSlot(i).getCount();
        }
        return count;
    }

    private static void fixture(GameTestHelper helper, BiConsumer<BasicEntityShip, GameTestEntities> check) {
        Vec3 relative = new Vec3(4.5D, 4D, 5.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (int x = 6; x <= 8; x++) {
                    for (int z = 4; z <= 6; z++) {
                        for (int y = 1; y <= 3; y++) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                        }
                    }
                }
                BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                helper.assertTrue(ship != null, "Fixture: create ship");
                ship.setNoAi(true);
                ship.setNoGravity(true);
                ship.setInvulnerable(true);
                ship.setEntitySit(false);
                ship.setStateFlag(ID.F.CanFollow, false);
                ship.setShipLevel(50, true);
                ship.setStateMinor(ID.M.NumGrudge, 100_000);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.moveTo(helper.absoluteVec(relative));
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Fixture: add ship");
                GameTestEntities.assertRegistered(helper, ship);
                ship.setGuardedPos(ship.blockPosition().getX(), ship.blockPosition().getY(), ship.blockPosition().getZ(),
                        helper.getLevel().dimension(), 1);
                ship.setDeltaMovement(Vec3.ZERO);
                ship.getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.FISHING_ROD));
                helper.assertTrue(ship.hasGuardDestination() && ship.getGuardedPos(4) == 1, "Fixture: guard position");
                check.accept(ship, entities);
                helper.succeed();
            }
        }, relative);
    }
}
