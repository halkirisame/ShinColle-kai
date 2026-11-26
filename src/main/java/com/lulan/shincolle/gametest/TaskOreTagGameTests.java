package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.task.TaskSideMask;
import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.RecipePaperData;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.InventoryHelper;
import com.lulan.shincolle.utility.TaskHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaskOreTagGameTests {
    private TaskOreTagGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void oreMatchingAcceptsOnlySharedMaterialChildTags(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_ORE);
        ItemStack deepslate = new ItemStack(Items.DEEPSLATE_IRON_ORE);
        iron.getOrCreateTag().putInt("TaskMarker", 1);
        deepslate.getOrCreateTag().putInt("TaskMarker", 2);
        helper.assertTrue(!InventoryHelper.matchTargetItem(iron, deepslate, false, false, false),
                "A shared material must not override item identity when ore matching is off");
        helper.assertTrue(InventoryHelper.matchTargetItem(iron, deepslate, true, true, true)
                        && InventoryHelper.matchTargetItem(deepslate, iron, true, true, true),
                "Different items sharing forge:ores/iron must match even with different NBT");
        helper.assertTrue(!InventoryHelper.matchTargetItem(new ItemStack(Items.COPPER_INGOT),
                        new ItemStack(Items.IRON_INGOT), false, false, true),
                "The forge:ingots parent tag must not equate different materials");
        helper.assertTrue(!InventoryHelper.matchTargetItem(iron, new ItemStack(Items.GOLD_ORE), false, false, true),
                "Shared ore rate and ground tags must not equate different ores");
        helper.assertTrue(!InventoryHelper.matchTargetItem(new ItemStack(Items.GLASS),
                        new ItemStack(Items.RED_STAINED_GLASS), false, false, true),
                "forge:glass/silica is not a material category and must not match different colors");
        helper.assertTrue(!InventoryHelper.matchTargetItem(new ItemStack(Items.OAK_PLANKS),
                        new ItemStack(Items.SPRUCE_PLANKS), false, false, true),
                "Shared minecraft tags must not provide task material alternatives");
        ItemStack differentNbt = iron.copy();
        differentNbt.getOrCreateTag().putInt("TaskMarker", 3);
        helper.assertTrue(!InventoryHelper.matchTargetItem(iron, differentNbt, false, true, true),
                "A same-item NBT mismatch must never fall back to its shared material tag");
        helper.assertTrue(InventoryHelper.matchTargetItem(iron, differentNbt, false, false, true),
                "Disabling NBT matching must preserve same-item matching");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void taskSidePacketPreservesOreBitAndExistingOptions(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            int oldMask = (1 << 0) | (1 << 7) | (1 << 17) | (1 << 18) | (1 << 20);
            int withOre = oldMask | (1 << 19);
            C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_TaskSide, withOre | (1 << 25));
            helper.assertTrue(ship.getStateMinor(ID.M.TaskSide) == withOre,
                    "TaskSide packet must retain bit19 and every existing option, but remove unknown bits");
            C2SGUIInputPacket.applyShipGUIButton(ship, ID.B.ShipInv_TaskSide, oldMask);
            helper.assertTrue(ship.getStateMinor(ID.M.TaskSide) == oldMask,
                    "Disabling ore matching must not modify existing task options");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedTaskSideMasksReloadWithoutMigration(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            BasicEntityShip restored = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
            int faces = (1 << 0) | (1 << 7) | (1 << 17);
            for (int raw : new int[] {0, 0x17FFFF, faces | (1 << 19), 0x1FFFFF | (1 << 25)}) {
                source.setStateMinor(ID.M.TaskSide, raw);
                helper.assertTrue(source.getStateMinor(ID.M.TaskSide) == raw,
                        "The source fixture must retain the legacy TaskSide value");
                CompoundTag saved = new CompoundTag();
                CapaShipSavedValues.saveNBTData(saved, source);
                helper.assertTrue(saved.getIntArray("StateMinor")[ID.M.TaskSide] == raw,
                        "TaskSide must still be stored in the existing StateMinor array");
                restored.setStateMinor(ID.M.TaskSide, ~raw);
                CapaShipSavedValues.loadNBTData(saved, restored);
                helper.assertTrue(restored.getStateMinor(ID.M.TaskSide) == raw,
                        "Loading an old TaskSide mask must not lose faces, options or unknown bits");
                TaskSideMask decoded = new TaskSideMask(restored.getStateMinor(ID.M.TaskSide));
                helper.assertTrue(decoded.checkMetadata() == ((raw & (1 << 18)) != 0)
                                && decoded.checkNbt() == ((raw & (1 << 20)) != 0)
                                && decoded.checkOreTag() == ((raw & (1 << 19)) != 0),
                        "Existing option bits must retain their meaning after loading");
                helper.assertTrue(decoded.unknownBits() == (raw & ~0x1FFFFF),
                        "The remaining unknown bits must survive a saved mask round trip");
                for (int group = 0; group < 3; group++) {
                    for (int face = 0; face < 6; face++) {
                        helper.assertTrue(decoded.face(group, face) == ((raw & (1 << (group * 6 + face))) != 0),
                                "Saved task faces must not shift when ore matching is restored");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void craftingOreMatchingDoesNotConsumeColoredGlass(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            BlockPos waypointPos = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos chestPos = helper.absolutePos(new BlockPos(3, 2, 2));
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                helper.getLevel().setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
                helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                TileEntityWaypoint waypoint = (TileEntityWaypoint) helper.getLevel().getBlockEntity(waypointPos);
                ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
                waypoint.setPairedChest(chestPos);
                chest.setItem(0, new ItemStack(Items.RED_STAINED_GLASS, 6));
                chest.setItem(1, new ItemStack(Items.GLASS, 6));

                BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                ship.moveTo(chestPos.getX() + 0.5D, chestPos.getY(), chestPos.getZ() + 0.5D);
                ship.setStateMinor(ID.M.NumGrudge, 10000);
                ship.setStateMinor(ID.M.TaskSide, 1 << 19);
                ship.setGuardedPos(waypointPos.getX(), waypointPos.getY(), waypointPos.getZ(),
                        helper.getLevel().dimension(), 1);
                ship.setStateFlag(ID.F.CanFollow, false);
                List<ItemStack> inputs = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) {
                    inputs.add(slot < 6 ? new ItemStack(Items.GLASS) : ItemStack.EMPTY);
                }
                ItemStack paper = new ItemStack(ModItems.RECIPE_PAPER.get());
                RecipePaperData.write(paper, inputs, new ItemStack(Items.GLASS_PANE, 16));
                ship.getCapaShipInventory().setStackInSlot(22, paper);
                helper.assertTrue(ship.hasGuardDestination(), "Crafting fixture must have an active guard destination");
                helper.assertTrue(waypoint.hasPairedChest(), "Crafting fixture must have a paired chest");
                helper.assertTrue(ship.getStateMinor(ID.M.TaskSide) == (1 << 19),
                        "Crafting fixture must retain the ore bit");
                helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel),
                        "Crafting fixture must have fuel");

                TaskHelper.onUpdateCrafting(ship);
                int panes = 0;
                int redGlass = 0;
                int clearGlass = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    ItemStack stack = chest.getItem(slot);
                    if (stack.is(Items.GLASS_PANE)) panes += stack.getCount();
                    if (stack.is(Items.RED_STAINED_GLASS)) redGlass += stack.getCount();
                    if (stack.is(Items.GLASS)) clearGlass += stack.getCount();
                }
                helper.assertTrue(panes == 16 && redGlass == 6 && clearGlass == 0,
                        "Ore matching must skip earlier colored glass and craft clear panes from clear glass");
                for (int slot = 12; slot <= 20; slot++) {
                    helper.assertTrue(ship.getCapaShipInventory().getStackInSlot(slot).isEmpty(),
                            "No glass may remain stuck in the crafting grid after a successful craft");
                }
            } finally {
                helper.getLevel().removeBlock(waypointPos, false);
                helper.getLevel().removeBlock(chestPos, false);
            }
            helper.succeed();
        }, new Vec3(3, 2, 2));
    }
}
