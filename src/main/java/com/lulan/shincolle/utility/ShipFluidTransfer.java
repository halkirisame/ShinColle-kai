package com.lulan.shincolle.utility;

import com.lulan.shincolle.ai.ShipResourceGate;
import com.lulan.shincolle.ai.domain.task.ShipResourceRules;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.server.ServerDataManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/** Same-call staging transaction for contract-conforming world and item fluid APIs. */
public final class ShipFluidTransfer {
    private ShipFluidTransfer() { }

    public enum Status { COMMITTED, NO_OP, EXTERNAL_CONTRACT_VIOLATION }

    public record Result(Status status, Optional<BlockPos> position, OptionalInt slot, int expected, int actual,
                         String reason, boolean finiteRemoval) {
        public static Result noOp() {
            return new Result(Status.NO_OP, Optional.empty(), OptionalInt.empty(), 0, 0, "", false);
        }
    }

    public record WorldSnapshot(BlockState state, Optional<CompoundTag> blockData) { }
    public record Source(BlockPos position, WorldSnapshot snapshot, FluidStack fluid) { }
    public record Observation(Optional<Source> source, boolean violation, BlockPos position, String reason) {
        public static Observation absent(BlockPos position) {
            return new Observation(Optional.empty(), false, position, "");
        }
    }

    private record Boundary<T>(Optional<T> value, String reason) {
        boolean valid() { return value.isPresent(); }
        T get() { return value.orElseThrow(); }
    }
    private record FluidKey(Fluid fluid, Optional<CompoundTag> tag) {
        static FluidKey of(FluidStack stack) {
            return new FluidKey(stack.getFluid(), Optional.ofNullable(stack.getTag()).map(CompoundTag::copy));
        }
    }
    private record ItemView(CompoundTag stackData, CompoundTag containerData, Optional<IFluidHandlerItem> handler,
                            Map<FluidKey, Long> fluids) { }
    private record SlotPlan(int slot, CompoundTag snapshot, int amount, Map<FluidKey, Long> before) { }
    private record StagedSlot(SlotPlan plan, ItemStack output) { }

    /** Only calls to external capability/block APIs and their serialization enter this exception boundary. */
    private static <T> Boundary<T> external(Supplier<T> operation) {
        try {
            return new Boundary<>(Optional.ofNullable(operation.get()), "external_null");
        } catch (RuntimeException failure) {
            return new Boundary<>(Optional.empty(), failure.getClass().getSimpleName());
        }
    }

    private static Boundary<WorldSnapshot> snapshot(Level level, BlockPos position) {
        return external(() -> {
            BlockEntity blockEntity = level.getBlockEntity(position);
            return new WorldSnapshot(level.getBlockState(position), blockEntity == null ? Optional.empty()
                    : Optional.of(blockEntity.saveWithFullMetadata().copy()));
        });
    }

    public static Observation observe(Level level, BlockPos position) {
        if (!level.hasChunkAt(position)) return Observation.absent(position);
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof IFluidBlock) && !(state.getBlock() instanceof LiquidBlock)) {
            return Observation.absent(position);
        }
        var before = snapshot(level, position);
        if (!before.valid()) return invalidObservation(position, before.reason());
        FluidStack fluid;
        if (state.getBlock() instanceof IFluidBlock block) {
            var drained = external(() -> block.canDrain(level, position)
                    ? block.drain(level, position, FluidAction.SIMULATE) : FluidStack.EMPTY);
            if (!drained.valid()) return invalidObservation(position, drained.reason());
            fluid = drained.get().copy();
        } else {
            if (!state.getFluidState().isSource()) return Observation.absent(position);
            fluid = new FluidStack(state.getFluidState().getType(), 1000);
        }
        var after = snapshot(level, position);
        if (!after.valid() || !before.get().equals(after.get())) {
            return invalidObservation(position, "world_simulate_mutated");
        }
        if (fluid.isEmpty()) return Observation.absent(position);
        return new Observation(Optional.of(new Source(position.immutable(), before.get(), fluid)), false, position, "");
    }

    private static Observation invalidObservation(BlockPos position, String reason) {
        return new Observation(Optional.empty(), true, position, reason);
    }

    public static Optional<Fluid> columnFluid(BlockState state) {
        if (state.getBlock() instanceof IFluidBlock block) return external(block::getFluid).value();
        return state.getBlock() instanceof LiquidBlock ? Optional.of(state.getFluidState().getType()) : Optional.empty();
    }

    public static boolean liquidPermission(BasicEntityShip ship, BlockPos position) {
        var player = ServerDataManager.getPlayerByUID(ship.getPlayerUID());
        return ShipResourceGate.permission(ship).allowed() && player != null && player.getAbilities().mayBuild
                && ship.level().mayInteract(player, position);
    }

    public static Result pump(BasicEntityShip ship) {
        var observation = BlockHelper.findPumpSource(ship);
        if (observation.violation()) {
            return violation(observation.position(), OptionalInt.empty(), 0, 0, observation.reason());
        }
        if (observation.source().isEmpty()) return Result.noOp();
        Source source = observation.source().get();
        if (!liquidPermission(ship, source.position())) return Result.noOp();
        Fluid fluid = source.fluid().getFluid();
        int depth = fluid.isSame(Fluids.WATER) ? ConfigHandler.infLiquid[0]
                : fluid.isSame(Fluids.LAVA) ? ConfigHandler.infLiquid[1] : 0;
        List<BlockPos> columns = new ArrayList<>();
        if (depth > 0) {
            for (int i = 0; i < 3; i++) {
                columns.add(source.position().offset(ship.getRandom().nextInt(3) - 1, 0,
                        ship.getRandom().nextInt(3) - 1));
            }
        }
        boolean infinite = ShipResourceRules.infinite(depth,
                BlockHelper.observePumpColumns(ship.level(), columns, fluid, depth));
        int requested = infinite ? Math.min(1000, source.fluid().getAmount()) : source.fluid().getAmount();
        CapaShipInventory inventory = ship.getCapaShipInventory();
        List<SlotPlan> plans = new ArrayList<>();
        int remaining = requested;
        for (int slot = CapaShipInventory.EquipSlots; slot < inventory.getSlots() && remaining > 0; slot++) {
            if (!inventory.isSlotAvailable(slot)) break;
            ItemStack original = inventory.getStackInSlot(slot);
            if (original.isEmpty() || original.getCount() != 1) continue;
            var originalSnapshot = external(() -> original.save(new CompoundTag()).copy());
            if (!originalSnapshot.valid()) {
                return violation(source.position(), OptionalInt.of(slot), remaining, 0, originalSnapshot.reason());
            }
            var copied = external(original::copy);
            if (!copied.valid()) return violation(source.position(), OptionalInt.of(slot), remaining, 0, copied.reason());
            ItemStack isolated = copied.get();
            var before = inspect(isolated);
            if (!before.valid()) return violation(source.position(), OptionalInt.of(slot), remaining, 0, before.reason());
            if (!originalSnapshot.get().equals(before.get().stackData())) {
                return violation(source.position(), OptionalInt.of(slot), remaining, 0, "copy_snapshot_mismatch");
            }
            if (before.get().handler().isEmpty()) continue;
            var offered = source.fluid().copy();
            offered.setAmount(remaining);
            var accepted = external(() -> before.get().handler().get().fill(offered, FluidAction.SIMULATE));
            if (!accepted.valid()) return violation(source.position(), OptionalInt.of(slot), remaining, 0, accepted.reason());
            int amount = accepted.get();
            var after = inspect(isolated);
            if (amount < 0 || amount > remaining || !after.valid() || !unchanged(before.get(), after.get())) {
                return violation(source.position(), OptionalInt.of(slot), remaining, amount, "item_simulate_contract");
            }
            if (amount > 0) {
                plans.add(new SlotPlan(slot, originalSnapshot.get(), amount, before.get().fluids()));
                remaining -= amount;
            }
        }
        if ((!infinite && remaining > 0) || remaining == requested) return Result.noOp();
        List<StagedSlot> staged = new ArrayList<>();
        for (SlotPlan plan : plans) {
            var copied = external(() -> inventory.getStackInSlot(plan.slot()).copy());
            if (!copied.valid()) return violation(source.position(), OptionalInt.of(plan.slot()), plan.amount(), 0, copied.reason());
            var before = inspect(copied.get());
            if (!before.valid() || !plan.snapshot().equals(before.get().stackData())) return Result.noOp();
            var offered = source.fluid().copy();
            offered.setAmount(plan.amount());
            var handler = before.get().handler().orElseThrow();
            var filled = external(() -> handler.fill(offered, FluidAction.EXECUTE));
            if (!filled.valid()) return violation(source.position(), OptionalInt.of(plan.slot()), plan.amount(), 0, filled.reason());
            var container = external(handler::getContainer);
            if (!container.valid() || container.get().isEmpty() || container.get().getCount() != 1) {
                return violation(source.position(), OptionalInt.of(plan.slot()), plan.amount(), filled.get(), "invalid_container");
            }
            var output = inspect(container.get());
            if (filled.get() != plan.amount() || !output.valid() || output.get().handler().isEmpty()
                    || !increasedOnly(plan.before(), output.get().fluids(), FluidKey.of(source.fluid()), plan.amount())) {
                return violation(source.position(), OptionalInt.of(plan.slot()), plan.amount(), filled.get(), "item_execute_contract");
            }
            staged.add(new StagedSlot(plan, container.get()));
        }
        // Re-read every live slot and the source after every external item callback, before world commit.
        for (SlotPlan plan : plans) {
            if (!inventory.isSlotAvailable(plan.slot()) || inventory.getStackInSlot(plan.slot()).getCount() != 1) return Result.noOp();
            var current = external(() -> inventory.getStackInSlot(plan.slot()).save(new CompoundTag()).copy());
            if (!current.valid() || !plan.snapshot().equals(current.get())) return Result.noOp();
        }
        var current = observe(ship.level(), source.position());
        if (current.violation()) return violation(source.position(), OptionalInt.empty(), requested, 0, current.reason());
        if (current.source().isEmpty() || !source.snapshot().equals(current.source().get().snapshot())
                || !sameFluid(source.fluid(), current.source().get().fluid())
                || !liquidPermission(ship, source.position())) return Result.noOp();
        if (infinite && !ShipResourceRules.infinite(depth,
                BlockHelper.observePumpColumns(ship.level(), columns, fluid, depth))) return Result.noOp();
        if (!infinite) {
            if (source.snapshot().state().getBlock() instanceof IFluidBlock block) {
                var drained = external(() -> block.drain(ship.level(), source.position(), FluidAction.EXECUTE));
                if (!drained.valid() || !sameFluid(source.fluid(), drained.get())) {
                    return violation(source.position(), OptionalInt.empty(), requested,
                            drained.valid() ? drained.get().getAmount() : 0, "world_execute_contract");
                }
            } else {
                var removed = external(() -> ship.level().setBlock(source.position(), Blocks.AIR.defaultBlockState(), 11));
                if (!removed.valid()) return violation(source.position(), OptionalInt.empty(), requested, 0, removed.reason());
                if (!removed.get() || !ship.level().getBlockState(source.position()).isAir()) return Result.noOp();
            }
        }
        for (StagedSlot slot : staged) inventory.setStackInSlot(slot.plan().slot(), slot.output());
        return new Result(Status.COMMITTED, Optional.of(source.position()), OptionalInt.empty(), requested,
                requested - remaining, "", !infinite);
    }

    /** Serializes ForgeCaps too, and observes tanks through the returned container, not the fill return alone. */
    private static Boundary<ItemView> inspect(ItemStack stack) {
        return external(() -> {
            CompoundTag saved = stack.save(new CompoundTag()).copy();
            Optional<IFluidHandlerItem> capability = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
            if (capability.isEmpty()) return new ItemView(saved, saved, capability, Map.of());
            IFluidHandlerItem handler = capability.get();
            ItemStack container = handler.getContainer();
            if (container == null || container.isEmpty() || container.getCount() != 1) return null;
            int tanks = handler.getTanks();
            if (tanks < 0) return null;
            Map<FluidKey, Long> fluids = new HashMap<>();
            for (int tank = 0; tank < tanks; tank++) {
                FluidStack content = handler.getFluidInTank(tank);
                if (content == null || content.getAmount() < 0) return null;
                if (!content.isEmpty()) fluids.merge(FluidKey.of(content), (long) content.getAmount(), Long::sum);
            }
            return new ItemView(saved, container.save(new CompoundTag()).copy(), capability, Map.copyOf(fluids));
        });
    }

    private static boolean unchanged(ItemView before, ItemView after) {
        return before.stackData().equals(after.stackData()) && before.containerData().equals(after.containerData())
                && before.fluids().equals(after.fluids());
    }

    private static boolean increasedOnly(Map<FluidKey, Long> before, Map<FluidKey, Long> after, FluidKey fluid, int amount) {
        Map<FluidKey, Long> expected = new HashMap<>(before);
        expected.merge(fluid, (long) amount, Long::sum);
        return expected.equals(after);
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first.isFluidEqual(second) && first.getAmount() == second.getAmount();
    }

    private static Result violation(BlockPos position, OptionalInt slot, int expected, int actual, String reason) {
        return new Result(Status.EXTERNAL_CONTRACT_VIOLATION, Optional.of(position), slot, expected, actual, reason, false);
    }
}
