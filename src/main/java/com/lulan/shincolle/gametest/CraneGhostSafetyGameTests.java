package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.ContainerCrane;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.BasicTileInventory;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneGhostSafetyGameTests {
    private CraneGhostSafetyGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void breakingCraneNeverDropsGhostFilters(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        TileEntityCrane tile = placeCrane(helper, pos);
        fillFilters(helper, tile);
        AABB area = new AABB(pos).inflate(1);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                "Fixture must start without loose items.");
        helper.assertTrue(helper.getLevel().destroyBlock(pos, false), "Crane must actually be destroyed.");
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == null, "Destroyed crane must be removed.");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                "Destroying a crane without block loot must not drop any filter samples.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneReplacementKeepsOtherInventoryDropsUnchanged(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        TileEntityCrane tile = placeCrane(helper, pos);
        fillFilters(helper, tile);
        helper.getLevel().setBlock(pos, tile.getBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == tile,
                "An unchanged block state must retain the same crane.");
        assertFilters(helper, tile);
        helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        AABB area = new AABB(pos).inflate(1);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                "Replacing a crane must not drop its filter samples either.");
        helper.getLevel().setBlock(pos, ModBlocks.SMALL_SHIPYARD.get().defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof BasicTileInventory,
                "Control block must use the shared inventory removal path.");
        BasicTileInventory shipyard = (BasicTileInventory) helper.getLevel().getBlockEntity(pos);
        shipyard.getInventory().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        helper.assertTrue(shipyard.getInventory().getStackInSlot(0).getCount() == 8,
                "Control inventory must contain real cargo before removal.");
        helper.getLevel().destroyBlock(pos, false);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
        helper.assertTrue(drops.stream().allMatch(item -> item.getItem().is(Items.STONE))
                        && drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 8,
                "Other inventory blocks must still drop all real cargo.");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneFiltersExposeNoItemCapabilityOrComparatorInventory(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        TileEntityCrane tile = placeCrane(helper, pos);
        fillFilters(helper, tile);
        helper.assertTrue(!tile.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                "Unsided item access must not expose filter samples.");
        for (Direction side : Direction.values()) {
            helper.assertTrue(!tile.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Sided item access must not expose filter samples: " + side);
        }
        helper.getLevel().setBlock(pos.below(), Blocks.HOPPER.defaultBlockState(), 3);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(pos.below());
        helper.assertTrue(hopper != null && hopper.isEmpty(), "Fixture must have an empty hopper below the crane.");
        helper.assertTrue(!HopperBlockEntity.suckInItems(helper.getLevel(), hopper) && hopper.isEmpty(),
                "A hopper must not extract ghost filters.");
        helper.assertTrue(!tile.getBlockState().hasAnalogOutputSignal()
                        && tile.getBlockState().getAnalogOutputSignal(helper.getLevel(), pos) == 0,
                "Ghost filters must not count as comparator inventory.");
        assertFilters(helper, tile);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneMenuNeverExtractsSamplesThroughAlternateClicks(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        TileEntityCrane tile = placeCrane(helper, pos);
        UUID owner = UUID.randomUUID();
        CompoundTag identity = tile.saveWithFullMetadata();
        identity.putUUID("OwnerUUID", owner);
        tile.load(identity);
        Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(owner, "crane_ghost"));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 4));
        ContainerCrane menu = new ContainerCrane(10, player.getInventory(), tile);
        helper.assertTrue(menu.stillValid(player), "Owner must be authorized and within menu reach.");
        fillFilters(helper, tile);
        for (int slot = 0; slot < TileEntityCrane.SLOT_COUNT; slot++) {
            menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
            menu.clicked(slot, 0, ClickType.SWAP, player);
            menu.clicked(slot, 40, ClickType.SWAP, player);
            menu.clicked(slot, 0, ClickType.THROW, player);
            player.getAbilities().instabuild = true;
            menu.clicked(slot, 2, ClickType.CLONE, player);
            player.getAbilities().instabuild = false;
            helper.assertTrue(menu.getCarried().isEmpty(), "Alternate clicks must not put samples on the cursor.");
        }
        menu.setCarried(new ItemStack(Items.STONE));
        // Double-click a player slot: vanilla collection must also skip ghost slots indirectly.
        menu.clicked(ContainerCrane.GHOST_SLOT_COUNT, 0, ClickType.PICKUP_ALL, player);
        helper.assertTrue(menu.getCarried().getCount() == 1, "Double-click must not collect matching ghost samples.");
        menu.clicked(-999, 0, ClickType.QUICK_CRAFT, player);
        for (int slot = 0; slot < TileEntityCrane.SLOT_COUNT; slot++) {
            menu.clicked(slot, 1, ClickType.QUICK_CRAFT, player);
        }
        menu.clicked(-999, 2, ClickType.QUICK_CRAFT, player);
        helper.assertTrue(menu.getCarried().getCount() == 1, "Dragging must not spend or extract real items in ghost slots.");
        assertFilters(helper, tile);
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 0
                        && player.getInventory().countItem(Items.DIRT) == 4,
                "Player inventory must retain its original items without any samples.");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(),
                "Throwing from a ghost slot must not produce loose items.");
        menu.setCarried(ItemStack.EMPTY);
        menu.removed(player);
        helper.assertTrue(player.getInventory().countItem(Items.STONE) == 0,
                "Closing the menu must not return ghost samples as real items.");
        helper.succeed();
    }

    private static TileEntityCrane placeCrane(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof TileEntityCrane,
                "Fixture must have a crane block entity.");
        return (TileEntityCrane) helper.getLevel().getBlockEntity(pos);
    }

    private static void fillFilters(GameTestHelper helper, TileEntityCrane tile) {
        for (int slot = 0; slot < TileEntityCrane.SLOT_COUNT; slot++) {
            tile.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE));
        }
        assertFilters(helper, tile);
    }

    private static void assertFilters(GameTestHelper helper, TileEntityCrane tile) {
        for (int slot = 0; slot < TileEntityCrane.SLOT_COUNT; slot++) {
            helper.assertTrue(tile.getInventory().getStackInSlot(slot).is(Items.STONE)
                            && tile.getInventory().getStackInSlot(slot).getCount() == 1,
                    "Every filter must remain a single ghost sample: " + slot);
        }
    }
}
