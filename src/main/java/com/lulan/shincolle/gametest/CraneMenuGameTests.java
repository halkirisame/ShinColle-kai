package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.ContainerCrane;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneMenuGameTests {
    private CraneMenuGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneMenuSynchronizesFullWidthResourceValuesAndShipIdentity(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 0);
                f.tick(80);
                helper.assertTrue(f.crane().getDockedShip() == f.ship(), "Fixture must have a docked ship.");
                f.crane().getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(IllegalStateException::new)
                        .fill(new FluidStack(Fluids.WATER, 1234567), FluidAction.EXECUTE);
                f.crane().getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(IllegalStateException::new)
                        .receiveEnergy(123456789, false);
                f.crane().setItemMode(17, true);
                var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(f.ship().getOwnerUUID(), "crane_menu"));
                var pos = f.crane().getBlockPos();
                player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                ContainerCrane sender = new ContainerCrane(10, player.getInventory(), f.crane());
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                buffer.writeBlockPos(pos);
                ContainerCrane receiver;
                try {
                    receiver = new ContainerCrane(11, player.getInventory(), buffer);
                } finally {
                    buffer.release();
                }
                sender.addSlotListener(new ContainerListener() {
                    @Override
                    public void slotChanged(AbstractContainerMenu source, int slot, ItemStack stack) { }
                    @Override
                    public void dataChanged(AbstractContainerMenu source, int index, int value) {
                        receiver.setData(index, (short) value);
                    }
                });
                sender.broadcastChanges();
                helper.assertTrue(receiver.getDockedShipId() == f.ship().getId() && receiver.getItemMode(17),
                        "The menu must expose the captured ship ID and all NOT bits.");
                helper.assertTrue(receiver.getFluidAmount() == 1234567 && receiver.getEnergyAmount() == 123456789
                                && receiver.getFluidCapacity() == f.crane().getFluidCapacity()
                                && receiver.getEnergyCapacity() == f.crane().getEnergyCapacity(),
                        "Resource amounts and capacities must survive signed-short synchronization without truncation.");
                f.crane().setActive(false);
                sender.broadcastChanges();
                helper.assertTrue(receiver.getDockedShipId() == -1,
                        "Releasing the ship must clear the synchronized identity, including its sign bits.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }
}
