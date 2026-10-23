package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.entity.transport.EntityTransportWa;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.IShipCombatRation;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.tileentity.TileEntityVolCore;
import com.lulan.shincolle.utility.BuffHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipMoraleTravelGameTests {
    private static final Vec3 SPAWN = new Vec3(1.5D, 2D, 1.5D);
    private static final UUID OWNER = UUID.fromString("a7000000-0000-4000-8000-000000000001");
    private static final int SHIP_UID = 7_297_001;

    private ShipMoraleTravelGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void moraleCombatWindowUsesStrict128TickBoundary(GameTestHelper helper) {
        TestShip ship = new TestShip(helper.getLevel());
        int[][] cases = {{0, 0, 40}, {1, 0, 41}, {899, 888, 939}, {900, 893, 940},
                {901, 894, 941}, {2099, 2092, 2139}, {2100, 2095, 2140}, {2101, 2096, 2141},
                {3099, 3094, 3139}, {3100, 3095, 3100}, {3101, 3096, 3101}, {3899, 3894, 3899},
                {3900, 3889, 3900}, {3901, 3890, 3891}, {5099, 5088, 5089}, {5100, 5089, 5090},
                {5101, 5090, 5091}, {16000, 15989, 15990}};
        for (int delta : new int[]{128, 129}) {
            for (int[] row : cases) {
                ship.tickCount = 1024;
                ship.setCombatTick(1024 - delta);
                ship.setMorale(row[0]);
                ship.updateMorale();
                int expected = row[delta == 128 ? 1 : 2];
                helper.assertTrue(ship.getMorale() == expected, "delta=" + delta + ", morale=" + row[0]
                        + ": expected " + expected + ", got " + ship.getMorale());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void moraleRecoveryAndDecayKeepExactThresholds(GameTestHelper helper) {
        TestShip ship = new TestShip(helper.getLevel());
        for (int delta : new int[]{601, 600}) {
            for (int[] row : new int[][]{{3099, 3139}, {3100, 3100}, {3101, 3101}, {3900, 3900}, {3901, 3891}}) {
                ship.tickCount = 1024;
                ship.setCombatTick(1024 - delta);
                ship.setMorale(row[0]);
                ship.updateMorale();
                helper.assertTrue(ship.getMorale() == row[1], "Recovery/decay changed at delta=" + delta
                        + ", morale=" + row[0] + ": got " + ship.getMorale());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void moraleBandsKeepStrictBoundaries(GameTestHelper helper) {
        for (int[] row : new int[][]{{899, 4}, {900, 4}, {901, 3}, {2099, 3}, {2100, 3}, {2101, 2},
                {3899, 2}, {3900, 2}, {3901, 1}, {5099, 1}, {5100, 1}, {5101, 0}}) {
            helper.assertTrue(BuffHelper.getMoraleLevel(row[0]) == row[1], "Incorrect morale band at " + row[0]);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void travelMoraleKeepsFiveToFiftyClamp(GameTestHelper helper) {
        withMoveCoefficient(helper, 3, () -> {
            TestShip ship = travelShip(helper, 64);
            consumeAndAssert(helper, ship, 0, 5);
            for (int[] row : new int[][]{{0, 5}, {1, 5}, {9, 5}, {10, 5}, {20, 10}, {100, 50}, {120, 50}}) {
                ship.moveTo(ship.position().add(row[0], 0, 0));
                consumeAndAssert(helper, ship, row[0], row[1]);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void negativeYTravelChargesTwentyBlocks(GameTestHelper helper) {
        heightTravel(helper, -60);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void zeroYTravelChargesTwentyBlocks(GameTestHelper helper) {
        heightTravel(helper, 0);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void positiveYTravelChargesTwentyBlocks(GameTestHelper helper) {
        heightTravel(helper, 64);
        withMoveCoefficient(helper, 3, () -> {
            TestShip ship = travelShip(helper, 64);
            consumeAndAssert(helper, ship, 0, 5);
            ship.moveTo(ship.position().add(12, 0, 16));
            consumeAndAssert(helper, ship, 20, 10);
            ship.moveTo(ship.position().add(20.5D, 0, 0));
            consumeAndAssert(helper, ship, 20, 10);
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void firstFarSpawnSampleNeverChargesDistance(GameTestHelper helper) {
        withMoveCoefficient(helper, 3, () -> {
            for (int y : new int[]{-60, 0, 64}) {
                TestShip ship = travelShip(helper, y);
                ship.moveTo(10000D, y, 12000D);
                consumeAndAssert(helper, ship, 0, 5);
                ship.moveTo(ship.position().add(20, 0, 0));
                consumeAndAssert(helper, ship, 20, 10);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void sameEntityWorldLoadInvalidatesTravelSample(GameTestHelper helper) {
        withMoveCoefficient(helper, 3, () -> {
            BasicEntityShip ship = realTransport(helper);
            ship.moveTo(100D, 64D, 100D);
            consumeAndAssert(helper, ship, 0, 5);
            ship.moveTo(10000D, 64D, 12000D);
            CompoundTag saved = savedShip(ship);
            ship.moveTo(100D, 64D, 100D);
            ship.setCombatTick(123);
            ship.load(saved);
            helper.assertTrue(ship.position().equals(new Vec3(10000D, 64D, 12000D))
                    && ship.getCombatTick() == 123, "Same-entity load did not restore position or changed LastCombat");
            assertSavedShip(helper, ship, saved);
            consumeAndAssert(helper, ship, 0, 5);
            ship.moveTo(ship.position().add(20, 0, 0));
            consumeAndAssert(helper, ship, 20, 10);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void newEntityWorldLoadStartsWithoutTravelSample(GameTestHelper helper) {
        withMoveCoefficient(helper, 3, () -> {
            for (int y : new int[]{-60, 0, 64}) {
                BasicEntityShip source = realTransport(helper);
                source.moveTo(10000D, y, 12000D);
                CompoundTag before = savedShip(source);
                consumeAndAssert(helper, source, 0, 5);
                CompoundTag saved = savedShip(source);
                helper.assertTrue(before.getAllKeys().equals(saved.getAllKeys())
                        && saved.getIntArray("StateMinor").length == 45,
                        "Travel sampling added saved keys or changed minor layout");
                BasicEntityShip loaded = realTransport(helper);
                loaded.load(saved);
                helper.assertTrue(source != loaded && source.position().equals(loaded.position()), "New load position changed");
                assertSavedShip(helper, loaded, saved);
                consumeAndAssert(helper, loaded, 0, 5);
                loaded.moveTo(loaded.position().add(20, 0, 0));
                consumeAndAssert(helper, loaded, 20, 10);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel", timeoutTicks = 200)
    public static void dimensionCloneStartsWithoutTravelSample(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> withMoveCoefficient(helper, 3, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip source = entities.add(realTransport(helper));
                source.moveTo(helper.absoluteVec(SPAWN));
                helper.assertTrue(helper.getLevel().addFreshEntity(source), "Could not register departing ship");
                GameTestEntities.assertRegistered(helper, source);
                consumeAndAssert(helper, source, 0, 5);
                ServerLevel destination = helper.getLevel().getServer().getLevel(Level.NETHER);
                helper.assertTrue(destination != null && destination != source.level(), "Missing different destination");
                Vec3 arrival = new Vec3(source.getX(), 0D, source.getZ());
                destination.getChunk(BlockPos.containing(arrival));
                Entity moved = source.changeDimension(destination, new ITeleporter() {
                    @Override
                    public PortalInfo getPortalInfo(Entity entity, ServerLevel level,
                            Function<ServerLevel, PortalInfo> vanilla) {
                        return new PortalInfo(arrival, Vec3.ZERO, 0F, 0F);
                    }

                    @Override
                    public Entity placeEntity(Entity entity, ServerLevel from, ServerLevel to, float yaw,
                            Function<Boolean, Entity> repositionEntity) {
                        return repositionEntity.apply(false);
                    }
                });
                if (moved != null) entities.add(moved);
                helper.assertTrue(moved instanceof EntityTransportWa && moved != source && source.isRemoved()
                        && moved.level() == destination && moved.position().equals(arrival),
                        "Dimension travel did not create the expected new entity at Y=0");
                BasicEntityShip loaded = (BasicEntityShip) moved;
                loaded.tickCount = 256;
                consumeAndAssert(helper, loaded, 0, 5);
                loaded.moveTo(loaded.position().add(20, 0, 0));
                consumeAndAssert(helper, loaded, 20, 10);
                helper.succeed();
            }
        }), SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void sameEntityDimensionSwitchInvalidatesTravelSample(GameTestHelper helper) {
        withMoveCoefficient(helper, 3, () -> {
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "Missing Nether");
            for (boolean roundTrip : new boolean[]{false, true}) {
                TestShip ship = travelShip(helper, 64);
                consumeAndAssert(helper, ship, 0, 5);
                ship.setCombatTick(123);
                ship.switchLevel(nether);
                if (roundTrip) ship.switchLevel(helper.getLevel());
                ship.moveTo(10000D, 64D, 12000D);
                helper.assertTrue(ship.getCombatTick() == 123, "Dimension boundary changed LastCombat");
                consumeAndAssert(helper, ship, 0, 5);
                ship.moveTo(ship.position().add(20, 0, 0));
                consumeAndAssert(helper, ship, 20, 10);
            }
            TestShip same = travelShip(helper, 64);
            consumeAndAssert(helper, same, 0, 5);
            same.switchLevel(helper.getLevel());
            same.moveTo(same.position().add(20, 0, 0));
            consumeAndAssert(helper, same, 20, 10);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel", timeoutTicks = 200)
    public static void deathEggRespawnStartsWithoutTravelSample(GameTestHelper helper) {
        Vec3 hatchRelative = new Vec3(1.5D, -helper.absolutePos(BlockPos.ZERO).getY(), 1.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> withMoveCoefficient(helper, 3, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip source = entities.add(realTransport(helper));
                source.moveTo(helper.absoluteVec(SPAWN));
                helper.assertTrue(helper.getLevel().addFreshEntity(source), "Could not register dying transport");
                GameTestEntities.assertRegistered(helper, source);
                consumeAndAssert(helper, source, 0, 5);
                CompoundTag saved = savedShip(source);
                helper.assertTrue(source.getStateFlag(ID.F.CanDrop), "Death egg drop disabled");
                source.deathTime = ConfigHandler.deathTime() - 1;
                invoke(source, "tickDeath");
                helper.assertTrue(source.isRemoved(), "Death did not remove old transport");
                List<BasicEntityItem> drops = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class,
                        source.getBoundingBox().inflate(2D), item -> item.getEntityItem().is(ModItems.SHIP_SPAWN_EGG.get()));
                helper.assertTrue(drops.size() == 1, "Expected one real death egg, got " + drops.size());
                BasicEntityItem drop = entities.add(drops.get(0));
                ItemStack egg = drop.getEntityItem().copy();
                drop.discard();
                helper.assertTrue(egg.hasTag(), "Death egg missing saved data");
                FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OWNER, "morale_admiral"));
                boolean creative = player.getAbilities().instabuild;
                Vec3 hatchPosition = helper.absoluteVec(hatchRelative);
                BlockPos floor = BlockPos.containing(hatchPosition).below();
                try {
                    player.getAbilities().instabuild = true;
                    InteractionResult result = egg.getItem().useOn(new UseOnContext(helper.getLevel(), player,
                            InteractionHand.MAIN_HAND, egg,
                            new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
                    helper.assertTrue(result == InteractionResult.CONSUME, "Death egg use failed: " + result);
                } finally {
                    player.getAbilities().instabuild = creative;
                }
                List<EntityTransportWa> ships = helper.getLevel().getEntitiesOfClass(EntityTransportWa.class,
                        new AABB(BlockPos.containing(hatchPosition)));
                for (EntityTransportWa candidate : ships) entities.add(candidate);
                helper.assertTrue(ships.size() == 1, "Expected one restored transport, got " + ships.size());
                BasicEntityShip loaded = ships.get(0);
                helper.assertTrue(loaded != source && loaded.getY() == 0D, "Death egg must create new transport at Y=0");
                GameTestEntities.assertRegistered(helper, loaded);
                assertSavedShip(helper, loaded, saved);
                loaded.tickCount = 256;
                consumeAndAssert(helper, loaded, 0, 5);
                loaded.moveTo(loaded.position().add(20, 0, 0));
                consumeAndAssert(helper, loaded, 20, 10);
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(SHIP_UID);
            }
        }), SPAWN, hatchRelative);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void sameDimensionTeleportKeepsEndpointCharge(GameTestHelper helper) {
        withMoveCoefficient(helper, 3, () -> {
            for (int y : new int[]{64, -60, 0}) {
                TestShip ship = travelShip(helper, y);
                consumeAndAssert(helper, ship, 0, 5);
                ship.teleportTo(ship.getX() + 20D, y, ship.getZ());
                consumeAndAssert(helper, ship, 20, 10);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel")
    public static void zeroMoveCoefficientKeepsMoraleAndIdleCost(GameTestHelper helper) {
        withMoveCoefficient(helper, 0, () -> {
            TestShip ship = travelShip(helper, -60);
            consumeAndAssert(helper, ship, 0, 5);
            ship.moveTo(ship.position().add(20, 0, 0));
            consumeAndAssert(helper, ship, 20, 10);
        });
        withMoveCoefficient(helper, 3, () -> {
            TestShip ship = travelShip(helper, 64);
            consumeAndAssert(helper, ship, 0, 5);
            for (int tick : new int[]{200, 201}) {
                ship.tickCount = tick;
                ship.moveTo(ship.position().add(20, 0, 0));
                consumeAndAssert(helper, ship, 20, 10);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel", timeoutTicks = 200)
    public static void periodicMoraleRunsAfterConsumptionAndRation(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                TestShip fed = periodicShip(helper, entities);
                fed.setMorale(2105);
                fed.setStateMinor(ID.M.UseCombatRation, 3);
                fed.setFoodSaturation(0);
                ItemStack ration = new ItemStack(ModItems.COMBAT_RATION.get(), 2);
                int gain = ((IShipCombatRation) ration.getItem()).getMoraleValue(0);
                fed.getCapaShipInventory().setStackInSlot(6, ration);
                helper.assertTrue(fed.getStateMinor(ID.M.UseCombatRation) == 3
                        && fed.getFoodSaturation() < fed.getFoodSaturationMax(), "Ration fixture was ignored");
                fed.aiStep();
                helper.assertTrue(fed.events.equals(List.of("consume:2105", "consumed:2100", "ration:2100",
                        "fed:" + (2100 + gain), "morale:" + (2100 + gain))), "Wrong periodic order: " + fed.events);
                helper.assertTrue(fed.getCapaShipInventory().getStackInSlot(6).getCount() == 1,
                        "Periodic ration must consume exactly one item");
                // The selected ration leaves morale in the unchanged middle band.
                helper.assertTrue(2100 + gain >= 3100 && 2100 + gain <= 3900, "Ration fixture left middle band");
                helper.assertTrue(fed.getMorale() == 2100 + gain, "Unexpected fed periodic morale");

                TestShip resting = periodicShip(helper, entities);
                resting.setMorale(1000);
                resting.aiStep();
                helper.assertTrue(resting.events.equals(List.of("consume:1000", "consumed:995", "morale:995")),
                        "Foodless periodic order changed: " + resting.events);
                helper.assertTrue(resting.getMorale() == 1035, "Foodless recovery expected 1035, got " + resting.getMorale());
                helper.succeed();
            }
        }, SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "morale_travel", timeoutTicks = 200)
    public static void emptyFuelSkipsPeriodicMoraleRecovery(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                for (boolean alreadyEmpty : new boolean[]{false, true}) {
                    TestShip ship = periodicShip(helper, entities);
                    ship.setStateMinor(ID.M.NumGrudge, alreadyEmpty ? 0 : ship.getGrudgeConsumption());
                    ship.setStateFlag(ID.F.NoFuel, alreadyEmpty);
                    ship.setMorale(alreadyEmpty ? 0 : 1000);
                    helper.assertTrue(ship.getGrudge() == (alreadyEmpty ? 0 : ship.getGrudgeConsumption())
                            && ship.getStateFlag(ID.F.NoFuel) == alreadyEmpty, "Empty fuel fixture was ignored");
                    ship.aiStep();
                    helper.assertTrue(ship.getGrudge() == 0 && ship.getStateFlag(ID.F.NoFuel)
                            && ship.getMorale() == 0, "Empty ship recovered morale or fuel");
                    helper.assertTrue(ship.events.stream().noneMatch(event -> event.startsWith("morale:")),
                            "Empty ship invoked periodic morale: " + ship.events);
                    helper.assertTrue(ship.events.stream().anyMatch(event -> event.startsWith("consumed:")),
                            "Empty ship skipped consumption/sample update");
                }
                helper.succeed();
            }
        }, SPAWN);
    }

    @GameTest(template = "arena", batch = "morale_travel", timeoutTicks = 200)
    public static void volCoreStillRequiresMoreThan128CombatTicks(GameTestHelper helper) {
        Vec3 wet = new Vec3(3.5D, 2D, 2.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos water = helper.absolutePos(new BlockPos(3, 2, 2));
            helper.getLevel().setBlock(core, ModBlocks.VOL_CORE.get().defaultBlockState(), 3);
            helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            TileEntityVolCore tile = (TileEntityVolCore) helper.getLevel().getBlockEntity(core);
            helper.assertTrue(tile != null, "Core fixture missing");
            tile.setPowerRemained(1000);
            tile.setBtnActive(true);
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                TestShip ship = entities.add(new TestShip(helper.getLevel()));
                resources(helper, ship);
                ship.moveTo(helper.absoluteVec(wet));
                ship.setHealth(ship.getMaxHealth() * 0.5F);
                ship.tickCount = 1024;
                ship.setCombatTick(896);
                helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Could not register submerged ship");
                GameTestEntities.assertRegistered(helper, ship);
                float health = ship.getHealth();
                int morale = ship.getMorale();
                coreTicks(helper, tile);
                helper.assertTrue(ship.getHealth() == health && ship.getMorale() == morale,
                        "Core recovered ship at delta 128");
                ship.setCombatTick(895);
                coreTicks(helper, tile);
                helper.assertTrue(ship.getMorale() == morale + 80 && ship.getHealth() > health,
                        "Core did not recover health and 80 morale at delta 129");
                helper.succeed();
            }
        }, wet);
    }

    private static void coreTicks(GameTestHelper helper, TileEntityVolCore tile) {
        for (int i = 0; i < 48; i++) {
            TileEntityVolCore.serverTick(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
        }
    }

    private static TestShip periodicShip(GameTestHelper helper, GameTestEntities entities) {
        TestShip ship = entities.add(new TestShip(helper.getLevel()));
        resources(helper, ship);
        ship.moveTo(helper.absoluteVec(SPAWN));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Could not register periodic ship");
        GameTestEntities.assertRegistered(helper, ship);
        ship.tickCount = 24;
        ship.aiStep(); // Finish goal registration before setting up the measured cycle.
        ship.updateConsumeItem();
        resources(helper, ship);
        ship.tickCount = 256;
        ship.setCombatTick(127);
        ship.events.clear();
        helper.assertTrue(!ship.stopAI && ship.isAlive(), "Periodic ship cannot run aiStep");
        return ship;
    }

    private static void resources(GameTestHelper helper, BasicEntityShip ship) {
        ship.setNoAi(true);
        ship.setNoGravity(true);
        ship.setInvulnerable(true);
        ship.removeEffect(MobEffects.HUNGER);
        ship.setShipLevel(37, false);
        ship.setStateMinor(ID.M.ExpCurrent, 7);
        ship.setExpNext();
        ship.setMorale(3000);
        ship.calcShipAttributes(31, false);
        ship.setHealth(ship.getMaxHealth());
        ship.setStateMinor(ID.M.NumGrudge, 10000);
        ship.setStateMinor(ID.M.NumAmmoLight, 200);
        ship.setStateMinor(ID.M.NumAmmoHeavy, 200);
        ship.setStateMinor(ID.M.UseCombatRation, 4);
        ship.setFoodSaturation(ship.getFoodSaturationMax());
        ship.setStateFlag(ID.F.NoFuel, false);
        helper.assertTrue(ship.getGrudge() == 10000 && !ship.getStateFlag(ID.F.NoFuel)
                && ship.getStateMinor(ID.M.NumAmmoLight) == 200 && ship.getStateMinor(ID.M.NumAmmoHeavy) == 200
                && ship.getFoodSaturation() == ship.getFoodSaturationMax()
                && ship.getStateMinor(ID.M.UseCombatRation) == 4 && !ship.hasEffect(MobEffects.HUNGER)
                && ship.getLevel() == 37 && ship.getStateMinor(ID.M.ExpCurrent) == 7
                && ship.getAttrs().getAttrsBuffed(ID.Attrs.XP) > 0F, "Resource fixture was ignored");
        for (int slot = 0; slot < ship.getCapaShipInventory().getSlots(); slot++) {
            helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(slot).isEmpty(), "Fixture inventory not empty");
        }
    }

    private static TestShip travelShip(GameTestHelper helper, double y) {
        TestShip ship = new TestShip(helper.getLevel());
        resources(helper, ship);
        ship.tickCount = 256;
        ship.moveTo(100D, y, 100D);
        helper.assertTrue(ship.getY() == y && ship.getShipType() == ID.ShipType.TRANSPORT, "Height/type fixture ignored");
        return ship;
    }

    private static BasicEntityShip realTransport(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.AP_WA.get().create(helper.getLevel());
        helper.assertTrue(ship != null, "Could not create transport");
        resources(helper, ship);
        ship.tickCount = 256;
        return ship;
    }

    private static void heightTravel(GameTestHelper helper, int y) {
        withMoveCoefficient(helper, 3, () -> {
            TestShip ship = travelShip(helper, y);
            consumeAndAssert(helper, ship, 0, 5);
            ship.moveTo(ship.position().add(20, 0, 0));
            consumeAndAssert(helper, ship, 20, 10);
            TestShip crossing = travelShip(helper, -1);
            consumeAndAssert(helper, crossing, 0, 5);
            crossing.moveTo(crossing.getX(), 1D, crossing.getZ());
            consumeAndAssert(helper, crossing, 2, 5);
            crossing.moveTo(crossing.getX(), -1D, crossing.getZ());
            consumeAndAssert(helper, crossing, 2, 5);
        });
    }

    private static void consumeAndAssert(GameTestHelper helper, BasicEntityShip ship, int distance, int moraleCost) {
        ship.setMorale(3000);
        int grudge = ship.getGrudge();
        int exp = ship.getStateMinor(ID.M.ExpCurrent);
        int light = ship.getStateMinor(ID.M.NumAmmoLight);
        int heavy = ship.getStateMinor(ID.M.NumAmmoHeavy);
        int moveCost = distance * ConfigHandler.consumeGrudgeAction[ID.ShipConsume.Move];
        int input = ship.tickCount > 200 ? (int) (moveCost * 0.2F) : 0;
        float xp = ship.getAttrs().getAttrsBuffed(ID.Attrs.XP);
        int gain = (int) (input * xp);
        int level = ship.getLevel();
        helper.assertTrue(grudge > moveCost + ship.getGrudgeConsumption() && !ship.getStateFlag(ID.F.NoFuel)
                && !ship.hasEffect(MobEffects.HUNGER) && exp + gain < ship.getExpNextValue(),
                "Measurement fixture would refuel, starve, or level up");
        if (ship instanceof TestShip test) {
            test.experienceInput = 0;
            test.updateConsumeItem();
            helper.assertTrue(test.experienceInput == input, "Wrong transport experience input: expected " + input
                    + ", got " + test.experienceInput);
        } else {
            invoke(ship, "updateConsumeItem");
        }
        helper.assertTrue(ship.getMorale() == 3000 - moraleCost, "Y=" + ship.getY() + ", D=" + distance
                + ": expected morale " + (3000 - moraleCost) + ", got " + ship.getMorale());
        helper.assertTrue(grudge - ship.getGrudge() == moveCost + ship.getGrudgeConsumption(),
                "Y=" + ship.getY() + ", D=" + distance + ": wrong grudge cost " + (grudge - ship.getGrudge()));
        helper.assertTrue(ship.getStateMinor(ID.M.ExpCurrent) - exp == gain && ship.getLevel() == level,
                "Wrong XP-adjusted transport gain: expected " + gain + " at multiplier " + xp);
        helper.assertTrue(ship.getStateMinor(ID.M.NumAmmoLight) == light && ship.getStateMinor(ID.M.NumAmmoHeavy) == heavy,
                "Travel with full ammunition changed quantities");
    }

    private static void withMoveCoefficient(GameTestHelper helper, int value, Runnable body) {
        int old = ConfigHandler.consumeGrudgeAction[ID.ShipConsume.Move];
        try {
            ConfigHandler.consumeGrudgeAction[ID.ShipConsume.Move] = value;
            helper.assertTrue(ConfigHandler.consumeGrudgeAction[ID.ShipConsume.Move] == value, "Move coefficient ignored");
            body.run();
        } finally {
            ConfigHandler.consumeGrudgeAction[ID.ShipConsume.Move] = old;
        }
    }

    private static CompoundTag savedShip(BasicEntityShip ship) {
        ship.setOwnerUUID(OWNER);
        ship.ownerName = "morale_admiral";
        ship.setShipUID(SHIP_UID);
        ship.setPlayerUID(729_701);
        ship.setMorale(3000);
        ship.setGrudgeConsumption(77);
        ship.setAmmoConsumption(33);
        ship.getCapaShipInventory().setStackInSlot(CapaShipInventory.EquipSlots, new ItemStack(Items.DIAMOND, 7));
        return ship.saveWithoutId(new CompoundTag());
    }

    private static void assertSavedShip(GameTestHelper helper, BasicEntityShip ship, CompoundTag saved) {
        int[] minor = saved.getIntArray("StateMinor");
        for (int id : new int[]{ID.M.ShipLevel, ID.M.ExpCurrent, ID.M.NumGrudge, ID.M.NumAmmoLight,
                ID.M.NumAmmoHeavy, ID.M.Morale, ID.M.ShipUID, ID.M.PlayerUID}) {
            helper.assertTrue(ship.getStateMinor(id) == minor[id], "Saved value changed at index " + id);
        }
        helper.assertTrue(OWNER.equals(ship.getOwnerUUID()) && "morale_admiral".equals(ship.ownerName), "Saved owner changed");
        helper.assertTrue(saved.getCompound(CapaShipInventory.InvName).equals(ship.getCapaShipInventory().serializeNBT()),
                "Saved inventory changed");
        helper.assertTrue(ship.getGrudgeConsumption() == ConfigHandler.consumeGrudgeShip[ID.ShipConsume.AP]
                && ship.getAmmoConsumption() == ConfigHandler.consumeAmmoShip[ID.ShipConsume.AP],
                "Load did not retain class consumption re-derivation");
    }

    private static void invoke(BasicEntityShip ship, String methodName) {
        try {
            var method = BasicEntityShip.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(ship);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Cannot invoke " + methodName + ": " + failure);
        }
    }

    private static final class TestShip extends EntityTransportWa {
        private final List<String> events = new ArrayList<>();
        private int experienceInput;

        private TestShip(Level level) {
            super(ModEntities.AP_WA.get(), level);
        }

        private void switchLevel(Level level) {
            super.setLevel(level);
        }

        @Override
        public void addShipExp(int exp) {
            experienceInput = exp;
            super.addShipExp(exp);
        }

        @Override
        protected void updateConsumeItem() {
            events.add("consume:" + getMorale());
            super.updateConsumeItem();
            events.add("consumed:" + getMorale());
        }

        @Override
        protected void useCombatRation() {
            events.add("ration:" + getMorale());
            super.useCombatRation();
            events.add("fed:" + getMorale());
        }

        @Override
        protected void updateMorale() {
            events.add("morale:" + getMorale());
            super.updateMorale();
        }
    }
}
