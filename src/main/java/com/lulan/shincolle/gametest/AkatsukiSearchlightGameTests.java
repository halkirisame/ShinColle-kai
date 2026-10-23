package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/** Exercises the equipment calculation before checking light and detection consumers. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AkatsukiSearchlightGameTests {
    private static final int SHIP_UID = 7_410_002;

    private AkatsukiSearchlightGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void marriedAkatsukiHasSearchlightWithoutEquipment(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = akatsuki(helper, entities);
            for (int i = 0; i < ship.getCapaShipInventory().getSlots(); i++) {
                helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(i).isEmpty(), "Expected empty equipment");
            }
            for (boolean[] flags : new boolean[][]{{true, true}, {false, true}, {true, false}, {false, false}, {true, true}}) {
                flags(helper, ship, flags[0], flags[1]);
                for (int repeat = 0; repeat < 3; repeat++) {
                    ship.calcShipAttributes(31, false);
                    assertLight(helper, ship, flags[0] && flags[1] ? 1 : 0);
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void akatsukiSearchlightAssignmentOverridesEquipment(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = akatsuki(helper, entities);
            flags(helper, ship, false, true);
            ship.calcShipAttributes(31, false);
            float[] empty = ship.getAttrs().getAttrsEquip().clone();
            equipTwoLights(helper, ship);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 2);
            float[] equipped = ship.getAttrs().getAttrsEquip().clone();
            helper.assertTrue(!Arrays.equals(empty, equipped), "Searchlights contributed no equipment attributes");
            flags(helper, ship, true, true);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 1);
            helper.assertTrue(Arrays.equals(equipped, ship.getAttrs().getAttrsEquip()),
                    "Innate searchlight assignment removed equipment attributes");
            flags(helper, ship, true, false);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 2);
            ship.getCapaShipInventory().setStackInSlot(0, ItemStack.EMPTY);
            ship.getCapaShipInventory().setStackInSlot(1, ItemStack.EMPTY);
            flags(helper, ship, true, true);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 1);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void otherShipsKeepEquipmentSearchlight(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            helper.assertTrue(ship != null, "Could not create Hibiki");
            flags(helper, ship, true, true);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 0);
            equipTwoLights(helper, ship);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 2);
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void savedAkatsukiRecalculatesSearchlightAfterEquipmentLoad(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> ShipConsumptionDefaultsGameTests.guarded(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BasicEntityShip source = akatsuki(helper, entities);
                ShipConsumptionDefaultsGameTests.savedFixture(source, SHIP_UID);
                flags(helper, source, true, true);
                equipTwoLights(helper, source);
                CompoundTag saved = new CompoundTag();
                source.addAdditionalSaveData(saved);
                int[] minor = saved.getIntArray("StateMinor");
                minor[ID.M.LevelSearchlight] = 7;
                saved.putIntArray("StateMinor", minor);
                BasicEntityShip world = akatsuki(helper, entities);
                world.readAdditionalSaveData(saved);
                assertLoadedLights(helper, world);
                BasicEntityShip egg = ShipConsumptionDefaultsGameTests.hatch(helper, entities, saved);
                assertLoadedLights(helper, egg);
                helper.succeed();
            } finally {
                ServerDataManager.removeShipData(SHIP_UID);
            }
        }), ShipConsumptionDefaultsGameTests.SPAWN);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void akatsukiSearchlightReachesLightAndDetectionConsumers(GameTestHelper helper) {
        long dayTime = helper.getLevel().getDayTime();
        boolean enabled = ConfigHandler.canSearchlight();
        BlockPos position = BlockPos.containing(helper.absoluteVec(ShipConsumptionDefaultsGameTests.SPAWN)).above(2);
        BlockState previous = helper.getLevel().getBlockState(position);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ConfigHandler.COMMON.canSearchlight.set(true);
            BasicEntityShip ship = akatsuki(helper, entities);
            ship.setPos(helper.absoluteVec(ShipConsumptionDefaultsGameTests.SPAWN));
            ship.setStateMinor(ID.M.NumGrudge, 500);
            ship.setStateFlag(ID.F.NoFuel, false);
            ship.tickCount = 0;
            helper.assertTrue(ship.isAlive() && ship.getGrudge() == 500 && !ship.getStateFlag(ID.F.NoFuel)
                            && ConfigHandler.canSearchlight() && ship.tickCount % ConfigHandler.cdSearchLight() == 0,
                    "Searchlight resource, config or period fixture was ignored");
            LivingEntity target = entities.add(EntityType.ZOMBIE.create(helper.getLevel()));
            helper.assertTrue(target != null, "Could not create detection target");
            target.setInvisible(true);
            helper.assertTrue(target.isInvisible(), "Detection target must be invisible");
            flags(helper, ship, true, true);
            ship.calcShipAttributes(31, false);
            helper.assertTrue(ship.getStateMinor(ID.M.LevelFlare) == 0, "Flare must not supply detection");
            helper.getLevel().setDayTime(6000);
            helper.assertTrue(TargetHelper.canDetectTarget(ship, target), "Innate searchlight did not enable daytime detection");
            flags(helper, ship, true, false);
            ship.calcShipAttributes(31, false);
            helper.assertTrue(!TargetHelper.canDetectTarget(ship, target), "Disabled searchlight still detected invisible target");
            flags(helper, ship, true, true);
            ship.calcShipAttributes(31, false);
            helper.getLevel().setDayTime(18000);
            helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            ship.updateSearchlight();
            helper.assertTrue(helper.getLevel().getBlockState(position).is(ModBlocks.LIGHT_AIR.get()),
                    "Innate searchlight did not reach the night light consumer");
            helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            helper.getLevel().setDayTime(6000);
            ship.updateSearchlight();
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.AIR), "Searchlight placed a light during daytime");
            helper.getLevel().setDayTime(18000);
            ship.setStateFlag(ID.F.NoFuel, true);
            ship.calcShipAttributes(31, false);
            assertLight(helper, ship, 1);
            ship.updateSearchlight();
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.AIR), "Fuel-less searchlight placed a light");
            helper.succeed();
        } finally {
            helper.getLevel().setBlockAndUpdate(position, previous);
            helper.getLevel().setDayTime(dayTime);
            ConfigHandler.COMMON.canSearchlight.set(enabled);
        }
    }

    private static BasicEntityShip akatsuki(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.DESTROYER_AKATSUKI.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create Akatsuki");
        ship.setNoAi(true);
        ship.setInvulnerable(true);
        return ship;
    }

    private static void flags(GameTestHelper helper, BasicEntityShip ship, boolean married, boolean effect) {
        ship.setStateFlag(ID.F.IsMarried, married);
        ship.setStateFlag(ID.F.UseRingEffect, effect);
        helper.assertTrue(ship.getStateFlag(ID.F.IsMarried) == married && ship.getStateFlag(ID.F.UseRingEffect) == effect,
                "Marriage or ring-effect fixture was ignored");
    }

    private static void equipTwoLights(GameTestHelper helper, BasicEntityShip ship) {
        for (int slot = 0; slot < 2; slot++) {
            ship.getCapaShipInventory().setStackInSlot(slot, new ItemStack(ModItems.EQUIP_SEARCHLIGHT.get()));
            helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(slot).is(ModItems.EQUIP_SEARCHLIGHT.get()),
                    "Searchlight equipment fixture was ignored");
        }
    }

    private static void assertLight(GameTestHelper helper, BasicEntityShip ship, int expected) {
        helper.assertTrue(ship.getStateMinor(ID.M.LevelSearchlight) == expected,
                "Expected searchlight " + expected + ", found " + ship.getStateMinor(ID.M.LevelSearchlight));
    }

    private static void assertLoadedLights(GameTestHelper helper, BasicEntityShip ship) {
        helper.assertTrue(ship.getStateFlag(ID.F.IsMarried) && ship.getStateFlag(ID.F.UseRingEffect),
                "Saved marriage or ring-effect state was not loaded");
        for (int slot = 0; slot < 2; slot++) {
            helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(slot).is(ModItems.EQUIP_SEARCHLIGHT.get()),
                    "Saved searchlight equipment was not loaded");
        }
        assertLight(helper, ship, 1);
        flags(helper, ship, true, false);
        ship.calcShipAttributes(31, false);
        assertLight(helper, ship, 2);
    }
}
