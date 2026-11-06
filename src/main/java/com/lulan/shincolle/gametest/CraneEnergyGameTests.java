package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.utility.CraneEnergyTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneEnergyGameTests {
    private CraneEnergyGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneInternalEnergyTransferPreservesOverflow(GameTestHelper helper) {
        TileEntityCrane restored = CraneCapacityGameTests.restoreWithSmallerCapacity(helper);
        IEnergyStorage buffer = restored.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(IllegalStateException::new);
        try (EnergyItems items = new EnergyItems()) {
            CraneEnergyTransfer.Result input = CraneEnergyTransfer.move(items.battery(2000), buffer, true, 1000);
            helper.assertTrue(input.moved() == 0 && energy(input.container()) == 2000 && restored.getEnergyAmount() == 3000,
                    "The shared chest/ship transfer must not consume battery FE or buffered overflow on input.");
            CraneEnergyTransfer.Result output = CraneEnergyTransfer.move(items.battery(0), buffer, false, 1000);
            helper.assertTrue(output.moved() == 1000 && energy(output.container()) == 1000 && restored.getEnergyAmount() == 2000,
                    "The shared chest/ship transfer must still be able to extract FE overflow into a battery.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneEnergyItemsChargeDischargeAndCompleteFullMode(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper); EnergyItems items = new EnergyItems()) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 1);
                f.crane().setEnergyMode(1);
                f.crane().setCraneMode(1);
                f.chest().setItem(0, items.battery(2000));
                f.ship().getCapaShipInventory().setStackInSlot(6, items.battery(0));
                f.tick(80);
                helper.assertTrue(energy(f.chest().getItem(0)) == 0
                                && energy(f.ship().getCapaShipInventory().getStackInSlot(6)) == 2000
                                && f.crane().getEnergyAmount() == 0 && f.ship().getStateMinor(ID.M.CraneState) == 0,
                        "Charging must conserve FE and complete when the ship's battery is full.");
                f.working();
                f.crane().setEnergyMode(2);
                f.tick(16);
                helper.assertTrue(f.crane().getEnergyAmount() == 2000 && f.crane().getDockedShip() == f.ship(),
                        "Discharging must buffer the ship's FE after chest pre-transfer and keep waiting for the chest.");
                f.tick(16);
                helper.assertTrue(energy(f.chest().getItem(0)) == 2000
                                && energy(f.ship().getCapaShipInventory().getStackInSlot(6)) == 0
                                && f.crane().getEnergyAmount() == 0 && f.ship().getStateMinor(ID.M.CraneState) == 0,
                        "The next chest pre-transfer must complete the discharge round trip.");
                helper.assertTrue(f.chest().getItem(0).is(Items.STICK) && f.chest().getItem(0).getCount() == 1,
                        "Energy transfer must preserve the original item and count.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneZeroEnergyRateLeavesShipBatteryUnchanged(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper); EnergyItems items = new EnergyItems()) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 0);
                f.crane().setEnergyMode(1);
                f.crane().setCraneMode(0);
                f.chest().setItem(0, items.battery(2000));
                f.ship().getCapaShipInventory().setStackInSlot(6, items.battery(0));
                f.tick(80);
                helper.assertTrue(f.crane().getEnergyAmount() == 2000
                                && energy(f.ship().getCapaShipInventory().getStackInSlot(6)) == 0
                                && f.ship().getStateMinor(ID.M.CraneState) == 0,
                        "Without pumps, chest FE must stay buffered and no-work mode must release the ship.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneEnergyCapabilityPersistsAndHonorsSidesAndLifecycle(GameTestHelper helper) {
        TileEntityCrane tile = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
        var capability = tile.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH);
        helper.assertTrue(capability.isPresent()
                        && !tile.getCapability(ForgeCapabilities.ENERGY, Direction.UP).isPresent()
                        && !tile.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).isPresent(),
                "Energy capability must be exposed horizontally, not above or below.");
        IEnergyStorage energy = capability.orElseThrow(IllegalStateException::new);
        helper.assertTrue(energy.receiveEnergy(12345, true) == 12345 && tile.getEnergyAmount() == 0,
                "Simulated FE input must not change the buffer.");
        energy.receiveEnergy(12345, false);
        TileEntityCrane restored = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
        restored.load(tile.saveWithFullMetadata());
        helper.assertTrue(restored.getEnergyAmount() == 12345 && restored.getEnergyCapacity() == tile.getEnergyCapacity(),
                "Energy must survive save/load without truncation.");
        helper.assertTrue(energy.extractEnergy(2345, false) == 2345 && tile.getEnergyAmount() == 10000,
                "External consumers must be able to extract stored FE.");
        tile.invalidateCaps();
        helper.assertTrue(!capability.isPresent(), "Removing the block must invalidate existing energy handles.");
        tile.reviveCaps();
        helper.assertTrue(tile.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(),
                "Revival must expose a fresh energy handle.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneEnergyEmptyModeChecksShipSupply(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper); EnergyItems items = new EnergyItems()) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 1);
                f.crane().setEnergyMode(2);
                f.crane().setCraneMode(2);
                f.ship().getCapaShipInventory().setStackInSlot(6, items.battery(2000));
                f.tick(80);
                helper.assertTrue(f.crane().getEnergyAmount() == 2000 && f.ship().getStateMinor(ID.M.CraneState) == 0
                                && energy(f.ship().getCapaShipInventory().getStackInSlot(6)) == 0,
                        "Empty mode must release after the ship's battery is drained, without losing buffered FE.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    private static int energy(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored();
    }

    private static final class EnergyItems implements AutoCloseable {
        private final String marker = UUID.randomUUID().toString();
        private final Consumer<AttachCapabilitiesEvent<ItemStack>> listener = this::attach;

        EnergyItems() {
            MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, listener);
        }

        ItemStack battery(int amount) {
            CompoundTag data = new CompoundTag();
            data.putString("FixtureEnergy", marker);
            data.putInt("StoredEnergy", amount);
            CompoundTag saved = new CompoundTag();
            saved.putString("id", "minecraft:stick");
            saved.putByte("Count", (byte) 1);
            saved.put("tag", data);
            ItemStack stack = ItemStack.of(saved);
            if (energy(stack) != amount) throw new IllegalStateException("Energy fixture capability did not initialize");
            return stack;
        }

        private void attach(AttachCapabilitiesEvent<ItemStack> event) {
            ItemStack stack = event.getObject();
            if (!stack.hasTag() || !marker.equals(stack.getTag().getString("FixtureEnergy"))) return;
            IEnergyStorage storage = new IEnergyStorage() {
                @Override
                public int receiveEnergy(int maximum, boolean simulate) {
                    int amount = Math.max(0, Math.min(maximum, 2000 - getEnergyStored()));
                    if (!simulate) stack.getOrCreateTag().putInt("StoredEnergy", getEnergyStored() + amount);
                    return amount;
                }

                @Override
                public int extractEnergy(int maximum, boolean simulate) {
                    int amount = Math.max(0, Math.min(maximum, getEnergyStored()));
                    if (!simulate) stack.getOrCreateTag().putInt("StoredEnergy", getEnergyStored() - amount);
                    return amount;
                }

                @Override
                public int getEnergyStored() { return stack.getOrCreateTag().getInt("StoredEnergy"); }
                @Override
                public int getMaxEnergyStored() { return 2000; }
                @Override
                public boolean canExtract() { return true; }
                @Override
                public boolean canReceive() { return true; }
            };
            LazyOptional<IEnergyStorage> capability = LazyOptional.of(() -> storage);
            event.addCapability(new ResourceLocation(Reference.MOD_ID, "crane_test_battery"), new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> type, @Nullable Direction side) {
                    return type == ForgeCapabilities.ENERGY ? capability.cast() : LazyOptional.empty();
                }
            });
            event.addListener(capability::invalidate);
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }
}
