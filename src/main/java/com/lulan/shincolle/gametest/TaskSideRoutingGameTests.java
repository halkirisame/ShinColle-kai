package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityWaypoint;
import com.lulan.shincolle.utility.CapaHelper;
import com.lulan.shincolle.utility.InventoryHelper;
import com.lulan.shincolle.utility.TaskHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaskSideRoutingGameTests {

    private TaskSideRoutingGameTests() {
    }

    @GameTest(template = "arena")
    public static void cookingDefaultTaskSidePrefersInputSlotForLogs(GameTestHelper helper) {
        CookingFixture fixture = createFixture(helper);
        try {
            fixture.ship().getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.OAK_LOG));
            fixture.ship().getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.OAK_LOG));

            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(0).is(Items.OAK_LOG),
                    "Default TaskSide must prefer the furnace input slot for a smeltable fuel item");
            helper.assertTrue(fixture.furnace().getItem(1).isEmpty(),
                    "Default TaskSide must not route the cooking input into the fuel slot");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void cookingInputUsesConfiguredFace(GameTestHelper helper) {
        CookingFixture fixture = createFixture(helper);
        try {
            fixture.ship().getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.RAW_IRON));
            fixture.ship().getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON));

            fixture.ship().setStateMinor(ID.M.TaskSide, 1 << 0); // input: down
            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(0).isEmpty(),
                    "Furnace input must not accept items through an unconfigured/invalid face");

            fixture.ship().setStateMinor(ID.M.TaskSide, 1 << 1); // input: up
            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(0).is(Items.RAW_IRON),
                    "Furnace input did not accept an item through the configured upper face");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void cookingFuelAndOutputUseConfiguredFaces(GameTestHelper helper) {
        CookingFixture fixture = createFixture(helper);
        try {
            fixture.ship().getCapaShipInventory().setStackInSlot(22, new ItemStack(Items.RAW_IRON));
            fixture.ship().getCapaShipInventory().setStackInSlot(23, new ItemStack(Items.COAL));
            fixture.ship().getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.COAL));
            fixture.furnace().setItem(2, new ItemStack(Items.IRON_INGOT));

            fixture.ship().setStateMinor(ID.M.TaskSide, (1 << 13) | (1 << 7)); // fuel/output: up
            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(0).is(Items.COAL),
                    "Fuel routed through the upper face did not enter that face's exposed input slot");
            helper.assertTrue(fixture.furnace().getItem(1).isEmpty(),
                    "Fuel routed through the upper face must not bypass into the fuel slot");
            helper.assertTrue(fixture.furnace().getItem(2).is(Items.IRON_INGOT),
                    "Furnace output must not leave through the upper input face");

            fixture.furnace().setItem(0, ItemStack.EMPTY);
            fixture.ship().getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.COAL));
            fixture.ship().setStateMinor(ID.M.TaskSide, (1 << 14) | (1 << 6)); // fuel: north, output: down
            IItemHandler north = CapaHelper.getCapaInventory(fixture.furnace(), 2);
            helper.assertTrue(north != null && north.getSlots() == 1,
                    "Furnace horizontal item capability was unavailable");
            helper.assertTrue(north.insertItem(0, new ItemStack(Items.COAL), true).isEmpty(),
                    "Furnace horizontal capability rejected valid fuel");
            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(1).is(Items.COAL),
                    "Furnace fuel did not enter through the configured horizontal face");
            helper.assertTrue(fixture.furnace().getItem(2).isEmpty(),
                    "Furnace output did not leave through the configured lower face");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void cookingHonorsTaskNbtMatching(GameTestHelper helper) {
        CookingFixture fixture = createFixture(helper);
        try {
            ItemStack template = new ItemStack(Items.RAW_IRON);
            template.getOrCreateTag().putInt("TaskMarker", 1);
            ItemStack candidate = new ItemStack(Items.RAW_IRON);
            candidate.getOrCreateTag().putInt("TaskMarker", 2);
            fixture.ship().getCapaShipInventory().setStackInSlot(22, template);
            fixture.ship().getCapaShipInventory().setStackInSlot(0, candidate);

            fixture.ship().setStateMinor(ID.M.TaskSide, (1 << 1) | (1 << 20));
            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(0).isEmpty(),
                    "NBT matching must reject a same-item ingredient with different tags");

            fixture.ship().setStateMinor(ID.M.TaskSide, 1 << 1);
            TaskHelper.onUpdateCooking(fixture.ship());
            helper.assertTrue(fixture.furnace().getItem(0).is(Items.RAW_IRON),
                    "Disabling NBT matching must allow a same-item ingredient with different tags");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void taskItemMatchingKeepsMetadataAndNbtIndependent(GameTestHelper helper) {
        ItemStack template = new ItemStack(Items.DIAMOND_SWORD);
        template.setDamageValue(1);
        template.getOrCreateTag().putInt("TaskMarker", 1);
        ItemStack differentDamage = template.copy();
        differentDamage.setDamageValue(2);
        ItemStack differentNbt = template.copy();
        differentNbt.getOrCreateTag().putInt("TaskMarker", 2);

        helper.assertTrue(InventoryHelper.matchTargetItem(differentDamage, template, false, true),
                "NBT-only matching must ignore item damage");
        helper.assertTrue(!InventoryHelper.matchTargetItem(differentDamage, template, true, true),
                "Metadata matching must reject different item damage");
        helper.assertTrue(InventoryHelper.matchTargetItem(differentNbt, template, true, false),
                "Metadata-only matching must ignore NBT");
        helper.assertTrue(!InventoryHelper.matchTargetItem(differentNbt, template, true, true),
                "NBT matching must reject different tags");
        helper.assertTrue(InventoryHelper.matchTargetItem(differentNbt, template, false, false),
                "Disabling both options must compare item type only");
        helper.assertTrue(!InventoryHelper.matchTargetItem(ItemStack.EMPTY, ItemStack.EMPTY, false, false),
                "TaskSide matching must not treat two empty stacks as an item match");
        helper.assertTrue(!InventoryHelper.matchTargetItem(ItemStack.EMPTY, template, true, true),
                "TaskSide matching must reject an empty candidate");
        helper.assertTrue(!InventoryHelper.matchTargetItem(template, ItemStack.EMPTY, true, true),
                "TaskSide matching must reject an empty target");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void cookingOutputExtractionFailureDropsWithoutItemLoss(GameTestHelper helper) {
        CookingFixture fixture = createFixture(helper);
        try {
            CapaShipInventory inventory = fixture.ship().getCapaShipInventory();
            ItemStack output = new ItemStack(Items.IRON_INGOT, 2);
            output.getOrCreateTag().putBoolean("TaskSideOutputFallback", true);
            inventory.setStackInSlot(CapaShipInventory.EquipSlots, output.copyWithCount(63));
            for (int slot = CapaShipInventory.EquipSlots + 1; slot < inventory.getSlots(); slot++) {
                inventory.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            }

            DivergentExtractHandler handler = new DivergentExtractHandler(output);
            boolean moved = invokeMoveMatchingOutputToShip(handler, inventory, fixture.ship(), output.copyWithCount(1));

            helper.assertTrue(moved, "A safely dropped extracted output must count as a completed move");
            helper.assertTrue(handler.getStackInSlot(0).isEmpty(),
                    "The inconsistent handler did not perform its real extraction");
            int droppedCount = fixture.level().getEntitiesOfClass(ItemEntity.class,
                            fixture.ship().getBoundingBox().inflate(2.0D),
                            entity -> ItemStack.isSameItemSameTags(entity.getItem(), output))
                    .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(droppedCount == 2,
                    "Extracted output must be dropped intact when cargo rejects the real stack");
        } finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static boolean invokeMoveMatchingOutputToShip(IItemHandler handler, CapaShipInventory inventory,
                                                          BasicEntityShip ship, ItemStack target) {
        try {
            Method method = TaskHelper.class.getDeclaredMethod("moveMatchingOutputToShip",
                    List.class, CapaShipInventory.class, BasicEntityShip.class,
                    ItemStack.class, boolean.class, boolean.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, List.of(handler), inventory, ship, target, true, true);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke cooking output transfer", exception);
        }
    }

    private static CookingFixture createFixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos waypointPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos furnacePos = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlock(waypointPos, ModBlocks.WAYPOINT.get().defaultBlockState(), 3);
        level.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState(), 3);
        BlockEntity waypointEntity = level.getBlockEntity(waypointPos);
        BlockEntity furnaceEntity = level.getBlockEntity(furnacePos);
        if (!(waypointEntity instanceof TileEntityWaypoint waypoint)
                || !(furnaceEntity instanceof AbstractFurnaceBlockEntity furnace)) {
            throw new AssertionError("Failed to create TaskSide cooking fixtures.");
        }
        waypoint.setPairedChest(furnacePos);

        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(level);
        if (ship == null) {
            throw new AssertionError("Failed to create ship for TaskSide test.");
        }
        ship.moveTo(furnacePos.getX() + 0.5D, furnacePos.getY(), furnacePos.getZ() + 0.5D);
        level.addFreshEntity(ship);
        ship.setGuardedPos(waypointPos.getX(), waypointPos.getY(), waypointPos.getZ(),
                level.dimension(), 1);
        ship.setStateFlag(ID.F.CanFollow, false);
        return new CookingFixture(level, waypointPos, furnacePos, ship, furnace);
    }

    private record CookingFixture(ServerLevel level, BlockPos waypointPos, BlockPos furnacePos,
                                  BasicEntityShip ship, AbstractFurnaceBlockEntity furnace) implements AutoCloseable {
        @Override
        public void close() {
            this.ship.discard();
            this.level.removeBlock(this.waypointPos, false);
            this.level.removeBlock(this.furnacePos, false);
        }
    }

    private static final class DivergentExtractHandler implements IItemHandler {
        private ItemStack stored;

        private DivergentExtractHandler(ItemStack stored) {
            this.stored = stored.copy();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? this.stored.copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || this.stored.isEmpty())
                return ItemStack.EMPTY;
            if (simulate)
                return this.stored.copyWithCount(1);
            ItemStack extracted = this.stored;
            this.stored = ItemStack.EMPTY;
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
