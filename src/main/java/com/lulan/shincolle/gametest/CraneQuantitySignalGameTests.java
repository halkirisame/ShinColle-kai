package com.lulan.shincolle.gametest;

import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneQuantitySignalGameTests {
    private CraneQuantitySignalGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneExcessAndRemainRespectEachTemplateBeforeMoving(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 0);
                f.crane().setEnabLoad(true);
                f.crane().setEnabUnload(true);
                f.crane().getInventory().setStackInSlot(0, new ItemStack(Items.STONE, 2));
                f.crane().getInventory().setStackInSlot(1, new ItemStack(Items.DIRT, 4));
                f.crane().getInventory().setStackInSlot(9, new ItemStack(Items.GRAVEL, 2));
                f.crane().getInventory().setStackInSlot(10, new ItemStack(Items.SAND, 4));
                f.chest().setItem(0, new ItemStack(Items.STONE, 2));
                f.chest().setItem(1, new ItemStack(Items.DIRT, 5));
                f.chest().setItem(2, new ItemStack(Items.GRAVEL, 2));
                f.chest().setItem(3, new ItemStack(Items.SAND, 3));
                f.ship().getCapaShipInventory().setStackInSlot(6, new ItemStack(Items.STONE, 2));
                f.ship().getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.DIRT, 3));
                f.ship().getCapaShipInventory().setStackInSlot(8, new ItemStack(Items.GRAVEL, 2));
                f.ship().getCapaShipInventory().setStackInSlot(9, new ItemStack(Items.SAND, 5));
                f.crane().setCraneMode(3);
                f.tick(80);
                helper.assertTrue(f.chest().getItem(0).getCount() == 2 && f.chest().getItem(2).getCount() == 2,
                        "Already-satisfied templates must not move while other templates still need items.");
                helper.assertTrue(f.chest().getItem(1).getCount() == 4 && f.chest().getItem(3).getCount() == 4,
                        "Excess must move one item in each direction up to the requested target count.");
                f.working();
                f.crane().setCraneMode(4);
                f.chest().setItem(1, new ItemStack(Items.DIRT, 5));
                f.ship().getCapaShipInventory().setStackInSlot(9, new ItemStack(Items.SAND, 5));
                f.tick(16);
                helper.assertTrue(f.chest().getItem(0).getCount() == 2 && f.chest().getItem(2).getCount() == 2,
                        "Remain must leave already-satisfied source templates untouched.");
                helper.assertTrue(f.chest().getItem(1).getCount() == 4 && f.chest().getItem(3).getCount() == 5
                                && f.ship().getCapaShipInventory().getStackInSlot(9).getCount() == 4,
                        "Remain must stop one item above each source threshold.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneContinuousSignalExpiresAfterInterruption(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 0);
                f.crane().setRedSignalMode(1);
                f.tick(80);
                assertSignal(helper, f, 15);
                f.tick(16);
                assertSignal(helper, f, 15);
                f.crane().setActive(false);
                f.tick(17);
                assertSignal(helper, f, 15);
                f.tick(1);
                assertSignal(helper, f, 0);
                f.crane().setActive(true);
                f.working();
                f.tick(14);
                assertSignal(helper, f, 15);
                f.crane().load(f.crane().saveWithFullMetadata());
                assertSignal(helper, f, 0);
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneCompletionPulseLastsTwoTicksAndModeZeroIsSilent(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 0);
                f.crane().setRedSignalMode(0);
                f.tick(80);
                assertSignal(helper, f, 0);
                f.crane().setRedSignalMode(2);
                f.tick(16);
                assertSignal(helper, f, 0);
                f.crane().setCraneMode(0);
                f.tick(16);
                helper.assertTrue(f.crane().getDockedShip() == null, "The work cycle must complete normally.");
                assertSignal(helper, f, 15);
                f.tick(1);
                assertSignal(helper, f, 15);
                f.tick(1);
                assertSignal(helper, f, 0);
                f.working();
                f.crane().setCraneMode(24);
                f.tick(14);
                f.crane().setActive(false);
                f.tick(1);
                assertSignal(helper, f, 0);
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    private static void assertSignal(GameTestHelper helper, CraneResourceFixture f, int expected) {
        var state = f.crane().getBlockState();
        for (Direction direction : Direction.values()) {
            helper.assertTrue(state.getSignal(helper.getLevel(), f.crane().getBlockPos(), direction) == expected
                            && state.getDirectSignal(helper.getLevel(), f.crane().getBlockPos(), direction) == expected,
                    "Crane weak and strong output must be " + expected + ".");
        }
    }
}
