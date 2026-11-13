package com.lulan.shincolle.tileentity;

import com.lulan.shincolle.client.gui.inventory.ContainerCrane;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.ai.ShipCraneStateAdapter;
import com.lulan.shincolle.ai.domain.crane.CraneLifecycle;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlockEntities;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.CraneItemFilter;
import com.lulan.shincolle.utility.CraneFluidTransfer;
import com.lulan.shincolle.utility.CraneEnergyTransfer;
import com.lulan.shincolle.utility.CraneTransferRate;
import com.lulan.shincolle.utility.EnchantHelper;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.EquipDrum;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import javax.annotation.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Block entity for the Crane block.
 * Handles automated item loading/unloading between chest and ship entities.
 * <p>
 * Slot layout (18 slots):
 * 0-8: Loading filter slots
 * 9-17: Unloading filter slots
 * <p>
 * Crane modes determine wait behavior:
 * 0: No wait, 1: Until full, 2: Until empty, 3: Excess, 4: Remain
 * 5-24: Timed waits (16-60000 ticks)
 */
public class TileEntityCrane extends BasicTileInventory implements MenuProvider, ITileWaypoint {

    public static final int SLOT_COUNT = 18;
    public static final String[] MODE_NAMES = {
            "No Wait", "Until Full", "Until Empty", "Excess", "Remain",
            "16 ticks", "32 ticks", "48 ticks", "64 ticks", "80 ticks",
            "5 sec", "10 sec", "15 sec", "20 sec", "25 sec",
            "1 min", "2 min", "3 min", "4 min", "5 min",
            "10 min", "20 min", "30 min", "40 min", "50 min"
    };
    /**
     * Tick counter
     */
    private int tick = 0;
    /**
     * Crane operating mode (wait condition)
     */
    private int craneMode = 0;
    /**
     * Whether currently active
     */
    private boolean isActive = false;
    /**
     * Whether paired with a chest
     */
    private boolean isPaired = false;
    /**
     * Enable loading (chest -> ship)
     */
    private boolean enabLoad = true;
    /**
     * Enable unloading (ship -> chest)
     */
    private boolean enabUnload = true;
    /**
     * Check item metadata when filtering
     */
    private boolean checkMetadata = false;
    /**
     * Check NBT data when filtering
     */
    private boolean checkNbt = false;
    /**
     * Check ore dictionary / item tags when filtering
     */
    private boolean checkDict = false;
    /** One exclusion bit per ghost filter slot. */
    private int modeItem;
    /**
     * Respond to redstone signal mode (0:none, 1:continuous, 2:pulse).
     */
    private int redSignalMode = 0;
    private int redSignalTicks;

    public int getRedstoneOutput() {
        return redSignalMode > 0 && redSignalTicks > 0 ? 15 : 0;
    }

    private void setRedSignalTicks(int remaining) {
        int before = getRedstoneOutput();
        redSignalTicks = remaining;
        if (before != getRedstoneOutput() && level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }
    /**
     * Liquid transfer mode (0:none, 1:to ship, 2:to crane).
     */
    private int liquidMode = 0;
    /**
     * Energy transfer mode (0:none, 1:to ship, 2:to crane).
     */
    private int energyMode = 0;
    /**
     * Paired chest position
     */
    private BlockPos chestPos = BlockPos.ZERO;
    /**
     * Next waypoint position
     */
    private BlockPos nextPos = BlockPos.ZERO;
    /**
     * Last waypoint position
     */
    private BlockPos lastPos = BlockPos.ZERO;
    private boolean nextWaypointSet;
    private boolean lastWaypointSet;
    /**
     * Currently docked ship
     */
    private BasicEntityShip dockedShip = null;
    /** Server-local visual cooldown, never saved or used for crane decisions. */
    private long nextCraneVisualTick;
    /**
     * Owner player UID
     */
    private int playerUID = 0;
    /** Stable owner identity; absent only on legacy saves. */
    private UUID ownerUUID;
    private final FluidTank fluidTank = new FluidTank(Math.max(1, ConfigHandler.tileCrane[0])) {
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (getFluidAmount() >= getCapacity()) return 0;
            return super.fill(resource, action);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidTank);
    private final EnergyStorage energyBuffer = new EnergyStorage(Math.max(1, ConfigHandler.tileCrane[1])) {
        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            if (getEnergyStored() >= getMaxEnergyStored()) return 0;
            int received = super.receiveEnergy(maximum, simulate);
            if (!simulate && received > 0) setChanged();
            return received;
        }

        @Override
        public int extractEnergy(int maximum, boolean simulate) {
            int extracted = super.extractEnergy(maximum, simulate);
            if (!simulate && extracted > 0) setChanged();
            return extracted;
        }
    };
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyBuffer);

    public TileEntityCrane(BlockPos pos, BlockState state) {
        this(ModBlockEntities.CRANE.get(), pos, state);
    }

    public TileEntityCrane(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, SLOT_COUNT);
    }

    private static int normalizeTriStateMode(int mode) {
        // [PORT] 1.10.2 -> 1.20.1: Keep legacy crane tri-state semantics (0/1/2; >2
        // wraps to 0).
        if (mode < 0 || mode > 2) {
            return 0;
        }
        return mode;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntityCrane tile) {
        tile.tickServer();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileEntityCrane tile) {
        com.lulan.shincolle.client.particle.BlockWaypointVisuals.tick(level, pos, tile);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // ==================== Getters/Setters ====================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.shincolle_kai.crane");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerCrane(containerId, playerInv, this);
    }

    public int getCraneMode() {
        return craneMode;
    }

    public void setCraneMode(int mode) {
        this.craneMode = Math.max(0, Math.min(mode, MODE_NAMES.length - 1));
        setChanged();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) releaseDockedShip(false);
        setChanged();
    }

    public boolean isPaired() {
        return isPaired;
    }

    public boolean isEnabLoad() {
        return enabLoad;
    }

    public void setEnabLoad(boolean v) {
        this.enabLoad = v;
        setChanged();
    }

    public boolean isEnabUnload() {
        return enabUnload;
    }

    public void setEnabUnload(boolean v) {
        this.enabUnload = v;
        setChanged();
    }

    public boolean isCheckMetadata() {
        return checkMetadata;
    }

    public void setCheckMetadata(boolean v) {
        this.checkMetadata = v;
        setChanged();
    }

    public boolean isCheckNbt() {
        return checkNbt;
    }

    public void setCheckNbt(boolean v) {
        this.checkNbt = v;
        setChanged();
    }

    public boolean isCheckDict() {
        return checkDict;
    }

    public void setCheckDict(boolean v) {
        this.checkDict = v;
        setChanged();
    }

    public int getItemModeMask() {
        return modeItem;
    }

    public boolean getItemMode(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && (modeItem & (1 << slot)) != 0;
    }

    public void setItemMode(int slot, boolean excluded) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        modeItem = excluded ? modeItem | (1 << slot) : modeItem & ~(1 << slot);
        setChanged();
    }

    public boolean isRedSignal() {
        return redSignalMode > 0;
    }

    public void setRedSignal(boolean v) {
        setRedSignalMode(v ? 1 : 0);
    }

    public int getRedSignalMode() {
        return redSignalMode;
    }

    public void setRedSignalMode(int mode) {
        int before = getRedstoneOutput();
        this.redSignalMode = normalizeTriStateMode(mode);
        if (before != getRedstoneOutput() && level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        setChanged();
    }

    public boolean isLiquidMode() {
        return liquidMode > 0;
    }

    public int getLiquidMode() {
        return liquidMode;
    }

    public void setLiquidMode(boolean v) {
        setLiquidMode(v ? 1 : 0);
    }

    public void setLiquidMode(int mode) {
        this.liquidMode = normalizeTriStateMode(mode);
        setChanged();
    }

    public boolean isEnergyMode() {
        return energyMode > 0;
    }

    public int getEnergyMode() {
        return energyMode;
    }

    public void setEnergyMode(boolean v) {
        setEnergyMode(v ? 1 : 0);
    }

    public void setEnergyMode(int mode) {
        this.energyMode = normalizeTriStateMode(mode);
        setChanged();
    }

    public BlockPos getChestPos() {
        return chestPos;
    }

    public void setChestPos(BlockPos pos) {
        if (pos.equals(BlockPos.ZERO) || !chestPos.equals(pos)) releaseDockedShip(false);
        this.chestPos = pos;
        this.isPaired = !pos.equals(BlockPos.ZERO);
        setChanged();
    }

    public BlockPos getNextPos() {
        return nextPos;
    }

    public void setNextPos(BlockPos pos) {
        this.nextPos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.nextWaypointSet = pos != null;
        setChanged();
    }

    public BlockPos getLastPos() {
        return lastPos;
    }

    public void setLastPos(BlockPos pos) {
        this.lastPos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.lastWaypointSet = pos != null;
        setChanged();
    }

    @Override
    public BlockPos getNextWaypoint() {
        return nextPos;
    }

    @Override
    public boolean hasNextWaypoint() {
        return nextWaypointSet;
    }

    @Override
    public void setNextWaypoint(BlockPos pos) {
        setNextPos(pos);
    }

    @Override
    public BlockPos getLastWaypoint() {
        return lastPos;
    }

    @Override
    public boolean hasLastWaypoint() {
        return lastWaypointSet;
    }

    @Override
    public void setLastWaypoint(BlockPos pos) {
        setLastPos(pos);
    }

    @Override
    public int getWpStayTime() {
        return 0;
    }

    @Override
    public void setWpStayTime(int time) {
        // Crane work conditions, rather than waypoint stay time, control departure.
    }

    @Override
    public BlockPos getPairedChest() {
        return chestPos;
    }

    @Override
    public boolean hasPairedChest() {
        return isPaired;
    }

    @Override
    public void setPairedChest(BlockPos pos) {
        if (pos == null || !chestPos.equals(pos)) releaseDockedShip(false);
        this.chestPos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.isPaired = pos != null;
        setChanged();
    }

    @Override
    public Entity getHostEntity() {
        return null;
    }

    public int getPlayerUID() {
        return playerUID;
    }

    public void setPlayerUID(int uid) {
        this.playerUID = uid;
        setChanged();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /**
     * Claims an unowned/legacy crane or verifies the existing owner.
     */
    public boolean claimOrVerifyOwner(Player player) {
        int uid = player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .map(CapaTeitoku::getPlayerUID).orElse(-1);
        if (uid <= 0) {
            LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                    + " result=rejected:uid_uninitialized");
            return false;
        }
        if (ownerUUID != null) {
            boolean verified = ownerUUID.equals(player.getUUID());
            LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                    + " result=" + (verified ? "verified" : "rejected:owner_mismatch"));
            return verified;
        }
        if (playerUID > 0 && playerUID != uid) {
            LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                    + " result=rejected:uid_mismatch");
            return false;
        }
        playerUID = uid;
        ownerUUID = player.getUUID();
        setChanged();
        LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                + " result=claimed");
        return true;
    }

    public boolean canUse(Player player) {
        if (player.hasPermissions(2) || player.getAbilities().instabuild) {
            LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                    + " result=verified:admin");
            return true;
        }
        if (ownerUUID != null) {
            boolean verified = ownerUUID.equals(player.getUUID());
            LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                    + " result=" + (verified ? "verified" : "rejected:owner_mismatch"));
            return verified;
        }
        int uid = player.getCapability(CapaTeitokuProvider.CAPABILITY)
                .map(CapaTeitoku::getPlayerUID).orElse(-1);
        boolean verified = playerUID > 0 && uid == playerUID;
        String reason = playerUID <= 0 ? "uid_uninitialized" : "uid_mismatch";
        LogHelper.diag("DIAG: crane owner check player=" + player.getName().getString()
                + " result=" + (verified ? "verified" : "rejected:" + reason));
        return verified;
    }

    private boolean isOwnerShip(BasicEntityShip ship) {
        if (ownerUUID != null) {
            return ownerUUID.equals(ship.getOwnerUUID());
        }
        return playerUID > 0 && ship.getPlayerUID() == playerUID;
    }

    public String getModeName() {
        if (craneMode >= 0 && craneMode < MODE_NAMES.length) {
            return MODE_NAMES[craneMode];
        }
        return "Unknown";
    }

    // ==================== Crane Logic ====================

    public BasicEntityShip getDockedShip() {
        return dockedShip;
    }

    /**
     * Check if paired chest still exists
     */
    private boolean checkPairedChest() {
        if (!isPaired || level == null || !level.hasChunkAt(chestPos))
            return false;
        BlockEntity be = level.getBlockEntity(chestPos);
        return be instanceof Container;
    }

    /**
     * The capture volume shared by candidate lookup and ship entry observations.
     */
    public AABB getCaptureArea() {
        return new AABB(worldPosition.getX() - 7D, worldPosition.getY() - 8D, worldPosition.getZ() - 7D,
                worldPosition.getX() + 7D, worldPosition.getY(), worldPosition.getZ() + 7D);
    }

    /**
     * Find ship at crane position
     */
    private void checkCraningShip() {
        if (level == null)
            return;

        List<BasicEntityShip> ships = level.getEntitiesOfClass(BasicEntityShip.class, getCaptureArea(),
                com.lulan.shincolle.utility.ShipInventoryAccess::isAvailable);
        UUID selected = CraneLifecycle.choose(ships.stream().map(ship -> new CraneLifecycle.Candidate(
                ship.getUUID(), ShipCraneStateAdapter.phase(ship), ship.isAlive(), isOwnerShip(ship),
                ShipCraneStateAdapter.guards(ship, this))).toList()).orElse(null);
        BasicEntityShip next = selected == null ? null
                : ships.stream().filter(ship -> selected.equals(ship.getUUID())).findFirst().orElse(null);
        if (dockedShip != next) releaseDockedShip(false);
        if (next != null) {
            CraneLifecycle.Phase before = ShipCraneStateAdapter.phase(next);
            if (ShipCraneStateAdapter.apply(next, CraneLifecycle.Phase.WORKING)) {
                if (before != CraneLifecycle.Phase.WORKING) next.setStateTimer(ID.T.CraneTime, 0);
                dockedShip = next;
            }
        }
    }

    private void releaseDockedShip(boolean completed) {
        BasicEntityShip ship = dockedShip;
        if (ship == null) return;
        dockedShip = null;
        ShipCraneStateAdapter.reset(ship);
        if (completed && ship.isAlive()) {
            if (EntityHelper.departCrane(ship, this)) ship.setStateMinor(ID.M.FollowMin, 2);
            ship.playSound(ModSounds.SHIP_BELL.get(), (float) ConfigHandler.volumeShip() * 1.5F,
                    ship.getRandom().nextFloat() * 0.3F + 1F);
        }
        setChanged();
    }

    @Override
    public void setRemoved() {
        releaseDockedShip(false);
        super.setRemoved();
    }

    /**
     * Transfer items between chest and ship
     */
    private boolean applyItemTransfer(boolean loading) {
        if (dockedShip == null || level == null || !isPaired || !level.hasChunkAt(chestPos))
            return false;

        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof Container chest))
            return false;

        if (loading && enabLoad) {
            // Move from chest to ship
            return transferItems(chest, dockedShip);
        } else if (!loading && enabUnload) {
            // Move from ship to chest
            return transferItemsFromShip(dockedShip, chest);
        }
        return false;
    }

    /**
     * Transfer one item stack from chest container to ship
     */
    private boolean transferItems(Container source, BasicEntityShip target) {
        var shipInv = target.getCapaShipInventory();

        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack.isEmpty())
                continue;

            // Check loading filter
            if (!matchesFilter(stack, true))
                continue;
            if (!quantityAllowsTransfer(stack, true, source, shipInv)) continue;

            // Try to add one item to ship inventory
            ItemStack toAdd = stack.copy();
            toAdd.setCount(1);
            if (shipInv.addItemStackToInventory(toAdd)) {
                source.removeItem(i, 1);
                source.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * Transfer one item from ship to chest
     */
    private boolean transferItemsFromShip(BasicEntityShip source, Container target) {
        var shipInv = source.getCapaShipInventory();

        for (int i = CapaShipInventory.EquipSlots; i < shipInv.getSlots(); i++) {
            if (!shipInv.isSlotAvailable(i)) continue;
            ItemStack stack = shipInv.getStackInSlot(i);
            if (stack.isEmpty())
                continue;

            // Check unloading filter
            if (!matchesFilter(stack, false))
                continue;
            if (!quantityAllowsTransfer(stack, false, target, shipInv)) continue;

            // Find a slot in the chest that can accept this item
            ItemStack toMove = stack.copy();
            toMove.setCount(1);

            // Try merging with existing stacks first
            for (int j = 0; j < target.getContainerSize(); j++) {
                ItemStack chestStack = target.getItem(j);
                if (!chestStack.isEmpty() && ItemStack.isSameItemSameTags(chestStack, toMove)
                        && chestStack.getCount() < chestStack.getMaxStackSize()) {
                    chestStack.grow(1);
                    target.setItem(j, chestStack);
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        shipInv.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    target.setChanged();
                    return true;
                }
            }

            // Try empty slots
            for (int j = 0; j < target.getContainerSize(); j++) {
                if (target.getItem(j).isEmpty()) {
                    target.setItem(j, toMove);
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        shipInv.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    target.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if item matches the filter slots (loading: 0-8, unloading: 9-17)
     */
    private boolean matchesFilter(ItemStack stack, boolean loading) {
        int start = loading ? 0 : 9;
        List<ItemStack> filters = new ArrayList<>();
        for (int slot = start; slot < start + 9; slot++) filters.add(inventory.getStackInSlot(slot));
        return CraneItemFilter.allows(stack, filters, modeItem >>> start, checkMetadata, checkNbt, checkDict);
    }

    private boolean matchesFilterEntry(ItemStack stack, ItemStack filter) {
        return CraneItemFilter.matches(stack, filter, checkMetadata, checkNbt, checkDict);
    }

    private boolean quantityAllowsTransfer(ItemStack stack, boolean loading, Container chest, CapaShipInventory cargo) {
        if (craneMode != 3 && craneMode != 4) return true;
        boolean countCargo = craneMode == 3 ? loading : !loading;
        int start = loading ? 0 : 9;
        for (int slot = start; slot < start + 9; slot++) {
            ItemStack filter = inventory.getStackInSlot(slot);
            if (filter.isEmpty() || getItemMode(slot) || !matchesFilterEntry(stack, filter)) continue;
            int amount = 0;
            if (countCargo) {
                for (int i = CapaShipInventory.EquipSlots; i < cargo.getSlots(); i++) {
                    ItemStack item = cargo.getStackInSlot(i);
                    if (cargo.isSlotAvailable(i) && matchesFilterEntry(item, filter)) amount += item.getCount();
                }
            } else {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack item = chest.getItem(i);
                    if (matchesFilterEntry(item, filter)) amount += item.getCount();
                }
            }
            // Transfers are one item, so checking the boundary before moving also caps the resulting quantity.
            if (craneMode == 3 ? amount >= filter.getCount() : amount <= filter.getCount()) return false;
        }
        return true;
    }

    private boolean amountSatisfied(List<ItemStack> items, boolean loading, boolean excess) {
        int start = loading ? 0 : 9;
        for (int slot = start; slot < start + 9; slot++) {
            ItemStack filter = inventory.getStackInSlot(slot);
            if (filter.isEmpty() || getItemMode(slot)) continue;
            int amount = 0;
            for (ItemStack item : items) {
                if (!item.isEmpty() && matchesFilterEntry(item, filter)) amount += item.getCount();
            }
            if (excess ? amount < filter.getCount() : amount > filter.getCount()) return false;
        }
        return true;
    }

    private CraneLifecycle.InventoryCompletion itemCompletion() {
        List<ItemStack> cargo = new ArrayList<>();
        CapaShipInventory shipInventory = dockedShip.getCapaShipInventory();
        for (int slot = CapaShipInventory.EquipSlots; slot < shipInventory.getSlots(); slot++) {
            if (shipInventory.isSlotAvailable(slot)) cargo.add(shipInventory.getStackInSlot(slot));
        }
        Container chest = (Container) level.getBlockEntity(chestPos);
        List<ItemStack> chestItems = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chestItems.add(chest.getItem(slot));
        boolean full = (!enabLoad || cargo.stream().noneMatch(ItemStack::isEmpty))
                && (!enabUnload || chestItems.stream().noneMatch(ItemStack::isEmpty));
        boolean empty = (!enabLoad || chestItems.stream().noneMatch(item -> !item.isEmpty() && matchesFilter(item, true)))
                && (!enabUnload || cargo.stream().noneMatch(item -> !item.isEmpty() && matchesFilter(item, false)));
        boolean excess = (!enabLoad || amountSatisfied(cargo, true, true))
                && (!enabUnload || amountSatisfied(chestItems, false, true));
        boolean remain = (!enabLoad || amountSatisfied(chestItems, true, false))
                && (!enabUnload || amountSatisfied(cargo, false, false));
        return new CraneLifecycle.InventoryCompletion(full && fluidComplete(true) && energyComplete(true),
                empty && fluidComplete(false) && energyComplete(false), excess, remain);
    }

    private CraneLifecycle.WaitMode waitMode() {
        return switch (craneMode) {
            case 0 -> CraneLifecycle.WaitMode.NO_WORK;
            case 1 -> CraneLifecycle.WaitMode.FULL;
            case 2 -> CraneLifecycle.WaitMode.EMPTY;
            case 3 -> CraneLifecycle.WaitMode.EXCESS;
            case 4 -> CraneLifecycle.WaitMode.REMAIN;
            default -> CraneLifecycle.WaitMode.TIMED;
        };
    }

    private int pumpRate(int[] rates) {
        int pumps = dockedShip.getShipType() == ID.ShipType.TRANSPORT && dockedShip.getStateFlag(ID.F.IsMarried) ? 1 : 0;
        int enchantments = 0;
        CapaShipInventory cargo = dockedShip.getCapaShipInventory();
        for (int slot = 0; slot < CapaShipInventory.EquipSlots; slot++) {
            ItemStack stack = cargo.getStackInSlot(slot);
            if (stack.getItem() instanceof EquipDrum && BasicEquip.getEquipMeta(stack) == 1) {
                pumps++;
                enchantments += EnchantHelper.calcEnchantNumber(stack);
            }
        }
        return CraneTransferRate.perCycle(dockedShip.getLevel(), pumps, enchantments, rates[0], rates[1]);
    }

    private void preFluidTransfer() {
        if (liquidMode == 0) return;
        Container chest = (Container) level.getBlockEntity(chestPos);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            CraneFluidTransfer.Result result = CraneFluidTransfer.move(chest.getItem(slot), fluidTank,
                    liquidMode == 1, liquidMode == 1 ? 64000 : 16000);
            if (result.moved() > 0) {
                chest.setItem(slot, result.container());
                chest.setChanged();
                return;
            }
        }
    }

    private boolean transferShipFluid() {
        if (liquidMode == 0) return false;
        int remaining = pumpRate(ConfigHandler.drumLiquid);
        boolean moved = false;
        CapaShipInventory cargo = dockedShip.getCapaShipInventory();
        for (int slot = CapaShipInventory.EquipSlots; slot < cargo.getSlots() && remaining > 0; slot++) {
            if (!cargo.isSlotAvailable(slot)) continue;
            CraneFluidTransfer.Result result = CraneFluidTransfer.move(cargo.getStackInSlot(slot), fluidTank, liquidMode == 2, remaining);
            if (result.moved() > 0) {
                cargo.setStackInSlot(slot, result.container());
                remaining -= result.moved();
                moved = true;
            }
        }
        return moved;
    }

    private boolean fluidComplete(boolean full) {
        if (liquidMode == 0) return true;
        boolean shipSide = full ? liquidMode == 1 : liquidMode == 2;
        if (shipSide) {
            CapaShipInventory cargo = dockedShip.getCapaShipInventory();
            for (int slot = CapaShipInventory.EquipSlots; slot < cargo.getSlots(); slot++) {
                if (cargo.isSlotAvailable(slot) && !CraneFluidTransfer.complete(cargo.getStackInSlot(slot), fluidTank.getFluid(), full)) {
                    return false;
                }
            }
        } else {
            Container chest = (Container) level.getBlockEntity(chestPos);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (!CraneFluidTransfer.complete(chest.getItem(slot), fluidTank.getFluid(), full)) return false;
            }
        }
        return true;
    }

    private void preEnergyTransfer() {
        if (energyMode == 0) return;
        Container chest = (Container) level.getBlockEntity(chestPos);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            CraneEnergyTransfer.Result result = CraneEnergyTransfer.move(chest.getItem(slot), energyBuffer,
                    energyMode == 1, energyMode == 1 ? 64000 : 16000);
            if (result.moved() > 0) {
                chest.setItem(slot, result.container());
                chest.setChanged();
                return;
            }
        }
    }

    private boolean transferShipEnergy() {
        if (energyMode == 0) return false;
        int remaining = pumpRate(ConfigHandler.drumEnergy);
        boolean moved = false;
        CapaShipInventory cargo = dockedShip.getCapaShipInventory();
        for (int slot = CapaShipInventory.EquipSlots; slot < cargo.getSlots() && remaining > 0; slot++) {
            if (!cargo.isSlotAvailable(slot)) continue;
            CraneEnergyTransfer.Result result = CraneEnergyTransfer.move(cargo.getStackInSlot(slot), energyBuffer, energyMode == 2, remaining);
            if (result.moved() > 0) {
                cargo.setStackInSlot(slot, result.container());
                remaining -= result.moved();
                moved = true;
            }
        }
        return moved;
    }

    private boolean energyComplete(boolean full) {
        if (energyMode == 0) return true;
        boolean shipSide = full ? energyMode == 1 : energyMode == 2;
        if (shipSide) {
            CapaShipInventory cargo = dockedShip.getCapaShipInventory();
            for (int slot = CapaShipInventory.EquipSlots; slot < cargo.getSlots(); slot++) {
                if (cargo.isSlotAvailable(slot) && !CraneEnergyTransfer.complete(cargo.getStackInSlot(slot), full)) return false;
            }
        } else {
            Container chest = (Container) level.getBlockEntity(chestPos);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (!CraneEnergyTransfer.complete(chest.getItem(slot), full)) return false;
            }
        }
        return true;
    }

    public int getEnergyAmount() {
        return energyBuffer.getEnergyStored();
    }

    public int getEnergyCapacity() {
        return energyBuffer.getMaxEnergyStored();
    }

    public int getFluidAmount() {
        return fluidTank.getFluidAmount();
    }

    public int getFluidCapacity() {
        return fluidTank.getCapacity();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER && (side == null || side.getAxis().isHorizontal())) {
            return fluidCapability.cast();
        }
        if (capability == ForgeCapabilities.ENERGY && (side == null || side.getAxis().isHorizontal())) {
            return energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> fluidTank);
        energyCapability = LazyOptional.of(() -> energyBuffer);
    }

    // ==================== Tick Logic ====================

    /**
     * Get wait time in ticks for timed modes
     */
    private int getWaitTime() {
        return switch (craneMode) {
            case 5 -> 16;
            case 6 -> 32;
            case 7 -> 48;
            case 8 -> 64;
            case 9 -> 80;
            case 10 -> 100;
            case 11 -> 200;
            case 12 -> 300;
            case 13 -> 400;
            case 14 -> 500;
            case 15 -> 1200;
            case 16 -> 2400;
            case 17 -> 3600;
            case 18 -> 4800;
            case 19 -> 6000;
            case 20 -> 12000;
            case 21 -> 24000;
            case 22 -> 36000;
            case 23 -> 48000;
            case 24 -> 60000;
            default -> 0;
        };
    }

    private void tickServer() {
        tick++;
        if (redSignalTicks > 0) setRedSignalTicks(redSignalTicks - 1);
        if (dockedShip != null) {
            if (!isActive || !isPaired || !dockedShip.isAlive() || !isOwnerShip(dockedShip)
                    || !com.lulan.shincolle.utility.ShipInventoryAccess.isAvailable(dockedShip)
                    || !ShipCraneStateAdapter.guards(dockedShip, this)
                    || ShipCraneStateAdapter.phase(dockedShip) != CraneLifecycle.Phase.WORKING) {
                releaseDockedShip(false);
            } else {
                int elapsed = dockedShip.getStateTimer(ID.T.CraneTime);
                dockedShip.setStateTimer(ID.T.CraneTime, elapsed == Integer.MAX_VALUE ? elapsed : elapsed + 1);
            }
        }

        // Main work cycle: every 16 ticks
        if (isActive && isPaired && tick > 64 && (tick & 15) == 0) {
            // Validate chest
            if (!checkPairedChest()) {
                isPaired = false;
                releaseDockedShip(false);
                setChanged();
                return;
            }

            preFluidTransfer();
            preEnergyTransfer();

            // Find ship
            checkCraningShip();

            // Perform work
            if (dockedShip != null) {
                if (redSignalMode == 1) setRedSignalTicks(18);
                boolean didLoad = applyItemTransfer(true);
                boolean didUnload = applyItemTransfer(false);
                boolean didFluid = transferShipFluid();
                boolean didEnergy = transferShipEnergy();
                CraneLifecycle.Work work = new CraneLifecycle.Work(didLoad, didUnload, didFluid, didEnergy);
                if (dockedShip.getShipType() == ID.ShipType.TRANSPORT) {
                    for (int i = 0; i < work.successes(); i++) dockedShip.addShipExp(ConfigHandler.expGain[6]);
                }

                if (didLoad || didUnload) {
                    com.lulan.shincolle.network.ModNetworking.sendToAllTracking(
                            com.lulan.shincolle.network.S2CSpawnParticlePacket.entityParameters(
                                    com.lulan.shincolle.network.ParticlePacketRoute.CRANE_TRANSFER_TYPE,
                                    dockedShip.getId(), 0D, 0.1D, 0D), dockedShip);
                }
                if (level.getGameTime() >= nextCraneVisualTick) {
                    nextCraneVisualTick = level.getGameTime() + 128L;
                    com.lulan.shincolle.network.ModNetworking.sendToAllTracking(
                            com.lulan.shincolle.network.S2CSpawnParticlePacket.position(40, dockedShip.getId(),
                                    worldPosition.getX() + 0.5D, worldPosition.getY() - 1D,
                                    worldPosition.getZ() + 0.5D,
                                    Math.max(1D, worldPosition.getY() - dockedShip.getY() - 1D), 0D, 0.25D), dockedShip);
                }

                CraneLifecycle.InventoryCompletion completion = craneMode >= 1 && craneMode <= 4 ? itemCompletion()
                        : new CraneLifecycle.InventoryCompletion(false, false, false, false);
                if (CraneLifecycle.complete(waitMode(), work, completion,
                        dockedShip.getStateTimer(ID.T.CraneTime), getWaitTime())) {
                    if (redSignalMode == 2) setRedSignalTicks(2);
                    releaseDockedShip(true);
                }
            }
        }

        // Periodic sync
        if ((tick & 127) == 0) {
            setChanged();
        }
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("CraneMode", craneMode);
        tag.putBoolean("Active", isActive);
        tag.putBoolean("Paired", isPaired);
        tag.putBoolean("EnabLoad", enabLoad);
        tag.putBoolean("EnabUnload", enabUnload);
        tag.putBoolean("CheckMeta", checkMetadata);
        tag.putBoolean("CheckNbt", checkNbt);
        tag.putBoolean("CheckDict", checkDict);
        tag.putInt("ModeItem", modeItem);
        tag.put("FluidTank", fluidTank.writeToNBT(new CompoundTag()));
        tag.put("EnergyBuffer", energyBuffer.serializeNBT());
        tag.putInt("RedSignal", redSignalMode);
        tag.putInt("LiquidMode", liquidMode);
        tag.putInt("EnergyMode", energyMode);
        tag.putInt("PlayerUID", playerUID);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putLong("ChestPos", chestPos.asLong());
        tag.putLong("NextPos", nextPos.asLong());
        tag.putLong("LastPos", lastPos.asLong());
        tag.putBoolean("HasNextWaypoint", nextWaypointSet);
        tag.putBoolean("HasLastWaypoint", lastWaypointSet);
    }

    @Override
    public void load(CompoundTag tag) {
        setRedSignalTicks(0);
        if (dockedShip != null) ShipCraneStateAdapter.reset(dockedShip);
        dockedShip = null;
        super.load(tag);
        craneMode = tag.getInt("CraneMode");
        isActive = tag.getBoolean("Active");
        isPaired = tag.getBoolean("Paired");
        enabLoad = tag.getBoolean("EnabLoad");
        enabUnload = tag.getBoolean("EnabUnload");
        checkMetadata = tag.getBoolean("CheckMeta");
        checkNbt = tag.getBoolean("CheckNbt");
        checkDict = tag.getBoolean("CheckDict");
        fluidTank.readFromNBT(tag.getCompound("FluidTank"));
        energyBuffer.deserializeNBT(tag.contains("EnergyBuffer", Tag.TAG_INT) ? tag.get("EnergyBuffer") : IntTag.valueOf(0));
        modeItem = (tag.contains("ModeItem") ? tag.getInt("ModeItem") : tag.getInt("imode")) & ((1 << SLOT_COUNT) - 1);
        if (tag.contains("RedSignal", Tag.TAG_INT)) {
            redSignalMode = normalizeTriStateMode(tag.getInt("RedSignal"));
        } else {
            redSignalMode = tag.getBoolean("RedSignal") ? 1 : 0;
        }
        if (tag.contains("LiquidMode", Tag.TAG_INT)) {
            liquidMode = normalizeTriStateMode(tag.getInt("LiquidMode"));
        } else {
            liquidMode = tag.getBoolean("LiquidMode") ? 1 : 0;
        }
        if (tag.contains("EnergyMode", Tag.TAG_INT)) {
            energyMode = normalizeTriStateMode(tag.getInt("EnergyMode"));
        } else {
            energyMode = tag.getBoolean("EnergyMode") ? 1 : 0;
        }
        playerUID = tag.getInt("PlayerUID");
        ownerUUID = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        if (tag.contains("ChestPos"))
            chestPos = BlockPos.of(tag.getLong("ChestPos"));
        if (tag.contains("NextPos"))
            nextPos = BlockPos.of(tag.getLong("NextPos"));
        if (tag.contains("LastPos"))
            lastPos = BlockPos.of(tag.getLong("LastPos"));
        nextWaypointSet = tag.contains("HasNextWaypoint")
                ? tag.getBoolean("HasNextWaypoint") : tag.contains("NextPos") && !nextPos.equals(BlockPos.ZERO);
        lastWaypointSet = tag.contains("HasLastWaypoint")
                ? tag.getBoolean("HasLastWaypoint") : tag.contains("LastPos") && !lastPos.equals(BlockPos.ZERO);
        if (!nextWaypointSet) {
            nextPos = BlockPos.ZERO;
        }
        if (!lastWaypointSet) {
            lastPos = BlockPos.ZERO;
        }
    }

    // ==================== Client-Server Sync ====================

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }
}
