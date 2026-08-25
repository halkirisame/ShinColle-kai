package com.lulan.shincolle.item;

import com.lulan.shincolle.capability.CapaFluidContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ship Tank - fluid/resource tank for ship entities.
 * Original meta types with capacities:
 * 0 = 32000 mB
 * 1 = 128000 mB
 * 2 = 512000 mB
 * 3 = 2048000 mB
 */
public class ShipTank extends BasicItem {

    private final int type;
    private final int capacity;

    public ShipTank() {
        this(0);
    }

    public ShipTank(int type) {
        super(new Properties().stacksTo(1));
        this.type = type;
        switch (type) {
            case 0:
                this.capacity = 32000;
                break;
            case 1:
                this.capacity = 128000;
                break;
            case 2:
                this.capacity = 512000;
                break;
            case 3:
                this.capacity = 2048000;
                break;
            default:
                this.capacity = 32000;
                break;
        }
    }

    public int getType() {
        return this.type;
    }

    public int getTankCapacity() {
        return this.capacity;
    }

    /**
     * Attach fluid capability to this item, enabling it to store fluids.
     */
    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ShipTankFluidProvider(stack, this.capacity);
    }

    /**
     * Place one source block from the tank through the ordinary item-use path.
     * This keeps fluid consumption and placement authority on the server instead
     * of trusting a client-supplied position packet.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        BlockPos targetPos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.isInWorldBounds(targetPos)
                || !level.mayInteract(context.getPlayer(), targetPos)
                || !level.getBlockState(targetPos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        IFluidHandlerItem tank = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (tank == null) {
            return InteractionResult.PASS;
        }

        FluidStack contained = tank.getFluidInTank(0);
        if (contained.isEmpty() || contained.getAmount() < 1000) {
            return InteractionResult.PASS;
        }

        Fluid fluid = contained.getFluid();
        FluidState sourceFluidState = fluid instanceof FlowingFluid flowingFluid
                ? flowingFluid.getSource(false)
                : fluid.defaultFluidState();
        BlockState sourceState = sourceFluidState.createLegacyBlock();
        if (sourceState.isAir()) {
            return InteractionResult.PASS;
        }

        level.setBlock(targetPos, sourceState, 3);
        tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.shincolle_kai.shiptank").withStyle(ChatFormatting.GRAY));

        // Show fluid contents and capacity
        String name = "";
        int amount = 0;

        var fluidOpt = FluidUtil.getFluidContained(stack);
        if (fluidOpt.isPresent()) {
            FluidStack fs = fluidOpt.get();
            name = fs.getDisplayName().getString();
            amount = fs.getAmount();
        }

        tooltip.add(Component.literal(ChatFormatting.AQUA + name + ChatFormatting.WHITE +
                " " + amount + " / " + capacity + " mB"));
    }

    /**
     * Capability provider that wraps CapaFluidContainer for fluid handling.
     */
    private static class ShipTankFluidProvider implements ICapabilityProvider {

        private final CapaFluidContainer fluidHandler;
        private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.fluids.capability.IFluidHandlerItem> holder;

        public ShipTankFluidProvider(ItemStack stack, int capacity) {
            this.fluidHandler = new CapaFluidContainer(stack, capacity);
            this.holder = net.minecraftforge.common.util.LazyOptional.of(() -> fluidHandler);
        }

        @Override
        public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> cap,
                @Nullable net.minecraft.core.Direction side) {
            if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM) {
                return holder.cast();
            }
            return net.minecraftforge.common.util.LazyOptional.empty();
        }
    }
}
