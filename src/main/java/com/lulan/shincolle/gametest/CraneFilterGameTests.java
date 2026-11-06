package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.ContainerCrane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneFilterGameTests {
    private CraneFilterGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneMetadataComparesDamageIndependentlyOfNbt(GameTestHelper helper) {
        TileEntityCrane crane = crane();
        ItemStack filter = sword(4, "first");
        crane.getInventory().setStackInSlot(0, filter);
        helper.assertTrue(matches(crane, sword(8, "second")), "Both disabled must compare only the item type.");
        crane.setCheckMetadata(true);
        helper.assertTrue(matches(crane, sword(4, "second")), "Metadata alone must ignore other NBT.");
        helper.assertTrue(!matches(crane, sword(8, "first")), "Metadata must reject a different damage value.");
        helper.assertTrue(filter.getDamageValue() == 4 && "first".equals(filter.getTag().getString("Label")),
                "Comparison must not mutate the filter.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneNbtComparisonIgnoresOnlyTopLevelDamage(GameTestHelper helper) {
        TileEntityCrane crane = crane();
        crane.setCheckNbt(true);
        crane.getInventory().setStackInSlot(0, sword(4, "first"));
        helper.assertTrue(matches(crane, sword(8, "first")), "NBT-only must ignore top-level Damage.");
        helper.assertTrue(!matches(crane, sword(4, "second")), "NBT-only must still compare other data.");
        ItemStack filter = new ItemStack(Items.STONE);
        filter.getOrCreateTag().putInt("Damage", 0);
        crane.getInventory().setStackInSlot(0, filter);
        helper.assertTrue(matches(crane, new ItemStack(Items.STONE)), "An empty normalized tag must equal no tag.");
        CompoundTag nested = new CompoundTag();
        nested.putInt("Damage", 3);
        filter.getOrCreateTag().put("Nested", nested);
        ItemStack different = filter.copy();
        different.getOrCreateTag().getCompound("Nested").putInt("Damage", 5);
        helper.assertTrue(!matches(crane, different), "Nested Damage belongs to NBT and must not be removed.");
        helper.assertTrue(filter.getTag().contains("Damage") && filter.getTag().getCompound("Nested").getInt("Damage") == 3,
                "Normalization must leave the source tags intact.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneCombinedFlagsRequireDamageAndNbt(GameTestHelper helper) {
        TileEntityCrane crane = crane();
        crane.setCheckMetadata(true);
        crane.setCheckNbt(true);
        crane.setCheckDict(true);
        crane.getInventory().setStackInSlot(0, sword(4, "first"));
        helper.assertTrue(matches(crane, sword(4, "first")), "Both matching fields must pass.");
        helper.assertTrue(!matches(crane, sword(8, "first")), "A damage mismatch must fail.");
        helper.assertTrue(!matches(crane, sword(4, "second")), "An NBT mismatch must fail.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneDictionaryUsesSharedForgeTagsOnly(GameTestHelper helper) {
        TileEntityCrane crane = crane();
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        helper.assertTrue(iron.getTags().anyMatch(tag -> "forge".equals(tag.location().getNamespace())
                        && gold.is(tag)), "Fixture must share a forge tag across different items.");
        crane.getInventory().setStackInSlot(0, iron);
        helper.assertTrue(!matches(crane, gold), "Dictionary disabled must not accept another item type.");
        crane.setCheckDict(true);
        helper.assertTrue(matches(crane, gold), "Dictionary enabled must accept a shared forge tag.");
        ItemStack oak = new ItemStack(Items.OAK_PLANKS);
        ItemStack spruce = new ItemStack(Items.SPRUCE_PLANKS);
        helper.assertTrue(oak.getTags().anyMatch(spruce::is)
                        && oak.getTags().noneMatch(tag -> "forge".equals(tag.location().getNamespace()) && spruce.is(tag)),
                "Fixture planks must share only non-forge tags.");
        crane.getInventory().setStackInSlot(0, oak);
        helper.assertTrue(!matches(crane, spruce), "A shared minecraft tag must not substitute for a forge tag.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneGhostClicksPersistAndSyncEveryNotBit(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
        TileEntityCrane tile = (TileEntityCrane) helper.getLevel().getBlockEntity(pos);
        UUID owner = UUID.randomUUID();
        CompoundTag identity = tile.saveWithFullMetadata();
        identity.putUUID("OwnerUUID", owner);
        tile.load(identity);
        Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(owner, "crane_filter"));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        ContainerCrane menu = new ContainerCrane(10, player.getInventory(), tile);
        menu.setCarried(new ItemStack(Items.STONE, 8));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        menu.clicked(17, 1, ClickType.PICKUP, player);
        helper.assertTrue(tile.saveWithFullMetadata().getInt("ModeItem") == ((1 << 17) | 1),
                "Right-click must set NOT even for the last filter slot.");
        helper.assertTrue(menu.getCarried().getCount() == 8 && tile.getInventory().getStackInSlot(0).getCount() == 1,
                "Ghost clicks must keep the cursor unchanged and preserve the existing one-item filter count.");
        TileEntityCrane restored = crane();
        restored.load(tile.saveWithFullMetadata());
        helper.assertTrue(restored.saveWithFullMetadata().getInt("ModeItem") == ((1 << 17) | 1),
                "All NOT bits must survive a save/load round trip.");
        int[] synced = new int[13];
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(pos);
        ContainerCrane receiver;
        try {
            receiver = new ContainerCrane(11, player.getInventory(), buffer);
        } finally {
            buffer.release();
        }
        menu.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu source, int slot, ItemStack stack) { }

            @Override
            public void dataChanged(AbstractContainerMenu source, int index, int value) {
                if (index < synced.length) synced[index] = (short) value;
                receiver.setData(index, (short) value);
            }
        });
        menu.broadcastChanges();
        helper.assertTrue(((synced[11] & 0xffff) | ((synced[12] & 3) << 16)) == ((1 << 17) | 1),
                "Menu data must transmit the NOT mask without losing bits above the network short.");
        helper.assertTrue(receiver.getItemMode(0) && receiver.getItemMode(17) && !receiver.getItemMode(16),
                "The receiving menu must reconstruct every NOT bit from the network data.");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(tile.saveWithFullMetadata().getInt("ModeItem") == (1 << 17), "Left-click must select normal mode.");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(17, 1, ClickType.PICKUP, player);
        helper.assertTrue(tile.saveWithFullMetadata().getInt("ModeItem") == 0
                        && tile.getInventory().getStackInSlot(17).isEmpty(), "Empty-cursor clicks must clear the bit and filter.");
        menu.setCarried(new ItemStack(Items.DIRT));
        player.moveTo(pos.getX() + 20D, pos.getY(), pos.getZ());
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(tile.saveWithFullMetadata().getInt("ModeItem") == 0
                        && tile.getInventory().getStackInSlot(0).is(Items.STONE), "Distant requests must not edit filters or NOT.");
        Player other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "crane_not_other"));
        other.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        menu.clicked(0, 1, ClickType.PICKUP, other);
        helper.assertTrue(tile.getItemModeMask() == 0 && tile.getInventory().getStackInSlot(0).is(Items.STONE),
                "A different owner must not edit the NOT mask or filter.");
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        menu.clicked(0, 1, ClickType.QUICK_MOVE, player);
        menu.clicked(0, 2, ClickType.PICKUP, player);
        helper.assertTrue(tile.getItemModeMask() == 0 && tile.getInventory().getStackInSlot(0).is(Items.STONE),
                "Unsupported click types and buttons must not edit ghost filters.");
        CompoundTag legacy = restored.saveWithFullMetadata();
        legacy.remove("ModeItem");
        legacy.putInt("imode", (1 << 17) | (1 << 25));
        restored.load(legacy);
        helper.assertTrue(restored.getItemMode(17) && restored.getItemModeMask() == (1 << 17),
                "Legacy NOT data must load without accepting bits outside the filter slots.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneNotFiltersBlockActualLoadingAndUnloading(GameTestHelper helper) {
        Vec3 position = new Vec3(3.5D, 3D, 3.5D);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                BlockPos pos = helper.absolutePos(new BlockPos(3, 5, 3));
                helper.getLevel().setBlock(pos, ModBlocks.CRANE.get().defaultBlockState(), 3);
                helper.getLevel().setBlock(pos.east(2), Blocks.CHEST.defaultBlockState(), 3);
                TileEntityCrane tile = (TileEntityCrane) helper.getLevel().getBlockEntity(pos);
                ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos.east(2));
                UUID owner = UUID.randomUUID();
                CompoundTag tag = tile.saveWithFullMetadata();
                tag.putUUID("OwnerUUID", owner);
                tag.putInt("ModeItem", (1 << 0) | (1 << 9));
                tile.load(tag);
                tile.getInventory().setStackInSlot(0, new ItemStack(Items.STONE));
                tile.getInventory().setStackInSlot(9, new ItemStack(Items.STONE));
                tile.setPairedChest(pos.east(2));
                tile.setActive(true);
                tile.setCraneMode(24);
                BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
                helper.assertTrue(ship != null, "Fixture ship must exist.");
                ship.setOwnerUUID(owner);
                ship.setStateMinor(ID.M.NumGrudge, 100);
                ship.setStateFlag(ID.F.NoFuel, false);
                ship.setInvulnerable(true);
                Vec3 absolute = helper.absoluteVec(position);
                ship.moveTo(absolute.x, absolute.y, absolute.z);
                ship.setGuardedPos(pos.getX(), pos.getY(), pos.getZ(), helper.getLevel().dimension(), 1);
                ship.setStateFlag(ID.F.CanFollow, false);
                ship.setStateTimer(ID.T.CrandDelay, 0);
                ship.setStateMinor(ID.M.CraneState, 2);
                helper.getLevel().addFreshEntity(ship);
                helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 2 && ship.getStateMinor(ID.M.NumGrudge) > 0
                                && !ship.getStateFlag(ID.F.NoFuel) && ship.hasGuardDestination(), "Fixture must retain crane and fuel state.");
                chest.setItem(0, new ItemStack(Items.STONE, 3));
                chest.setItem(1, new ItemStack(Items.DIRT, 3));
                ship.getCapaShipInventory().setStackInSlot(6, new ItemStack(Items.STONE, 3));
                ship.getCapaShipInventory().setStackInSlot(7, new ItemStack(Items.GRAVEL, 3));
                for (int tick = 0; tick < 80; tick++) TileEntityCrane.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
                helper.assertTrue(tile.getDockedShip() == ship, "Fixture must be captured before transfer assertions.");
                helper.assertTrue(chest.getItem(0).getCount() == 3 && ship.getCapaShipInventory().getStackInSlot(6).getCount() == 3,
                        "NOT must prevent stone transfer in both directions.");
                helper.assertTrue(chest.getItem(1).getCount() == 2 && ship.getCapaShipInventory().getStackInSlot(7).getCount() == 2,
                        "NOT-only filters must allow unmatched cargo in both directions.");
                tile.getInventory().setStackInSlot(1, new ItemStack(Items.STONE));
                helper.assertTrue(!matches(tile, new ItemStack(Items.STONE)),
                        "A positive filter must not override the matching NOT filter.");
                helper.succeed();
            }
        }, position);
    }

    private static TileEntityCrane crane() {
        return new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
    }

    private static ItemStack sword(int damage, String label) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.setDamageValue(damage);
        stack.getOrCreateTag().putString("Label", label);
        return stack;
    }

    private static boolean matches(TileEntityCrane tile, ItemStack item) {
        try {
            Method method = TileEntityCrane.class.getDeclaredMethod("matchesFilter", ItemStack.class, boolean.class);
            method.setAccessible(true);
            return (boolean) method.invoke(tile, item, true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect crane filter", error);
        }
    }
}
