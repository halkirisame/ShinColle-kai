package com.lulan.shincolle.gametest;

import com.lulan.shincolle.block.BasicBlockMulti;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.lulan.shincolle.utility.MulitBlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LargeShipyardUnformedGameTests {

    private static final BlockPos CORE = new BlockPos(4, 4, 4);
    private static final BlockPos COMPONENT = CORE.offset(1, -1, 1);

    private LargeShipyardUnformedGameTests() {
    }

    @GameTest(template = "arena")
    public static void brokenShipyardFreezesProgressFuelAndInputsThenResumes(GameTestHelper helper) {
        withBuildingShipyard(helper, core -> {
            int progress = core.getPowerConsumed();
            int fuel = core.getPowerRemained();
            int goal = core.getPowerGoal();
            breakComponent(helper, core);
            var items = core.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            ItemStack[] inputs = {
                    new ItemStack(Items.COAL, 2), new ItemStack(ModItems.GRUDGE.get(), 2),
                    new ItemStack(ModItems.ABYSS_METAL.get(), 2), new ItemStack(ModItems.AMMO.get(), 2),
                    new ItemStack(ModItems.POLYMETAL_NODULE.get(), 2)
            };
            for (int i = 0; i < inputs.length; i++) {
                helper.assertTrue(items.insertItem(i + 1, inputs[i].copy(), false).isEmpty(),
                        "Unformed core must still accept queued input " + i);
            }
            IFluidHandler tank = core.getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .orElseThrow(IllegalStateException::new);
            helper.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 1500), IFluidHandler.FluidAction.EXECUTE) == 1500,
                    "Unformed core must still expose its fuel tank for queued lava");

            for (int tick = 1; tick <= 20; tick++) {
                tick(helper, core);
                helper.assertTrue(core.getPowerConsumed() == progress,
                        "Broken shipyard must freeze progress at tick " + tick + ": "
                                + core.getPowerConsumed() + " != " + progress);
                helper.assertTrue(core.getPowerRemained() == fuel && core.getPowerGoal() == goal,
                        "Broken shipyard must preserve fuel and goal at tick " + tick);
                for (int i = 0; i < 4; i++) {
                    helper.assertTrue(core.getMatStock(i) == 2000 && core.getMatBuild(i) == 1000,
                            "Broken shipyard must preserve material stock and selection " + i);
                }
                for (int i = 0; i < inputs.length; i++) {
                    helper.assertTrue(ItemStack.matches(items.getStackInSlot(i + 1), inputs[i]),
                            "Broken shipyard must leave queued input unchanged " + i);
                }
                helper.assertTrue(tank.getFluidInTank(0).getAmount() == 1500
                                && core.getInventory().getStackInSlot(0).isEmpty() && core.getBuildType() == 1,
                        "Broken shipyard must not consume lava or complete the build");
                assertInactiveAndUnformed(helper, core);
            }

            helper.getLevel().setBlock(helper.absolutePos(COMPONENT), ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
            form(helper, core);
            helper.assertTrue(core.getPowerConsumed() == progress && core.getPowerRemained() == fuel
                            && core.getBlockState().getValue(BasicBlockMulti.MBS) == 1,
                    "Reformation must retain the stopped build and start in MBS1");
            tick(helper, core);
            helper.assertTrue(core.getPowerConsumed() == progress + 48
                            && core.saveWithoutMetadata().getBoolean("Active")
                            && core.getBlockState().getValue(BasicBlockMulti.MBS) == 2,
                    "Reformed shipyard must resume from retained progress and become MBS2");
            helper.assertTrue(items.getStackInSlot(1).getCount() == 1
                            && tank.getFluidInTank(0).getAmount() == 500 && core.getPowerRemained() > fuel,
                    "Reformation must resume queued item and liquid fuel intake");
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(core.getMatStock(i) > 2000 && items.getStackInSlot(i + 2).getCount() == 1,
                        "Reformation must resume queued material intake " + i);
            }
        });
    }

    @GameTest(template = "arena")
    public static void brokenShipyardFuelExhaustionDoesNotRestoreFormedBlockState(GameTestHelper helper) {
        withBuildingShipyard(helper, core -> {
            helper.assertTrue(core.saveWithoutMetadata().getBoolean("Active") && core.isBuilding()
                            && core.getPowerGoal() > core.getPowerConsumed() + 48
                            && ConfigHandler.tileShipyardLarge[1] == 48 && ConfigHandler.tileShipyardLargeFuelUse == 1,
                    "State-transition fixture must be active, unfinished, and consume positive fuel");
            for (int slot = TileMultiGrudgeHeavy.SLOT_INPUT_START; slot <= TileMultiGrudgeHeavy.SLOT_INPUT_END; slot++) {
                helper.assertTrue(core.getInventory().getStackInSlot(slot).isEmpty(),
                        "State-transition fixture must have no input fuel or instant material");
            }
            helper.assertTrue(core.getCapability(ForgeCapabilities.FLUID_HANDLER)
                            .orElseThrow(IllegalStateException::new).getFluidInTank(0).isEmpty(),
                    "State-transition fixture must have no tank fuel");
            int progress = core.getPowerConsumed();
            breakComponent(helper, core);
            core.setPowerRemained(0);
            helper.assertTrue(core.getPowerRemained() == 0 && !core.hasRemainedPower() && !core.isBuilding(),
                    "Exhausted fixture must not be refillable or building");
            tick(helper, core);
            helper.assertTrue(core.getBlockState().getValue(BasicBlockMulti.MBS) == 0,
                    "Fuel exhaustion after destruction must not rewrite MBS0 to MBS1");
            assertInactiveAndUnformed(helper, core);
            helper.assertTrue(core.getPowerConsumed() == progress && core.getPowerRemained() == 0,
                    "Stopping activity must retain progress without adding fuel");
        });
    }

    @GameTest(template = "arena")
    public static void brokenShipyardDoesNotReleaseStockOrConsumeInstantMaterial(GameTestHelper helper) {
        withBuildingShipyard(helper, core -> {
            breakComponent(helper, core);
            core.setInvMode(1);
            core.setSelectMat(3);
            core.getInventory().setStackInSlot(1, new ItemStack(ModItems.INSTANT_CON_MAT.get()));
            int progress = core.getPowerConsumed();
            int fuel = core.getPowerRemained();
            int goal = core.getPowerGoal();
            for (int tick = 1; tick <= 20; tick++) {
                tick(helper, core);
                helper.assertTrue(core.getInventory().getStackInSlot(1).is(ModItems.INSTANT_CON_MAT.get())
                                && core.getInventory().getStackInSlot(1).getCount() == 1
                                && core.getInventory().getStackInSlot(0).isEmpty(),
                        "Broken shipyard must not consume instant material or produce output");
                for (int slot = 2; slot <= TileMultiGrudgeHeavy.SLOT_INPUT_END; slot++) {
                    helper.assertTrue(core.getInventory().getStackInSlot(slot).isEmpty(),
                            "Broken shipyard must not release material into slot " + slot);
                }
                for (int i = 0; i < 4; i++) {
                    helper.assertTrue(core.getMatStock(i) == 2000, "Broken shipyard must not release stock " + i);
                }
                helper.assertTrue(core.getPowerConsumed() == progress && core.getPowerRemained() == fuel
                                && core.getPowerGoal() == goal,
                        "Release mode must also retain the stopped build");
                assertInactiveAndUnformed(helper, core);
            }
        });
    }

    private static void withBuildingShipyard(GameTestHelper helper, Consumer<TileMultiGrudgeHeavy> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            double[] oldConfig = ConfigHandler.tileShipyardLarge;
            double oldFuelUse = ConfigHandler.tileShipyardLargeFuelUse;
            try {
                ConfigHandler.tileShipyardLarge = new double[] {1382400, 48, 1};
                ConfigHandler.tileShipyardLargeFuelUse = 1;
                TileMultiGrudgeHeavy.reloadConfig();
                BlockPos pos = helper.absolutePos(CORE);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        helper.getLevel().setBlock(pos.offset(dx, -2, dz),
                                ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
                        if (dx != 0 && dz != 0) {
                            helper.getLevel().setBlock(pos.offset(dx, -1, dz),
                                    ModBlocks.POLYMETAL.get().defaultBlockState(), 3);
                        }
                    }
                }
                helper.getLevel().setBlock(pos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState(), 3);
                TileMultiGrudgeHeavy core = (TileMultiGrudgeHeavy) helper.getLevel().getBlockEntity(pos);
                form(helper, core);
                core.setBuildType(1);
                for (int i = 0; i < 4; i++) {
                    core.setMatStock(i, 2000);
                    core.setMatBuild(i, 1000);
                    helper.assertTrue(core.getMatStock(i) == 2000 && core.getMatBuild(i) == 1000,
                            "Building fixture must have sufficient selected material " + i);
                }
                core.setPowerRemained(100000);
                helper.assertTrue(core.getPowerRemained() == 100000 && core.getBuildType() == 1,
                        "Building fixture must apply fuel and build type");
                for (int tick = 0; tick < 3; tick++) {
                    tick(helper, core);
                }
                helper.assertTrue(core.getPowerConsumed() == 144 && core.getPowerRemained() == 99856
                                && core.getPowerGoal() > 144 + 20 * 48 && core.canBuild()
                                && core.saveWithoutMetadata().getBoolean("Active")
                                && core.getBlockState().getValue(BasicBlockMulti.MBS) == 2,
                        "Formed fixture must demonstrably build before destruction without nearing completion");
                verification.accept(core);
            } finally {
                ConfigHandler.tileShipyardLarge = oldConfig;
                ConfigHandler.tileShipyardLargeFuelUse = oldFuelUse;
                TileMultiGrudgeHeavy.reloadConfig();
            }
            helper.succeed();
        }, Vec3.atCenterOf(CORE), Vec3.atCenterOf(CORE.offset(-1, -2, -1)),
                Vec3.atCenterOf(CORE.offset(1, 0, 1)));
    }

    private static void form(GameTestHelper helper, TileMultiGrudgeHeavy core) {
        BlockPos pos = core.getBlockPos();
        helper.assertTrue(MulitBlockHelper.checkMultiBlockForm(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ()) == 1,
                "Fixture must match an unformed large shipyard");
        MulitBlockHelper.setupStructure(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), 1);
        helper.assertTrue(core.hasCorePos() && core.getCorePos().equals(pos),
                "Real structure formation must assign the center's core position");
    }

    private static void breakComponent(GameTestHelper helper, TileMultiGrudgeHeavy core) {
        helper.assertTrue(helper.getLevel().removeBlock(helper.absolutePos(COMPONENT), false),
                "A real polymetal component must be removed through onRemove");
        helper.assertTrue(helper.getLevel().getBlockEntity(core.getBlockPos()) == core && !core.hasCorePos()
                        && core.getBlockState().getValue(BasicBlockMulti.MBS) == 0,
                "Component removal must detach the same surviving core and reset it to MBS0");
    }

    private static void assertInactiveAndUnformed(GameTestHelper helper, TileMultiGrudgeHeavy core) {
        helper.assertTrue(!core.hasCorePos() && !core.saveWithoutMetadata().getBoolean("Active")
                        && core.getBlockState().getValue(BasicBlockMulti.MBS) == 0,
                "Unformed tick must clear activity without restoring a formed block state");
    }

    private static void tick(GameTestHelper helper, TileMultiGrudgeHeavy core) {
        TileMultiGrudgeHeavy.serverTick(helper.getLevel(), core.getBlockPos(), core.getBlockState(), core);
    }
}
